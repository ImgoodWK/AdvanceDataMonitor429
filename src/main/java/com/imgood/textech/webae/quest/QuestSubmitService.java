package com.imgood.textech.webae.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.Config;
import com.imgood.textech.compat.bq.BqApiFacade;
import com.imgood.textech.compat.bq.BqQuestingIdentity;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.QuestAnalysisDto;
import com.imgood.textech.webae.dto.QuestAnalysisStepDto;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestSubmitResultDto;
import com.imgood.textech.webae.dto.QuestSubmitStepResultDto;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;

/**
 * Executes quest item/fluid submission from AE network (server main thread).
 * Retrieval (DETECT) uses virtual escrow + offline {@link BqApiFacade#completeRetrievalTask}.
 */
public final class QuestSubmitService {

    private QuestSubmitService() {}

    public static QuestSubmitResultDto submit(String ownerUuid, int networkId, String questId, boolean dryRun,
        List<Integer> stepFilter) {
        return submit(ownerUuid, networkId, questId, dryRun, stepFilter, false);
    }

    public static QuestSubmitResultDto submit(String ownerUuid, int networkId, String questId, boolean dryRun,
        List<Integer> stepFilter, boolean includeAllFluidContainers) {
        return submitInternal(ownerUuid, networkId, questId, dryRun, stepFilter, null, includeAllFluidContainers);
    }

    /**
     * Submit consuming an existing escrow session (scenario B after craft lock).
     * DETECT steps are completed via retrieval API then escrow is released;
     * SUBMIT steps consume escrow stacks then commit.
     */
    public static QuestSubmitResultDto submitFromEscrow(String ownerUuid, int networkId, String questId,
        String escrowId, List<Integer> stepFilter) {
        return submitFromEscrow(ownerUuid, networkId, questId, escrowId, stepFilter, false);
    }

    public static QuestSubmitResultDto submitFromEscrow(String ownerUuid, int networkId, String questId,
        String escrowId, List<Integer> stepFilter, boolean includeAllFluidContainers) {
        return submitInternal(ownerUuid, networkId, questId, false, stepFilter, escrowId, includeAllFluidContainers);
    }

