package com.imgood.textech.webae.quest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.QuestAnalysisStepDto;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

/**
 * In-memory AE virtual escrow for WebAE quest submit/detect.
 * Extracts stacks with {@link Actionable#MODULATE}, holds them until commit (consume) or release (return).
 * Supports fluid ↔ cell equivalence and partial lock for craft-then-submit.
 */
public final class QuestInventoryEscrow {

    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<String, Session>();

    private QuestInventoryEscrow() {}

    public static final class Session {

        public final String escrowId;
        public final String ownerUuid;
        public final int networkId;
        public final long createdAtMs;
        public long deadlineMs;
        public boolean includeAllFluidContainers;
        public final List<ItemStack> items = new ArrayList<ItemStack>();
        public final List<FluidStack> fluids = new ArrayList<FluidStack>();

        Session(String escrowId, String ownerUuid, int networkId, long deadlineMs) {
            this.escrowId = escrowId;
            this.ownerUuid = ownerUuid;
            this.networkId = networkId;
            this.createdAtMs = System.currentTimeMillis();
            this.deadlineMs = deadlineMs;
        }
    }

    public static final class LockResult {

        public boolean success;
        public String escrowId = "";
        public String message = "";
        public Session session;
    }

    /** Full lock: fails unless every lockable step can be fully covered. */
    public static LockResult lock(String ownerUuid, int networkId, EntityPlayerMP player,
        List<QuestAnalysisStepDto> steps) {
        return lock(ownerUuid, networkId, player, steps, false);
    }

    public static LockResult lock(String ownerUuid, int networkId, EntityPlayerMP player,
        List<QuestAnalysisStepDto> steps, boolean includeAllFluidContainers) {
        LockResult partial = lockPartial(ownerUuid, networkId, player, steps, includeAllFluidContainers, null);
        if (!partial.success) {
            return partial;
        }
        if (partial.session == null || (partial.escrowId == null || partial.escrowId.isEmpty())) {
            // Nothing to lock — treat as success when no remaining requirements.
            if (allStepsCovered(steps, null)) {
                return partial;
            }
            partial.success = false;
            partial.message = "Nothing locked but requirements remain";
            return partial;
        }
        if (!allStepsCovered(steps, partial.session)) {
            release(partial.escrowId);
            LockResult fail = new LockResult();
            fail.message = "Insufficient AE stock for full escrow lock";
            return fail;
        }
        return partial;
    }

    /**
     * Lock currently available materials for each step (allows missing &gt; 0).
     * When {@code existingEscrowId} is set, appends into that session.
     */
    public static LockResult lockPartial(String ownerUuid, int networkId, EntityPlayerMP player,
        List<QuestAnalysisStepDto> steps, boolean includeAllFluidContainers, String existingEscrowId) {
        LockResult result = new LockResult();
        if (!Config.webQuestEscrowEnabled) {
            result.message = "Quest escrow disabled";
            return result;
        }
        if (ownerUuid == null || player == null || steps == null) {
            result.message = "Invalid escrow lock request";
            return result;
        }
        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        if (grid == null) {
            result.message = "No AE network";
            return result;
        }
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) {
            result.message = "No AE storage grid";
            return result;
        }
        PlayerSource source = new PlayerSource(player, null);
        boolean includeAll = QuestFluidEquivalence.resolveIncludeAll(includeAllFluidContainers);
        long timeout = Config.webQuestEscrowTimeoutMs > 0 ? Config.webQuestEscrowTimeoutMs
            : Config.webQuestCraftWaitTimeoutMs;

        Session session;
        String escrowId;
        if (existingEscrowId != null && !existingEscrowId.isEmpty()) {
            session = SESSIONS.get(existingEscrowId);
            if (session == null) {
                result.message = "Escrow session not found";
                return result;
            }
            escrowId = existingEscrowId;
            session.deadlineMs = System.currentTimeMillis() + timeout;
            session.includeAllFluidContainers = includeAll;
        } else {
            escrowId = UUID.randomUUID()
                .toString()
                .substring(0, 12);
            session = new Session(escrowId, ownerUuid, networkId, System.currentTimeMillis() + timeout);
            session.includeAllFluidContainers = includeAll;
        }

