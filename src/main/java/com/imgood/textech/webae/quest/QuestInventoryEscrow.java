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

/**
 * In-memory AE virtual escrow for WebAE quest submit/detect.
 * Extracts stacks with {@link Actionable#MODULATE}, holds them until commit (consume) or release (return).
 */
public final class QuestInventoryEscrow {

    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<String, Session>();

    private QuestInventoryEscrow() {}

    public static final class Session {

        public final String escrowId;
        public final String ownerUuid;
        public final int networkId;
        public final long createdAtMs;
        public final long deadlineMs;
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

    /**
     * Lock remaining SUBMIT/DETECT requirements from AE. On partial failure, already-extracted stacks
     * are injected back and the session is discarded.
     */
    public static LockResult lock(String ownerUuid, int networkId, EntityPlayerMP player,
        List<QuestAnalysisStepDto> steps) {
        LockResult result = new LockResult();
        if (!Config.webQuestEscrowEnabled) {
            result.message = "Quest escrow disabled";
            return result;
        }
        if (ownerUuid == null || player == null || steps == null || steps.isEmpty()) {
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
        long timeout = Config.webQuestEscrowTimeoutMs > 0 ? Config.webQuestEscrowTimeoutMs
            : Config.webQuestCraftWaitTimeoutMs;
        String escrowId = UUID.randomUUID()
            .toString()
            .substring(0, 12);
        Session session = new Session(escrowId, ownerUuid, networkId, System.currentTimeMillis() + timeout);

        List<IAEItemStack> extractedItems = new ArrayList<IAEItemStack>();
        List<IAEFluidStack> extractedFluids = new ArrayList<IAEFluidStack>();

        for (QuestAnalysisStepDto step : steps) {
            if (step == null || step.complete || !step.webCapable) {
                continue;
            }
            boolean isSubmit = QuestTaskDeserializer.WEB_SUBMIT.equals(step.webAction);
            boolean isDetect = QuestTaskDeserializer.WEB_DETECT.equals(step.webAction);
            if (!isSubmit && !isDetect) {
                continue;
            }

            if (step.fluidName != null && !step.fluidName.isEmpty() && step.fluidRemaining > 0) {
                if (step.fluidMissing > 0) {
                    rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
                    result.message = "Insufficient AE fluid for escrow: " + step.fluidName;
                    return result;
                }
                FluidStack need = QuestTaskDeserializer.parseFluidStack(step.fluidName, step.fluidRemaining);
                if (need == null) {
                    rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
                    result.message = "Unknown fluid: " + step.fluidName;
                    return result;
                }
                IAEFluidStack request = AEApi.instance()
                    .storage()
                    .createFluidStack(need);
                if (request == null) {
                    rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
                    result.message = "Cannot create AE fluid request";
                    return result;
                }
                IAEFluidStack extracted = storageGrid.getFluidInventory()
                    .extractItems(request, Actionable.MODULATE, source);
                if (extracted == null || extracted.getStackSize() < need.amount) {
                    if (extracted != null && extracted.getStackSize() > 0) {
                        extractedFluids.add(extracted);
                    }
                    rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
                    result.message = "Insufficient AE fluid stock: " + step.fluidName;
                    return result;
                }
                extractedFluids.add(extracted);
                FluidStack held = extracted.getFluidStack();
                if (held != null) {
                    session.fluids.add(held.copy());
                }
                continue;
            }

            if (step.registryName != null && !step.registryName.isEmpty() && step.remaining > 0) {
                if (step.missing > 0) {
                    rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
                    result.message = "Insufficient AE items for escrow: " + step.registryName;
                    return result;
                }
                ItemStack proto = stackFromKey(step.registryName, step.meta);
                if (proto == null) {
                    rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
                    result.message = "Unknown item: " + step.registryName;
                    return result;
                }
                IAEItemStack request = AEApi.instance()
                    .storage()
                    .createItemStack(proto);
                if (request == null) {
                    rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
                    result.message = "Cannot create AE item request";
                    return result;
                }
                request.setStackSize(step.remaining);
                IAEItemStack extracted = storageGrid.getItemInventory()
                    .extractItems(request, Actionable.MODULATE, source);
                if (extracted == null || extracted.getStackSize() < step.remaining) {
                    if (extracted != null && extracted.getStackSize() > 0) {
                        extractedItems.add(extracted);
                    }
                    rollbackExtracts(storageGrid, source, extractedItems, extractedFluids);
                    result.message = "Insufficient AE item stock: " + step.registryName;
                    return result;
                }
                extractedItems.add(extracted);
                ItemStack held = extracted.getItemStack();
                if (held != null) {
                    session.items.add(held.copy());
                }
            }
        }

        if (session.items.isEmpty() && session.fluids.isEmpty()) {
            result.success = true;
            result.escrowId = "";
            result.message = "Nothing to lock";
            result.session = session;
            return result;
        }

        SESSIONS.put(escrowId, session);
        result.success = true;
        result.escrowId = escrowId;
        result.session = session;
        result.message = "Escrow locked";
        return result;
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
            AdvanceDataMonitor.LOG.error(
                "[WebAE Quest] Escrow {} release failed: no storage grid",
                session.escrowId);
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

    private static void rollbackExtracts(IStorageGrid storageGrid, PlayerSource source,
        List<IAEItemStack> items, List<IAEFluidStack> fluids) {
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