    private static QuestSubmitResultDto submitInternal(String ownerUuid, int networkId, String questId, boolean dryRun,
        List<Integer> stepFilter, String escrowId, boolean includeAllFluidContainers) {
        QuestSubmitResultDto result = new QuestSubmitResultDto();
        result.questId = questId;
        result.dryRun = dryRun;

        if (!Config.webQuestSubmitEnabled) {
            result.message = "Quest submit disabled";
            return result;
        }

        EntityPlayerMP player = BqQuestingIdentity.resolvePlayer(ownerUuid);
        if (player == null) {
            result.message = "Player context unavailable";
            return result;
        }

        UUID qUuid;
        try {
            qUuid = UUID.fromString(questId);
        } catch (IllegalArgumentException e) {
            result.message = "Invalid quest id";
            return result;
        }

        Object quest = BqApiFacade.getQuest(qUuid);
        if (quest == null) {
            result.message = "Quest not found";
            return result;
        }

        if (!BqApiFacade.canSubmitQuest(quest, player)) {
            result.message = "Quest cannot be submitted now";
            return result;
        }

        boolean includeAll = QuestFluidEquivalence.resolveIncludeAll(includeAllFluidContainers);
        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
        QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(ownerUuid, networkId, detail, includeAll);
        UUID questingUuid = BqQuestingIdentity.resolveQuestingUuid(player);
        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        IStorageGrid storageGrid = grid != null ? grid.getCache(IStorageGrid.class) : null;
        PlayerSource source = new PlayerSource(player, null);

        QuestInventoryEscrow.Session escrow = escrowId != null && !escrowId.isEmpty()
            ? QuestInventoryEscrow.get(escrowId)
            : null;
        boolean ownEscrow = false;

        // Scenario C / B: lock DETECT+SUBMIT remaining into escrow when enabled and not dry-run.
        if (!dryRun && escrow == null && Config.webQuestEscrowEnabled && hasLockableSteps(analysis, stepFilter)) {
            List<QuestAnalysisStepDto> lockSteps = filterSteps(analysis.steps, stepFilter);
            QuestInventoryEscrow.LockResult lock = QuestInventoryEscrow
                .lock(ownerUuid, networkId, player, lockSteps, includeAll);
            if (!lock.success) {
                result.success = false;
                result.message = lock.message != null ? lock.message : "Escrow lock failed";
                return result;
            }
            if (lock.escrowId != null && !lock.escrowId.isEmpty()) {
                escrowId = lock.escrowId;
                escrow = lock.session;
                ownEscrow = true;
            }
        }

        int stackKinds = 0;
        boolean allOk = true;
        boolean anyDetect = false;
        boolean anySubmit = false;

        for (QuestAnalysisStepDto step : analysis.steps) {
            if (stepFilter != null && !stepFilter.isEmpty() && !stepFilter.contains(Integer.valueOf(step.index))) {
                continue;
            }
            if (step.complete || !step.webCapable) {
                continue;
            }

            QuestSubmitStepResultDto stepResult = new QuestSubmitStepResultDto();
            stepResult.index = step.index;

            if (QuestTaskDeserializer.WEB_DETECT.equals(step.webAction)) {
                anyDetect = true;
                if (dryRun) {
                    applyDryRunOutcome(stepResult, step, "Would detect retrieval from AE");
                    if (!stepResult.success) {
                        allOk = false;
                    }
                } else {
                    Object task = taskAtIndex(quest, step.index);
                    boolean ok = completeDetectStep(
                        player,
                        questingUuid,
                        qUuid,
                        quest,
                        task,
                        step,
                        escrow,
                        storageGrid);
                    stepResult.success = ok;
                    stepResult.message = ok ? "Retrieval completed (AE hold)" : "Retrieval complete failed";
                    if (!ok) {
                        allOk = false;
                    } else {
                        stackKinds++;
                    }
                }
                result.steps.add(stepResult);
                continue;
            }

            if (QuestTaskDeserializer.WEB_SUBMIT.equals(step.webAction)) {
                anySubmit = true;
                if (step.fluidName != null && !step.fluidName.isEmpty() && step.fluidRemaining > 0) {
                    stepResult.fluidName = step.fluidName;
                    stepResult.fluidAmount = step.fluidRemaining;
                    if (dryRun) {
                        applyDryRunOutcome(stepResult, step, "Would submit fluid");
                        if (!stepResult.success) {
                            allOk = false;
                        }
                    } else if (step.fluidMissing > 0 && escrow == null) {
                        stepResult.success = false;
                        stepResult.message = "Insufficient AE stock";
                        allOk = false;
                    } else if (storageGrid == null && escrow == null) {
                        stepResult.success = false;
                        stepResult.message = "No AE network";
                        allOk = false;
                    } else {
                        FluidStack fluid = QuestTaskDeserializer.parseFluidStack(step.fluidName, step.fluidRemaining);
                        Object task = taskAtIndex(quest, step.index);
                        boolean ok;
                        if (escrow != null) {
                            FluidStack fromEscrow = takeFluidFromEscrow(escrow, step.fluidName, step.fluidRemaining);
                            if (fromEscrow == null || task == null) {
                                ok = false;
                            } else {
                                FluidStack leftover = BqApiFacade
                                    .submitFluidTaskLeftover(task, player, questingUuid, qUuid, quest, fromEscrow);
                                if (leftover != null && leftover.amount > 0) {
                                    returnFluidToEscrow(escrow, leftover);
                                }
                                ok = leftover == null || leftover.amount <= 0;
                            }
                        } else {
                            ok = fluid != null && task != null
                                && submitFluidFromAe(
                                    player,
                                    storageGrid,
                                    source,
                                    questingUuid,
                                    qUuid,
                                    quest,
                                    task,
                                    fluid);
                        }
                        if (ok) {
                            stepResult.success = true;
                            stepResult.message = "Fluid submitted";
                            stackKinds++;
                        } else {
                            stepResult.success = false;
                            stepResult.message = "Fluid submit failed";
                            allOk = false;
                        }
                    }
                    result.steps.add(stepResult);
                    continue;
                }

                if (step.registryName != null && !step.registryName.isEmpty() && step.remaining > 0) {
                    stepResult.itemId = step.registryName;
                    stepResult.amount = step.remaining;
                    if (dryRun) {
                        applyDryRunOutcome(stepResult, step, "Would submit item");
                        if (!stepResult.success) {
                            allOk = false;
                        }
                    } else if (step.missing > 0 && escrow == null) {
                        stepResult.success = false;
                        stepResult.message = "Insufficient AE stock";
                        allOk = false;
                    } else if (storageGrid == null && escrow == null) {
                        stepResult.success = false;
                        stepResult.message = "No AE network";
                        allOk = false;
                    } else {
                        ItemStack proto = stackFromKey(step.registryName, step.meta);
                        Object task = taskAtIndex(quest, step.index);
                        boolean ok;
                        if (escrow != null) {
                            ItemStack fromEscrow = takeItemFromEscrow(
                                escrow,
                                step.registryName,
                                step.meta,
                                step.remaining);
                            if (fromEscrow == null || task == null) {
                                ok = false;
                            } else {
                                ItemStack leftover = BqApiFacade
                                    .submitItemTaskLeftover(task, player, questingUuid, qUuid, quest, fromEscrow);
                                if (leftover != null && leftover.stackSize > 0) {
                                    returnItemToEscrow(escrow, leftover);
                                }
                                ok = leftover == null || leftover.stackSize <= 0;
                            }
                        } else {
                            ok = proto != null && task != null
                                && submitItemFromAe(
                                    player,
                                    storageGrid,
                                    source,
                                    questingUuid,
                                    qUuid,
                                    quest,
                                    task,
                                    proto,
                                    step.remaining);
                        }
                        if (ok) {
                            stepResult.success = true;
                            stepResult.message = "Item submitted";
                            stackKinds++;
                        } else {
                            stepResult.success = false;
                            stepResult.message = "Item submit failed";
                            allOk = false;
                        }
                    }
                    result.steps.add(stepResult);
                }
            }
        }

        if (stackKinds > Config.webQuestSubmitMaxStacks) {
            if (ownEscrow && escrowId != null) {
                QuestInventoryEscrow.release(escrowId);
            }
            result.success = false;
            result.message = "Too many item types in one submit (max " + Config.webQuestSubmitMaxStacks + ")";
            return result;
        }

        if (!dryRun) {
            if (allOk) {
                if (anySubmit || anyDetect) {
                    BqApiFacade.updateQuest(quest, player);
                }
                if (escrowId != null && !escrowId.isEmpty()) {
                    if (anyDetect && !anySubmit) {
                        // Hold-detect: return all locked materials.
                        QuestInventoryEscrow.release(escrowId);
                    } else if (anyDetect && anySubmit) {
                        // Mixed: remaining escrow stacks (detect leftovers) return; submitted already taken.
                        QuestInventoryEscrow.release(escrowId);
                    } else {
                        QuestInventoryEscrow.commit(escrowId);
                    }
                }
                QuestCacheStore.instance()
                    .invalidateProgress(questingUuid != null ? questingUuid.toString() : "");
                result.newState = QuestDataCollector.collectQuestDetail(questId, player).state;
            } else if (ownEscrow && escrowId != null) {
                QuestInventoryEscrow.release(escrowId);
            } else if (escrowId != null && !escrowId.isEmpty() && escrow != null) {
                // Caller-owned escrow (craft): leave release to orchestrator on failure.
            }
        }

        result.success = allOk;
        result.message = dryRun ? "Dry run complete" : (allOk ? "Submit complete" : "Partial failure");
        if (!dryRun) {
            QuestSubmitLog.append(ownerUuid, result);
        }
        return result;
    }