        List<IAEItemStack> extractedItems = new ArrayList<IAEItemStack>();
        List<IAEFluidStack> extractedFluids = new ArrayList<IAEFluidStack>();
        // Track inject-backs that are not part of escrow (empty cells / remainder fluid after drain).
        List<IAEItemStack> returnedItems = new ArrayList<IAEItemStack>();
        List<IAEFluidStack> returnedFluids = new ArrayList<IAEFluidStack>();
        int itemsBefore = session.items.size();
        int fluidsBefore = session.fluids.size();

        try {
            for (QuestAnalysisStepDto step : steps) {
                if (step == null || step.complete || !step.webCapable) {
                    continue;
                }
                boolean isSubmit = QuestTaskDeserializer.WEB_SUBMIT.equals(step.webAction);
                boolean isDetect = QuestTaskDeserializer.WEB_DETECT.equals(step.webAction);
                if (!isSubmit && !isDetect) {
                    continue;
                }

                if (step.fluidName != null && !step.fluidName.isEmpty()
                    && step.fluidRemaining > 0
                    && (step.registryName == null || step.registryName.isEmpty() || !step.fluidCellTask)) {
                    long already = countFluidInSession(session, step.fluidName);
                    long need = step.fluidRemaining - already;
                    if (need <= 0) {
                        continue;
                    }
                    long got = extractFluidPreferCells(
                        storageGrid,
                        source,
                        step.fluidName,
                        need,
                        includeAll,
                        session,
                        extractedItems,
                        extractedFluids,
                        returnedItems,
                        returnedFluids);
                    if (got < 0) {
                        failLock(
                            storageGrid,
                            source,
                            extractedItems,
                            extractedFluids,
                            returnedItems,
                            returnedFluids,
                            session,
                            itemsBefore,
                            fluidsBefore);
                        result.message = "Failed to escrow fluid: " + step.fluidName;
                        return result;
                    }
                    continue;
                }

                if (step.registryName != null && !step.registryName.isEmpty() && step.remaining > 0) {
                    long already = countItemInSession(session, step.registryName, step.meta);
                    // DETECT cell tasks may also hold equivalent fluid in session.
                    if (step.fluidCellTask && isDetect) {
                        int cap = step.fluidCellCapacityMb > 0 ? step.fluidCellCapacityMb : 1000;
                        ItemStack proto = QuestFluidEquivalence.stackFromKey(step.registryName, step.meta);
                        String fluidName = QuestFluidIconResolver.resolveFluidName(proto);
                        if (fluidName != null && !fluidName.isEmpty()) {
                            already += countFluidInSession(session, fluidName) / cap;
                        }
                    }
                    long need = step.remaining - already;
                    if (need <= 0) {
                        continue;
                    }

                    if (step.fluidCellTask && isDetect) {
                        if (!lockCellDetectPartial(
                            storageGrid,
                            source,
                            step,
                            need,
                            includeAll,
                            session,
                            extractedItems,
                            extractedFluids,
                            returnedItems,
                            returnedFluids)) {
                            failLock(
                                storageGrid,
                                source,
                                extractedItems,
                                extractedFluids,
                                returnedItems,
                                returnedFluids,
                                session,
                                itemsBefore,
                                fluidsBefore);
                            result.message = "Failed to escrow fluid-cell detect: " + step.registryName;
                            return result;
                        }
                    } else if (step.fluidCellTask && isSubmit) {
                        if (!lockCellSubmitPartial(
                            storageGrid,
                            source,
                            step,
                            need,
                            includeAll,
                            session,
                            extractedItems,
                            extractedFluids,
                            returnedItems,
                            returnedFluids)) {
                            failLock(
                                storageGrid,
                                source,
                                extractedItems,
                                extractedFluids,
                                returnedItems,
                                returnedFluids,
                                session,
                                itemsBefore,
                                fluidsBefore);
                            result.message = "Failed to escrow fluid-cell submit: " + step.registryName;
                            return result;
                        }
                    } else {
                        long got = extractItems(
                            storageGrid,
                            source,
                            step.registryName,
                            step.meta,
                            need,
                            session,
                            extractedItems);
                        if (got < 0) {
                            failLock(
                                storageGrid,
                                source,
                                extractedItems,
                                extractedFluids,
                                returnedItems,
                                returnedFluids,
                                session,
                                itemsBefore,
                                fluidsBefore);
                            result.message = "Failed to escrow item: " + step.registryName;
                            return result;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            failLock(
                storageGrid,
                source,
                extractedItems,
                extractedFluids,
                returnedItems,
                returnedFluids,
                session,
                itemsBefore,
                fluidsBefore);
            result.message = "Escrow lock error: " + t.toString();
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] Escrow lock error", t);
            return result;
        }

        if (session.items.isEmpty() && session.fluids.isEmpty()) {
            // Return any stray extracts that never entered the session.
            rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
            reInject(storageGrid, source, returnedItems, returnedFluids);
            result.success = true;
            result.escrowId = existingEscrowId != null ? existingEscrowId : "";
            result.message = "Nothing to lock";
            result.session = session;
            if (existingEscrowId == null || existingEscrowId.isEmpty()) {
                // ephemeral empty session not registered
            }
            return result;
        }

        // Leftover tracking lists must not retain AE stacks after a successful lock.
        rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
        reInject(storageGrid, source, returnedItems, returnedFluids);

        SESSIONS.put(escrowId, session);
        result.success = true;
        result.escrowId = escrowId;
        result.session = session;
        result.message = "Escrow locked";
        return result;
    }

    public static LockResult appendLock(String escrowId, String ownerUuid, int networkId, EntityPlayerMP player,
        List<QuestAnalysisStepDto> steps, boolean includeAllFluidContainers) {
        return lockPartial(ownerUuid, networkId, player, steps, includeAllFluidContainers, escrowId);
    }

    public static Session get(String escrowId) {
        if (escrowId == null || escrowId.isEmpty()) {
            return null;
        }
        return SESSIONS.get(escrowId);
    }

    /** Drop held stacks without returning to AE (SUBMIT consumed them). */
    public static boolean commit(String escrowId) {
        if (escrowId == null || escrowId.isEmpty()) {
            return true;
        }
        Session removed = SESSIONS.remove(escrowId);
        return removed != null;
    }

    /** Inject held stacks back into AE and remove the session. */
    public static boolean release(String escrowId) {
        if (escrowId == null || escrowId.isEmpty()) {
            return true;
        }
        Session session = SESSIONS.remove(escrowId);
        if (session == null) {
            return false;
        }
        return injectSession(session);
    }

    public static void releaseAllForOwner(String ownerUuid) {
        if (ownerUuid == null) {
            return;
        }
        Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, Session> entry = it.next();
            Session session = entry.getValue();
            if (session != null && ownerUuid.equals(session.ownerUuid)) {
                it.remove();
                injectSession(session);
            }
        }
    }

    /** Timed cleanup; call from server tick. */
    public static void tickCleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, Session> entry = it.next();
            Session session = entry.getValue();
            if (session != null && now > session.deadlineMs) {
                it.remove();
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE Quest] Escrow {} timed out; returning items to AE (owner={})",
                    session.escrowId,
                    session.ownerUuid);
                injectSession(session);
            }
        }
    }

    public static long countItemInSession(Session session, String registryName, int meta) {
        if (session == null || registryName == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack held : session.items) {
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
            total += held.stackSize;
        }
        return total;
    }

    public static long countFluidInSession(Session session, String fluidName) {
        if (session == null || fluidName == null) {
            return 0L;
        }
        long total = 0L;
        for (FluidStack held : session.fluids) {
            if (held == null || held.getFluid() == null) {
                continue;
            }
            if (fluidName.equalsIgnoreCase(
                held.getFluid()
                    .getName())) {
                total += held.amount;
            }
        }
        return total;
    }

    public static boolean allStepsCovered(List<QuestAnalysisStepDto> steps, Session session) {
        if (steps == null) {
            return true;
        }
        for (QuestAnalysisStepDto step : steps) {
            if (step == null || step.complete || !step.webCapable) {
                continue;
            }
            boolean isSubmit = QuestTaskDeserializer.WEB_SUBMIT.equals(step.webAction);
            boolean isDetect = QuestTaskDeserializer.WEB_DETECT.equals(step.webAction);
            if (!isSubmit && !isDetect) {
                continue;
            }
            if (step.fluidName != null && !step.fluidName.isEmpty()
                && step.fluidRemaining > 0
                && (step.registryName == null || step.registryName.isEmpty() || !step.fluidCellTask)) {
                long have = countFluidInSession(session, step.fluidName);
                if (have < step.fluidRemaining) {
                    return false;
                }
                continue;
            }
            if (step.registryName != null && !step.registryName.isEmpty() && step.remaining > 0) {
                long have = countItemInSession(session, step.registryName, step.meta);
                if (step.fluidCellTask && isDetect) {
                    int cap = step.fluidCellCapacityMb > 0 ? step.fluidCellCapacityMb : 1000;
                    ItemStack proto = QuestFluidEquivalence.stackFromKey(step.registryName, step.meta);
                    String fluidName = QuestFluidIconResolver.resolveFluidName(proto);
                    if (fluidName != null) {
                        have += countFluidInSession(session, fluidName) / cap;
                    }
                }
                if (have < step.remaining) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean lockCellDetectPartial(IStorageGrid storageGrid, PlayerSource source,
        QuestAnalysisStepDto step, long need, boolean includeAll, Session session, List<IAEItemStack> extractedItems,
        List<IAEFluidStack> extractedFluids, List<IAEItemStack> returnedItems, List<IAEFluidStack> returnedFluids) {
        long gotItems = extractItems(storageGrid, source, step.registryName, step.meta, need, session, extractedItems);
        if (gotItems < 0) {
            return false;
        }
        long still = need - gotItems;
        if (still <= 0) {
            return true;
        }
        int cap = step.fluidCellCapacityMb > 0 ? step.fluidCellCapacityMb : 1000;
        ItemStack proto = QuestFluidEquivalence.stackFromKey(step.registryName, step.meta);
        String fluidName = QuestFluidIconResolver.resolveFluidName(proto);
        if (fluidName == null) {
            return gotItems > 0; // partial ok for lockPartial
        }
        long fluidNeed = still * (long) cap;
        long gotFluid = extractFluidPreferCells(
            storageGrid,
            source,
            fluidName,
            fluidNeed,
            includeAll,
            session,
            extractedItems,
            extractedFluids,
            returnedItems,
            returnedFluids);
        return gotFluid >= 0;
    }

    private static boolean lockCellSubmitPartial(IStorageGrid storageGrid, PlayerSource source,
        QuestAnalysisStepDto step, long need, boolean includeAll, Session session, List<IAEItemStack> extractedItems,
        List<IAEFluidStack> extractedFluids, List<IAEItemStack> returnedItems, List<IAEFluidStack> returnedFluids) {
        long before = countItemInSession(session, step.registryName, step.meta);
        long gotItems = extractItems(storageGrid, source, step.registryName, step.meta, need, session, extractedItems);
        if (gotItems < 0) {
            return false;
        }
        long still = need - gotItems;
        if (still <= 0) {
            return true;
        }
        ItemStack filledProto = QuestFluidEquivalence.stackFromKey(step.registryName, step.meta);
        if (filledProto == null) {
            return gotItems > 0;
        }
        String fluidName = QuestFluidIconResolver.resolveFluidName(filledProto);
        ItemStack emptyProto = QuestFluidEquivalence.emptyForFilled(filledProto);
        if (fluidName == null || emptyProto == null) {
            return gotItems > 0;
        }
        int cap = step.fluidCellCapacityMb > 0 ? step.fluidCellCapacityMb
            : QuestFluidEquivalence.capacityMb(filledProto);
        if (cap <= 0) {
            cap = 1000;
        }
        Object emptyNameObj = Item.itemRegistry.getNameForObject(emptyProto.getItem());
        String emptyName = emptyNameObj != null ? emptyNameObj.toString() : "";
        int emptyMeta = emptyProto.getItemDamage();

        for (long i = 0; i < still; i++) {
            long emptyGot = extractItems(storageGrid, source, emptyName, emptyMeta, 1L, null, extractedItems);
            if (emptyGot < 1) {
                break;
            }
            ItemStack empty = takeLastExtractedItem(extractedItems, emptyName, emptyMeta);
            if (empty == null) {
                // Extract tracked but unmatched — leave in extractedItems for end-of-lock rollback.
                break;
            }
            List<IAEFluidStack> attemptFluids = new ArrayList<IAEFluidStack>();
            FluidStack needFs = QuestFluidEquivalence.parseFluid(fluidName, cap);
            if (needFs == null) {
                if (!injectItemOk(storageGrid, source, empty, returnedItems)) {
                    returnedItems.add(
                        AEApi.instance()
                            .storage()
                            .createItemStack(empty));
                }
                break;
            }
            IAEFluidStack freq = AEApi.instance()
                .storage()
                .createFluidStack(needFs);
            IAEFluidStack extracted = storageGrid.getFluidInventory()
                .extractItems(freq, Actionable.MODULATE, source);
            long haveFluid = extracted != null ? extracted.getStackSize() : 0L;
            if (extracted != null && haveFluid > 0) {
                attemptFluids.add(extracted);
            }
            if (haveFluid < cap) {
                long rem = cap - haveFluid;
                long fromCells = extractFluidPreferCells(
                    storageGrid,
                    source,
                    fluidName,
                    rem,
                    includeAll,
                    null,
                    extractedItems,
                    attemptFluids,
                    returnedItems,
                    returnedFluids);
                haveFluid += fromCells;
            }
            if (haveFluid < cap) {
                if (!injectItemOk(storageGrid, source, empty, returnedItems)) {
                    IAEItemStack aeEmpty = AEApi.instance()
                        .storage()
                        .createItemStack(empty);
                    if (aeEmpty != null) {
                        returnedItems.add(aeEmpty);
                    }
                }
                rollbackExtracts(storageGrid, source, new ArrayList<IAEItemStack>(), attemptFluids);
                break;
            }
            FluidStack fillFs = QuestFluidEquivalence.parseFluid(fluidName, (int) Math.min(haveFluid, cap));
            ItemStack filled = QuestFluidEquivalence.fillEmpty(empty, fillFs, filledProto);
            if (filled == null || filled.getItem() != filledProto.getItem()
                || filled.getItemDamage() != filledProto.getItemDamage()) {
                if (!injectItemOk(storageGrid, source, empty, returnedItems)) {
                    IAEItemStack aeEmpty = AEApi.instance()
                        .storage()
                        .createItemStack(empty);
                    if (aeEmpty != null) {
                        returnedItems.add(aeEmpty);
                    }
                }
                rollbackExtracts(storageGrid, source, new ArrayList<IAEItemStack>(), attemptFluids);
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE Quest] Failed to fill empty cell for quest item {} meta {} — empty/fluid returned",
                    step.registryName,
                    Integer.valueOf(step.meta));
                break;
            }
            // Fluid consumed into the filled cell — drop from rollback tracking.
            consumeExtractedFluids(attemptFluids, fluidName, cap);
            // Any surplus from over-extract goes back with the shared lists.
            if (!attemptFluids.isEmpty()) {
                extractedFluids.addAll(attemptFluids);
            }
            addItemToSession(session, filled);
        }
        long gained = countItemInSession(session, step.registryName, step.meta) - before;
        return gained >= 0;
    }

    private static boolean injectItemOk(IStorageGrid storageGrid, PlayerSource source, ItemStack stack,
        List<IAEItemStack> returnedItems) {
        if (stack == null) {
            return true;
        }
        IAEItemStack ae = AEApi.instance()
            .storage()
            .createItemStack(stack);
        if (ae == null) {
            return false;
        }
        IAEItemStack leftover = storageGrid.getItemInventory()
            .injectItems(ae, Actionable.MODULATE, source);
        if (leftover != null && leftover.getStackSize() > 0) {
            if (returnedItems != null) {
                returnedItems.add(leftover);
            }
            return false;
        }
        return true;
    }

    private static ItemStack takeLastExtractedItem(List<IAEItemStack> extractedItems, String registryName, int meta) {
        for (int i = extractedItems.size() - 1; i >= 0; i--) {
            IAEItemStack ae = extractedItems.get(i);
            if (ae == null) {
                continue;
            }
            ItemStack st = ae.getItemStack();
            if (st == null || st.getItem() == null) {
                continue;
            }
            Object nameObj = Item.itemRegistry.getNameForObject(st.getItem());
            String name = nameObj != null ? nameObj.toString() : "";
            if (!registryName.equals(name)) {
                continue;
            }
            if (st.getItemDamage() != meta) {
                continue;
            }
            extractedItems.remove(i);
            ItemStack one = st.copy();
            one.stackSize = 1;
            if (ae.getStackSize() > 1) {
                IAEItemStack rest = ae.copy();
                rest.setStackSize(ae.getStackSize() - 1);
                extractedItems.add(rest);
            }
            return one;
        }
        return null;
    }

    private static void consumeExtractedFluids(List<IAEFluidStack> extractedFluids, String fluidName, long amount) {
        long need = amount;
        for (int i = extractedFluids.size() - 1; i >= 0 && need > 0; i--) {
            IAEFluidStack ae = extractedFluids.get(i);
            if (ae == null || ae.getFluid() == null) {
                continue;
            }
            if (!fluidName.equalsIgnoreCase(
                ae.getFluid()
                    .getName())) {
                continue;
            }
            long take = Math.min(need, ae.getStackSize());
            ae.setStackSize(ae.getStackSize() - take);
            need -= take;
            if (ae.getStackSize() <= 0) {
                extractedFluids.remove(i);
            }
        }
    }

    /**
     * Extract {@code need} mB of fluid: drain from cells first (partial), then free fluid.
     * Returns mB obtained (&gt;=0), or -1 on hard failure.
     * When {@code session} is non-null, obtained fluid is added to session.
     */
    private static long extractFluidPreferCells(IStorageGrid storageGrid, PlayerSource source, String fluidName,
        long need, boolean includeAll, Session session, List<IAEItemStack> extractedItems,
        List<IAEFluidStack> extractedFluids, List<IAEItemStack> returnedItems, List<IAEFluidStack> returnedFluids) {
        if (need <= 0) {
            return 0L;
        }
        long remaining = need;
        long obtained = 0L;

        // Drain cells first.
        IItemList<IAEItemStack> itemList = storageGrid.getItemInventory()
            .getStorageList();
        if (itemList != null) {
            for (IAEItemStack aeItem : itemList) {
                if (remaining <= 0) {
                    break;
                }
                if (aeItem == null || aeItem.getItemStack() == null) {
                    continue;
                }
                ItemStack proto = aeItem.getItemStack()
                    .copy();
                proto.stackSize = 1;
                if (!QuestFluidEquivalence.isInScope(proto, includeAll)) {
                    continue;
                }
                String name = QuestFluidIconResolver.resolveFluidName(proto);
                if (name == null || !fluidName.equalsIgnoreCase(name)) {
                    continue;
                }
                int cap = QuestFluidEquivalence.capacityMb(proto);
                if (cap <= 0) {
                    continue;
                }
                long availableCells = aeItem.getStackSize();
                while (remaining > 0 && availableCells > 0) {
                    IAEItemStack req = aeItem.copy();
                    req.setStackSize(1);
                    IAEItemStack extracted = storageGrid.getItemInventory()
                        .extractItems(req, Actionable.MODULATE, source);
                    if (extracted == null || extracted.getStackSize() < 1) {
                        break;
                    }
                    extractedItems.add(extracted);
                    availableCells--;
                    int take = (int) Math.min(remaining, cap);
                    FluidStack intoEscrow = QuestFluidEquivalence.parseFluid(fluidName, take);
                    if (intoEscrow != null) {
                        if (session != null) {
                            addFluidToSession(session, intoEscrow);
                        } else if (extractedFluids != null) {
                            // Keep drained mB trackable for rollback when not going into a session.
                            IAEFluidStack drained = AEApi.instance()
                                .storage()
                                .createFluidStack(intoEscrow);
                            if (drained != null) {
                                extractedFluids.add(drained);
                            }
                        }
                    }
                    obtained += take;
                    remaining -= take;
                    // Return empty + remainder fluid (not kept in escrow).
                    ItemStack empty = QuestFluidEquivalence.emptyForFilled(proto);
                    if (empty != null) {
                        IAEItemStack emptyAe = AEApi.instance()
                            .storage()
                            .createItemStack(empty);
                        if (emptyAe != null) {
                            IAEItemStack leftover = storageGrid.getItemInventory()
                                .injectItems(emptyAe, Actionable.MODULATE, source);
                            if (leftover != null && leftover.getStackSize() > 0) {
                                returnedItems.add(leftover);
                            }
                        }
                    }
                    int remMb = cap - take;
                    if (remMb > 0) {
                        FluidStack remFs = QuestFluidEquivalence.parseFluid(fluidName, remMb);
                        if (remFs != null) {
                            IAEFluidStack remAe = AEApi.instance()
                                .storage()
                                .createFluidStack(remFs);
                            if (remAe != null) {
                                IAEFluidStack leftover = storageGrid.getFluidInventory()
                                    .injectItems(remAe, Actionable.MODULATE, source);
                                if (leftover != null && leftover.getStackSize() > 0) {
                                    returnedFluids.add(leftover);
                                }
                            }
                        }
                    }
                    // Cell was converted — remove from extractedItems tracking (empty returned separately).
                    extractedItems.remove(extractedItems.size() - 1);
                }
            }
        }

        if (remaining > 0) {
            FluidStack needFs = QuestFluidEquivalence.parseFluid(fluidName, remaining);
            if (needFs != null) {
                IAEFluidStack request = AEApi.instance()
                    .storage()
                    .createFluidStack(needFs);
                if (request != null) {
                    IAEFluidStack extracted = storageGrid.getFluidInventory()
                        .extractItems(request, Actionable.MODULATE, source);
                    if (extracted != null && extracted.getStackSize() > 0) {
                        long got = extracted.getStackSize();
                        obtained += got;
                        remaining -= got;
                        if (session != null) {
                            FluidStack held = extracted.getFluidStack();
                            if (held != null) {
                                addFluidToSession(session, held.copy());
                            }
                            // Session owns this extract — do not also track in extractedFluids.
                        } else {
                            extractedFluids.add(extracted);
                        }
                    }
                }
            }
        }
        return obtained;
    }

    /** @return amount extracted, or -1 on hard error */
    private static long extractItems(IStorageGrid storageGrid, PlayerSource source, String registryName, int meta,
        long amount, Session session, List<IAEItemStack> extractedItems) {
        if (amount <= 0) {
            return 0L;
        }
        ItemStack proto = QuestFluidEquivalence.stackFromKey(registryName, meta);
        if (proto == null) {
            return -1L;
        }
        IAEItemStack request = AEApi.instance()
            .storage()
            .createItemStack(proto);
        if (request == null) {
            return -1L;
        }
        request.setStackSize(amount);
        IAEItemStack extracted = storageGrid.getItemInventory()
            .extractItems(request, Actionable.MODULATE, source);
        if (extracted == null || extracted.getStackSize() <= 0) {
            return 0L;
        }
        long got = extracted.getStackSize();
        if (session != null) {
            ItemStack held = extracted.getItemStack();
            if (held != null) {
                addItemToSession(session, held.copy());
            }
            // Session owns this extract — skip extractedItems tracking.
        } else {
            extractedItems.add(extracted);
        }
        return got;
    }

    private static void addItemToSession(Session session, ItemStack stack) {
        if (session == null || stack == null) {
            return;
        }
        for (ItemStack held : session.items) {
            if (held != null && held.getItem() == stack.getItem()
                && held.getItemDamage() == stack.getItemDamage()
                && ItemStack.areItemStackTagsEqual(held, stack)) {
                held.stackSize += stack.stackSize;
                return;
            }
        }
        session.items.add(stack);
    }

    private static void addFluidToSession(Session session, FluidStack fluid) {
        if (session == null || fluid == null) {
            return;
        }
        for (FluidStack held : session.fluids) {
            if (held != null && held.isFluidEqual(fluid)) {
                held.amount += fluid.amount;
                return;
            }
        }
        session.fluids.add(fluid);
    }

    private static void injectItem(IStorageGrid storageGrid, PlayerSource source, ItemStack stack) {
        if (stack == null) {
            return;
        }
        IAEItemStack ae = AEApi.instance()
            .storage()
            .createItemStack(stack);
        if (ae == null) {
            return;
        }
        storageGrid.getItemInventory()
            .injectItems(ae, Actionable.MODULATE, source);
    }

    private static void failLock(IStorageGrid storageGrid, PlayerSource source, List<IAEItemStack> extractedItems,
        List<IAEFluidStack> extractedFluids, List<IAEItemStack> returnedItems, List<IAEFluidStack> returnedFluids,
        Session session, int itemsBefore, int fluidsBefore) {
        rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
        reInject(storageGrid, source, returnedItems, returnedFluids);
        // Return only stacks added during this lock call (preserve prior escrow on appendLock).
        if (session != null) {
            while (session.items.size() > itemsBefore) {
                ItemStack stack = session.items.remove(session.items.size() - 1);
                injectItem(storageGrid, source, stack);
            }
            while (session.fluids.size() > fluidsBefore) {
                FluidStack fluid = session.fluids.remove(session.fluids.size() - 1);
                if (fluid == null) {
                    continue;
                }
                IAEFluidStack ae = AEApi.instance()
                    .storage()
                    .createFluidStack(fluid);
                if (ae != null) {
                    storageGrid.getFluidInventory()
                        .injectItems(ae, Actionable.MODULATE, source);
                }
            }
        }
    }

    private static void reInject(IStorageGrid storageGrid, PlayerSource source, List<IAEItemStack> items,
        List<IAEFluidStack> fluids) {
        // Already injected during drain; list tracks leftovers that failed inject — try again.
        for (IAEItemStack stack : items) {
            if (stack == null) {
                continue;
            }
            storageGrid.getItemInventory()
                .injectItems(stack.copy(), Actionable.MODULATE, source);
        }
        for (IAEFluidStack stack : fluids) {
            if (stack == null) {
                continue;
            }
            storageGrid.getFluidInventory()
                .injectItems(stack.copy(), Actionable.MODULATE, source);
        }
        items.clear();
        fluids.clear();
    }

    private static boolean injectSession(Session session) {
        if (session == null) {
            return false;
        }
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(session.ownerUuid);
        IGrid grid = WebAeOwnerContext.getGrid(session.ownerUuid, session.networkId);
        if (player == null || grid == null) {
            AdvanceDataMonitor.LOG.error(
                "[WebAE Quest] Escrow {} release failed: player/grid unavailable; stacks may be lost",
                session.escrowId);
            return false;
        }
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) {
            AdvanceDataMonitor.LOG.error("[WebAE Quest] Escrow {} release failed: no storage grid", session.escrowId);
            return false;
        }
        PlayerSource source = new PlayerSource(player, null);
        boolean ok = true;
        for (ItemStack stack : session.items) {
            if (stack == null) {
                continue;
            }
            IAEItemStack request = AEApi.instance()
                .storage()
                .createItemStack(stack);
            if (request == null) {
                ok = false;
                continue;
            }
            IAEItemStack leftover = storageGrid.getItemInventory()
                .injectItems(request, Actionable.MODULATE, source);
            if (leftover != null && leftover.getStackSize() > 0) {
                ok = false;
                AdvanceDataMonitor.LOG.error(
                    "[WebAE Quest] Escrow {} could not fully return item {} x{}",
                    session.escrowId,
                    stack.getDisplayName(),
                    Long.valueOf(leftover.getStackSize()));
            }
        }
        for (FluidStack fluid : session.fluids) {
            if (fluid == null) {
                continue;
            }
            IAEFluidStack request = AEApi.instance()
                .storage()
                .createFluidStack(fluid);
            if (request == null) {
                ok = false;
                continue;
            }
            IAEFluidStack leftover = storageGrid.getFluidInventory()
                .injectItems(request, Actionable.MODULATE, source);
            if (leftover != null && leftover.getStackSize() > 0) {
                ok = false;
                AdvanceDataMonitor.LOG.error(
                    "[WebAE Quest] Escrow {} could not fully return fluid {} x{}",
                    session.escrowId,
                    fluid.getFluid() != null ? fluid.getFluid()
                        .getName() : "?",
                    Long.valueOf(leftover.getStackSize()));
            }
        }
        return ok;
    }

    private static void rollbackExtracts(IStorageGrid storageGrid, PlayerSource source, List<IAEItemStack> items,
        List<IAEFluidStack> fluids) {
        for (IAEItemStack stack : items) {
            if (stack == null) {
                continue;
            }
            IAEItemStack leftover = storageGrid.getItemInventory()
                .injectItems(stack.copy(), Actionable.MODULATE, source);
            if (leftover != null && leftover.getStackSize() > 0) {
                AdvanceDataMonitor.LOG.error(
                    "[WebAE Quest] Escrow rollback could not return item leftover x{}",
                    Long.valueOf(leftover.getStackSize()));
            }
        }
        for (IAEFluidStack stack : fluids) {
            if (stack == null) {
                continue;
            }
            IAEFluidStack leftover = storageGrid.getFluidInventory()
                .injectItems(stack.copy(), Actionable.MODULATE, source);
            if (leftover != null && leftover.getStackSize() > 0) {
                AdvanceDataMonitor.LOG.error(
                    "[WebAE Quest] Escrow rollback could not return fluid leftover x{}",
                    Long.valueOf(leftover.getStackSize()));
            }
        }
        items.clear();
        fluids.clear();
    }
}
