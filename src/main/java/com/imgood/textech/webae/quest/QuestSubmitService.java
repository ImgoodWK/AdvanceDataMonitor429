package com.imgood.textech.webae.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.Config;
import com.imgood.textech.assistant.PlayerInventoryUtil;
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
 */
public final class QuestSubmitService {

    private QuestSubmitService() {}

    public static QuestSubmitResultDto submit(String ownerUuid, int networkId, String questId, boolean dryRun,
        List<Integer> stepFilter) {
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

        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
        QuestAnalysisDto analysis = QuestRequirementAnalyzer.analyze(ownerUuid, networkId, detail);
        UUID questingUuid = BqQuestingIdentity.resolveQuestingUuid(player);
        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        IStorageGrid storageGrid = grid != null ? grid.getCache(IStorageGrid.class) : null;
        PlayerSource source = new PlayerSource(player, null);

        int stackKinds = 0;
        boolean allOk = true;

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
                if (!dryRun) {
                    if (step.registryName != null && step.missing > 0 && storageGrid != null) {
                        ItemStack proto = stackFromKey(step.registryName, step.meta);
                        if (proto != null) {
                            long inserted = extractItemToPlayer(player, storageGrid, source, proto, step.missing);
                            if (inserted < step.missing) {
                                stepResult.success = false;
                                stepResult.message = "Insufficient AE stock for retrieval";
                                allOk = false;
                                result.steps.add(stepResult);
                                continue;
                            }
                            stackKinds++;
                        }
                    }
                    BqApiFacade.detectQuest(quest, player);
                    stepResult.success = true;
                    stepResult.message = "Detect triggered";
                } else {
                    stepResult.success = true;
                    stepResult.message = "Would detect retrieval";
                    if (step.registryName != null && step.missing > 0) {
                        stepResult.itemId = step.registryName;
                        stepResult.amount = step.missing;
                    }
                }
                result.steps.add(stepResult);
                continue;
            }

            if (QuestTaskDeserializer.WEB_SUBMIT.equals(step.webAction)) {
                if (step.fluidName != null && !step.fluidName.isEmpty() && step.fluidMissing > 0) {
                    stepResult.fluidName = step.fluidName;
                    stepResult.fluidAmount = step.fluidMissing;
                    if (dryRun) {
                        stepResult.success = true;
                        stepResult.message = "Would submit fluid";
                    } else if (storageGrid == null) {
                        stepResult.success = false;
                        stepResult.message = "No AE network";
                        allOk = false;
                    } else {
                        FluidStack fluid = QuestTaskDeserializer.parseFluidStack(step.fluidName, step.fluidMissing);
                        Object task = taskAtIndex(quest, step.index);
                        if (fluid != null && task != null && submitFluidFromAe(
                            player,
                            storageGrid,
                            source,
                            questingUuid,
                            task,
                            fluid)) {
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

                if (step.registryName != null && step.missing > 0) {
                    stepResult.itemId = step.registryName;
                    stepResult.amount = step.missing;
                    if (dryRun) {
                        stepResult.success = true;
                        stepResult.message = "Would submit item";
                    } else if (storageGrid == null) {
                        stepResult.success = false;
                        stepResult.message = "No AE network";
                        allOk = false;
                    } else {
                        ItemStack proto = stackFromKey(step.registryName, step.meta);
                        Object task = taskAtIndex(quest, step.index);
                        if (proto != null && task != null && submitItemFromAe(
                            player,
                            storageGrid,
                            source,
                            questingUuid,
                            task,
                            proto,
                            step.missing)) {
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
            result.success = false;
            result.message = "Too many item types in one submit (max " + Config.webQuestSubmitMaxStacks + ")";
            return result;
        }

        if (!dryRun && allOk) {
            BqApiFacade.detectQuest(quest, player);
            BqApiFacade.updateQuest(quest, player);
            QuestCacheStore.instance()
                .invalidateProgress(questingUuid != null ? questingUuid.toString() : "");
            result.newState = QuestDataCollector.collectQuestDetail(questId, player).state;
        }

        result.success = dryRun ? allOk : allOk;
        result.message = dryRun ? "Dry run complete" : (allOk ? "Submit complete" : "Partial failure");
        if (!dryRun) {
            QuestSubmitLog.append(ownerUuid, result);
        }
        return result;
    }

    public static QuestSubmitResultDto detectOnly(String ownerUuid, String questId) {
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

    private static Object taskAtIndex(Object quest, int index) {
        List<BqApiFacade.IndexedEntry> tasks = BqApiFacade.entriesOfTasks(quest);
        if (index < 0 || index >= tasks.size()) {
            return null;
        }
        return tasks.get(index).value;
    }

    private static boolean submitItemFromAe(EntityPlayerMP player, IStorageGrid storageGrid, PlayerSource source,
        UUID questingUuid, Object task, ItemStack prototype, long amount) {
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
        return BqApiFacade.submitItemTask(task, player, questingUuid, stack);
    }

    private static boolean submitFluidFromAe(EntityPlayerMP player, IStorageGrid storageGrid, PlayerSource source,
        UUID questingUuid, Object task, FluidStack fluid) {
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
        return BqApiFacade.submitFluidTask(task, player, questingUuid, fs);
    }

    private static long extractItemToPlayer(EntityPlayerMP player, IStorageGrid storageGrid, PlayerSource source,
        ItemStack prototype, long amount) {
        IAEItemStack request = AEApi.instance()
            .storage()
            .createItemStack(prototype);
        if (request == null) {
            return 0L;
        }
        request.setStackSize(amount);
        IAEItemStack extracted = storageGrid.getItemInventory()
            .extractItems(request, Actionable.MODULATE, source);
        if (extracted == null || extracted.getStackSize() <= 0) {
            return 0L;
        }
        ItemStack stack = extracted.getItemStack();
        if (stack == null) {
            return 0L;
        }
        return PlayerInventoryUtil.insertIntoPlayerInventory(player, stack);
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