    public static QuestSubmitResultDto detectOnly(String ownerUuid, String questId) {
        return detectOnly(ownerUuid, questId, 0, false);
    }

    public static QuestSubmitResultDto detectOnly(String ownerUuid, String questId, int networkId) {
        return detectOnly(ownerUuid, questId, networkId, false);
    }

    /**
     * DETECT-only path. When {@code networkId} resolves and escrow is enabled, completes Retrieval via AE
     * hold (lock → completeRetrieval → release). Otherwise falls back to legacy {@code detect(player)}.
     */
    public static QuestSubmitResultDto detectOnly(String ownerUuid, String questId, int networkId,
        boolean includeAllFluidContainers) {
        QuestSubmitResultDto result = new QuestSubmitResultDto();
        result.questId = questId;
        result.dryRun = false;
        EntityPlayerMP player = BqQuestingIdentity.resolvePlayer(ownerUuid);
        if (player == null) {
            result.message = "Player unavailable";
            return result;
        }
        UUID qUuid;
        try {
            qUuid = UUID.fromString(questId);
        } catch (IllegalArgumentException e) {
            result.message = "Invalid quest id";
            return result;
        }
        Object quest = BqApiFacade.getQuest(qUuid);
        if (quest == null) {
            result.message = "Quest not found";
            return result;
        }

        if (Config.webQuestEscrowEnabled && WebAeOwnerContext.getGrid(ownerUuid, networkId) != null) {
            List<Integer> detectSteps = detectStepFilter(questId, player);
            if (!detectSteps.isEmpty()) {
                return submitInternal(
                    ownerUuid,
                    networkId,
                    questId,
                    false,
                    detectSteps,
                    null,
                    includeAllFluidContainers);
            }
        }

        BqApiFacade.detectQuest(quest, player);
        BqApiFacade.updateQuest(quest, player);
        UUID questingUuid = BqQuestingIdentity.resolveQuestingUuid(player);
        QuestCacheStore.instance()
            .invalidateProgress(questingUuid != null ? questingUuid.toString() : "");
        result.success = true;
        result.message = "Detect complete";
        result.newState = QuestDataCollector.collectQuestDetail(questId, player).state;
        return result;
    }

    private static List<Integer> detectStepFilter(String questId, EntityPlayerMP player) {
        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
        List<Integer> indices = new ArrayList<Integer>();
        if (detail == null || detail.tasks == null) {
            return indices;
        }
        for (int i = 0; i < detail.tasks.size(); i++) {
            if (detail.tasks.get(i) != null && QuestTaskDeserializer.WEB_DETECT.equals(detail.tasks.get(i).webAction)
                && !detail.tasks.get(i).complete) {
                indices.add(Integer.valueOf(detail.tasks.get(i).index));
            }
        }
        return indices;
    }

    private static boolean completeDetectStep(EntityPlayerMP player, UUID questingUuid, UUID questId, Object quest,
        Object task, QuestAnalysisStepDto step, QuestInventoryEscrow.Session escrow, IStorageGrid storageGrid) {
        if (task == null) {
            return false;
        }
        // Item / fluid-cell DETECT first (cell tasks may also carry fluidName for display — prefer item path).
        if (step.registryName != null && !step.registryName.isEmpty()) {
            if (step.missing > 0 && escrow == null) {
                return false;
            }
            ItemStack proto = stackFromKey(step.registryName, step.meta);
            if (proto == null) {
                return false;
            }
            long amount = step.remaining > 0 ? step.remaining : Math.max(1L, step.required);
            if (step.fluidCellTask && escrow != null) {
                long fromItems = QuestInventoryEscrow.countItemInSession(escrow, step.registryName, step.meta);
                int cap = step.fluidCellCapacityMb > 0 ? step.fluidCellCapacityMb : 1000;
                String fluidName = QuestFluidIconResolver.resolveFluidName(proto);
                long fromFluid = 0L;
                if (fluidName != null) {
                    fromFluid = QuestInventoryEscrow.countFluidInSession(escrow, fluidName) / cap;
                }
                long equiv = fromItems + fromFluid;
                if (equiv < amount) {
                    return false;
                }
                // BQ retrieveItems needs an ItemStack; use real+synthetic filled cells for the full amount.
                proto.stackSize = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, amount));
                return BqApiFacade.completeRetrievalTask(task, player, questingUuid, questId, quest, proto, null);
            }
            proto.stackSize = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, amount));
            return BqApiFacade.completeRetrievalTask(task, player, questingUuid, questId, quest, proto, null);
        }
        if (step.fluidName != null && !step.fluidName.isEmpty()) {
            if (step.fluidMissing > 0 && escrow == null) {
                return false;
            }
            FluidStack fluid = QuestTaskDeserializer
                .parseFluidStack(step.fluidName, step.fluidRemaining > 0 ? step.fluidRemaining : step.fluidRequired);
            if (fluid == null) {
                return false;
            }
            return BqApiFacade.completeRetrievalTask(task, player, questingUuid, questId, quest, null, fluid);
        }
        // Checkbox / empty retrieval — complete by UUID.
        return BqApiFacade.completeRetrievalTask(task, player, questingUuid, questId, quest, null, null);
    }

    private static boolean hasLockableSteps(QuestAnalysisDto analysis, List<Integer> stepFilter) {
        if (analysis == null || analysis.steps == null) {
            return false;
        }
        for (QuestAnalysisStepDto step : analysis.steps) {
            if (stepFilter != null && !stepFilter.isEmpty() && !stepFilter.contains(Integer.valueOf(step.index))) {
                continue;
            }
            if (step == null || step.complete || !step.webCapable) {
                continue;
            }
            if ((step.remaining > 0 && step.missing <= 0) || (step.fluidRemaining > 0 && step.fluidMissing <= 0)) {
                return true;
            }
        }
        return false;
    }

    private static List<QuestAnalysisStepDto> filterSteps(List<QuestAnalysisStepDto> steps, List<Integer> stepFilter) {
        List<QuestAnalysisStepDto> out = new ArrayList<QuestAnalysisStepDto>();
        if (steps == null) {
            return out;
        }
        for (QuestAnalysisStepDto step : steps) {
            if (stepFilter != null && !stepFilter.isEmpty() && !stepFilter.contains(Integer.valueOf(step.index))) {
                continue;
            }
            out.add(step);
        }
        return out;
    }

    private static ItemStack takeItemFromEscrow(QuestInventoryEscrow.Session escrow, String registryName, int meta,
        long amount) {
        if (escrow == null || registryName == null) {
            return null;
        }
        long need = amount;
        ItemStack collected = null;
        for (int i = 0; i < escrow.items.size() && need > 0; i++) {
            ItemStack held = escrow.items.get(i);
            if (held == null || held.getItem() == null) {
                continue;
            }
            Object nameObj = Item.itemRegistry.getNameForObject(held.getItem());
            String name = nameObj != null ? nameObj.toString() : "";
            if (!registryName.equals(name)) {
                continue;
            }
            if (held.getItemDamage() != meta) {
                continue;
            }
            int take = (int) Math.min(need, held.stackSize);
            if (collected == null) {
                collected = held.copy();
                collected.stackSize = take;
            } else {
                collected.stackSize += take;
            }
            held.stackSize -= take;
            need -= take;
            if (held.stackSize <= 0) {
                escrow.items.set(i, null);
            }
        }
        return need <= 0 ? collected : null;
    }

    private static void returnItemToEscrow(QuestInventoryEscrow.Session escrow, ItemStack stack) {
        if (escrow == null || stack == null || stack.stackSize <= 0) {
            return;
        }
        for (ItemStack held : escrow.items) {
            if (held != null && held.getItem() == stack.getItem()
                && held.getItemDamage() == stack.getItemDamage()
                && ItemStack.areItemStackTagsEqual(held, stack)) {
                held.stackSize += stack.stackSize;
                return;
            }
        }
        escrow.items.add(stack.copy());
    }

    private static FluidStack takeFluidFromEscrow(QuestInventoryEscrow.Session escrow, String fluidName, long amount) {
        if (escrow == null || fluidName == null) {
            return null;
        }
        long need = amount;
        FluidStack collected = null;
        for (int i = 0; i < escrow.fluids.size() && need > 0; i++) {
            FluidStack held = escrow.fluids.get(i);
            if (held == null || held.getFluid() == null) {
                continue;
            }
            if (!fluidName.equalsIgnoreCase(
                held.getFluid()
                    .getName())) {
                continue;
            }
            int take = (int) Math.min(need, held.amount);
            if (collected == null) {
                collected = held.copy();
                collected.amount = take;
            } else {
                collected.amount += take;
            }
            held.amount -= take;
            need -= take;
            if (held.amount <= 0) {
                escrow.fluids.set(i, null);
            }
        }
        return need <= 0 ? collected : null;
    }

    private static void returnFluidToEscrow(QuestInventoryEscrow.Session escrow, FluidStack fluid) {
        if (escrow == null || fluid == null || fluid.amount <= 0) {
            return;
        }
        for (FluidStack held : escrow.fluids) {
            if (held != null && held.isFluidEqual(fluid)) {
                held.amount += fluid.amount;
                return;
            }
        }
        escrow.fluids.add(fluid.copy());
    }

    private static void applyDryRunOutcome(QuestSubmitStepResultDto stepResult, QuestAnalysisStepDto step,
        String sufficientMessage) {
        boolean itemShort = step.missing > 0;
        boolean fluidShort = step.fluidMissing > 0;
        if (!itemShort && !fluidShort) {
            stepResult.success = true;
            stepResult.message = sufficientMessage;
            return;
        }
        if (itemShort && step.registryName != null && !step.registryName.isEmpty()) {
            stepResult.itemId = step.registryName;
            stepResult.amount = step.remaining > 0 ? step.remaining : step.missing;
        }
        if (fluidShort && step.fluidName != null && !step.fluidName.isEmpty()) {
            stepResult.fluidName = step.fluidName;
            stepResult.fluidAmount = step.fluidRemaining > 0 ? step.fluidRemaining : step.fluidMissing;
        }
        if (itemShort && step.craftable >= step.missing && step.craftable > 0) {
            stepResult.success = false;
            stepResult.message = "Needs craft first";
            return;
        }
        stepResult.success = false;
        stepResult.message = "Insufficient AE stock";
    }

    private static Object taskAtIndex(Object quest, int index) {
        List<BqApiFacade.IndexedEntry> tasks = BqApiFacade.entriesOfTasks(quest);
        if (index < 0 || index >= tasks.size()) {
            return null;
        }
        return tasks.get(index).value;
    }

    private static boolean submitItemFromAe(EntityPlayerMP player, IStorageGrid storageGrid, PlayerSource source,
        UUID questingUuid, UUID questId, Object quest, Object task, ItemStack prototype, long amount) {
        IAEItemStack request = AEApi.instance()
            .storage()
            .createItemStack(prototype);
        if (request == null) {
            return false;
        }
        request.setStackSize(amount);
        IAEItemStack extracted = storageGrid.getItemInventory()
            .extractItems(request, Actionable.MODULATE, source);
        if (extracted == null || extracted.getStackSize() <= 0) {
            return false;
        }
        ItemStack stack = extracted.getItemStack();
        if (stack == null) {
            return false;
        }
        ItemStack leftover = BqApiFacade.submitItemTaskLeftover(task, player, questingUuid, questId, quest, stack);
        if (leftover != null && leftover.stackSize > 0) {
            IAEItemStack back = AEApi.instance()
                .storage()
                .createItemStack(leftover);
            if (back != null) {
                storageGrid.getItemInventory()
                    .injectItems(back, Actionable.MODULATE, source);
            }
            return false;
        }
        return true;
    }

    private static boolean submitFluidFromAe(EntityPlayerMP player, IStorageGrid storageGrid, PlayerSource source,
        UUID questingUuid, UUID questId, Object quest, Object task, FluidStack fluid) {
        IAEFluidStack request = AEApi.instance()
            .storage()
            .createFluidStack(fluid);
        if (request == null) {
            return false;
        }
        IAEFluidStack extracted = storageGrid.getFluidInventory()
            .extractItems(request, Actionable.MODULATE, source);
        if (extracted == null || extracted.getStackSize() <= 0) {
            return false;
        }
        FluidStack fs = extracted.getFluidStack();
        if (fs == null) {
            return false;
        }
        FluidStack leftover = BqApiFacade.submitFluidTaskLeftover(task, player, questingUuid, questId, quest, fs);
        if (leftover != null && leftover.amount > 0) {
            IAEFluidStack back = AEApi.instance()
                .storage()
                .createFluidStack(leftover);
            if (back != null) {
                storageGrid.getFluidInventory()
                    .injectItems(back, Actionable.MODULATE, source);
            }
            return false;
        }
        return true;
    }

    private static ItemStack stackFromKey(String registryName, int meta) {
        if (registryName == null || registryName.isEmpty()) {
            return null;
        }
        Object itemObj = Item.itemRegistry.getObject(registryName);
        if (!(itemObj instanceof Item)) {
            return null;
        }
        return new ItemStack((Item) itemObj, 1, meta);
    }
}
