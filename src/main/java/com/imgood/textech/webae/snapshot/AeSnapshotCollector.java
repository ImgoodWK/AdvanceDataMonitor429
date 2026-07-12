package com.imgood.textech.webae.snapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.dto.StorageDto.CpuEntry;
import com.imgood.textech.webae.dto.StorageDto.EssentiaEntry;
import com.imgood.textech.webae.dto.StorageDto.FluidEntry;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;

/**
 * Main-thread AE2 snapshot collector for WebAE console.
 * All AE2 operations must run on the server thread.
 * HTTP handlers call {@link #collectBlocking(String, int, long)} which enqueues
 * via HandlerTick and waits with timeout for the result.
 */
public class AeSnapshotCollector {

    private static final Set<String> KNOWN_ASPECTS = buildKnownAspects();

    private static Set<String> buildKnownAspects() {
        Set<String> set = new HashSet<String>();
        try {
            Class<?> aspectClass = Class.forName("thaumcraft.api.aspects.Aspect");
            Object aspectsList = aspectClass.getMethod("aspects")
                .invoke(null);
            if (aspectsList instanceof Map) {
                for (Object key : ((Map<?, ?>) aspectsList).keySet()) {
                    if (key instanceof String) {
                        set.add(((String) key).toLowerCase());
                    }
                }
            }
        } catch (Throwable ignored) {}
        return set;
    }

    /**
     * Find all AE networks for an owner. Must be called from server thread.
     */
    public static List<NetworkInfo> findNetworks(String ownerUuid) {
        return findNetworks(ownerUuid, false);
    }

    public static List<NetworkInfo> findNetworks(String ownerUuid, boolean forceRefresh) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return WebAeOwnerContext.findNetworksForOwner(ownerUuid, forceRefresh);
    }

    /**
     * Collect AE storage snapshot for a specific network. Must be called from server thread.
     */
    public static StorageDto collect(String ownerUuid, int networkId) {
        long t0 = System.nanoTime();
        try {
            return collectInner(ownerUuid, networkId);
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            com.imgood.textech.webae.perf.WebAePerfProfiler.instance()
                .recordCollect("storage", ms);
        }
    }

    private static StorageDto collectInner(String ownerUuid, int networkId) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return emptyDto(networkId);
        }
        List<NetworkGroup> groups = WebAeOwnerContext.findNetworkGroups(ownerUuid);
        if (networkId < 0 || networkId >= groups.size()) {
            return emptyDto(networkId);
        }
        NetworkGroup group = groups.get(networkId);
        StorageDto dto = new StorageDto();
        dto.networkId = networkId;
        dto.timestamp = System.currentTimeMillis();

        if (group.storageLink != null) {
            collectItems(group.storageLink, dto);
            collectFluids(group.storageLink, dto);
            collectEssentia(group.storageLink, dto);
        }
        if (group.networkLink != null) {
            dto.bytesUsed = group.networkLink.getItemUsedBytes() + group.networkLink.getFluidUsedBytes();
            dto.bytesMax = group.networkLink.getItemTotalBytes() + group.networkLink.getFluidTotalBytes();
        }
        if (group.craftingLink != null) {
            collectCpus(group.craftingLink, dto, group);
        }
        return dto;
    }

    /**
     * Async enqueue on server thread with callback.
     */
    public static void enqueueCollect(final String ownerUuid, final int networkId, final SnapshotCallback callback) {
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    callback.onResult(collect(ownerUuid, networkId));
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Snapshot collection failed", t);
                    callback.onResult(null);
                }
            }
        });
    }

    /**
     * Blocking collect for HTTP handlers. Enqueues on server thread and waits up to timeoutMs.
     */
    public static StorageDto collectBlocking(String ownerUuid, int networkId, long timeoutMs) {
        final StorageDto[] holder = new StorageDto[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = collect(ownerUuid, networkId);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Snapshot collection failed", t);
                    holder[0] = null;
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return holder[0];
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        AdvanceDataMonitor.LOG.warn("[WebAE] Snapshot collection timed out owner={} network={}", ownerUuid, networkId);
        return null;
    }

    /**
     * Blocking network enumeration for HTTP handlers.
     */
    public static List<NetworkInfo> findNetworksBlocking(String ownerUuid, long timeoutMs) {
        return findNetworksBlocking(ownerUuid, timeoutMs, false);
    }

    public static List<NetworkInfo> findNetworksBlocking(String ownerUuid, long timeoutMs, boolean forceRefresh) {
        final List<NetworkInfo>[] holder = new List[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = findNetworks(ownerUuid, forceRefresh);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Network enumeration failed", t);
                    holder[0] = java.util.Collections.emptyList();
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return holder[0];
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return java.util.Collections.emptyList();
    }

    public static void invalidateConnectors(String ownerUuid) {
        WebAeOwnerContext.invalidateConnectors(ownerUuid);
    }

    // ---- data collection ----

    private static void collectItems(TileEntityAdvanceNetworkLink storageLink, StorageDto dto) {
        try {
            IGridNode node = ((IGridHost) storageLink).getGridNode(ForgeDirection.UNKNOWN);
            if (node == null || node.getGrid() == null) return;
            IStorageGrid sg = node.getGrid()
                .getCache(IStorageGrid.class);
            if (sg == null) return;

            List<ItemEntry> items = new ArrayList<ItemEntry>();
            for (IAEItemStack stored : sg.getItemInventory()
                .getStorageList()) {
                if (stored == null) continue;
                ItemStack stack = stored.getItemStack();
                if (stack == null || stack.getItem() == null) continue;

                String regName = "";
                try {
                    Object nameObj = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                    regName = nameObj != null ? nameObj.toString() : "";
                } catch (Throwable ignored) {}

                String nbtHash = "";
                if (stack.hasTagCompound()) {
                    nbtHash = String.valueOf(
                        stack.getTagCompound()
                            .hashCode());
                }

                int meta = stack.getItemDamage();
                if (meta == Short.MAX_VALUE) meta = 0;
                String itemId = com.imgood.textech.webae.recipe.RecipeItemEntries.buildItemId(regName, meta);
                if (itemId == null || itemId.isEmpty()) {
                    itemId = stack.getItem()
                        .getUnlocalizedName() + ":"
                        + meta;
                }

                items.add(new ItemEntry(itemId, stack.getDisplayName(), regName, meta, stored.getStackSize(), nbtHash));
            }
            dto.items = items;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to access storage grid for items", e);
        }
    }

    private static void collectFluids(TileEntityAdvanceNetworkLink storageLink, StorageDto dto) {
        try {
            IGridNode node = ((IGridHost) storageLink).getGridNode(ForgeDirection.UNKNOWN);
            if (node == null || node.getGrid() == null) return;
            IStorageGrid sg = node.getGrid()
                .getCache(IStorageGrid.class);
            if (sg == null) return;

            List<FluidEntry> fluids = new ArrayList<FluidEntry>();
            for (IAEFluidStack stored : sg.getFluidInventory()
                .getStorageList()) {
                if (stored == null) continue;
                net.minecraftforge.fluids.FluidStack fs = stored.getFluidStack();
                if (fs == null || fs.getFluid() == null) continue;
                fluids.add(
                    new FluidEntry(
                        fs.getFluid()
                            .getName(),
                        stored.getStackSize()));
            }
            dto.fluids = fluids;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to access storage grid for fluids", e);
        }
    }

    private static void collectEssentia(TileEntityAdvanceNetworkLink storageLink, StorageDto dto) {
        if (KNOWN_ASPECTS.isEmpty()) return;
        try {
            IGridNode node = ((IGridHost) storageLink).getGridNode(ForgeDirection.UNKNOWN);
            if (node == null || node.getGrid() == null) return;
            IStorageGrid sg = node.getGrid()
                .getCache(IStorageGrid.class);
            if (sg == null) return;

            List<EssentiaEntry> essentias = new ArrayList<EssentiaEntry>();
            for (IAEFluidStack stored : sg.getFluidInventory()
                .getStorageList()) {
                if (stored == null) continue;
                net.minecraftforge.fluids.FluidStack fs = stored.getFluidStack();
                if (fs == null || fs.getFluid() == null) continue;
                if (KNOWN_ASPECTS.contains(
                    fs.getFluid()
                        .getName()
                        .toLowerCase())) {
                    essentias.add(
                        new EssentiaEntry(
                            fs.getFluid()
                                .getName(),
                            stored.getStackSize()));
                }
            }
            dto.essentia = essentias;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to access storage grid for essentia", e);
        }
    }

    private static void collectCpus(TileEntityAdvanceNetworkLink craftingLink, StorageDto dto, NetworkGroup group) {
        try {
            craftingLink.updateCraftingStats();
            List<TileEntityAdvanceNetworkLink.CraftingCpuSnapshot> snaps = craftingLink.getCpuSnapshots();
            List<CpuEntry> entries = new ArrayList<CpuEntry>();
            int linkDim = craftingLink.getWorldObj() != null ? craftingLink.getWorldObj().provider.dimensionId : 0;
            int monitorDim = group.monitorDim;
            for (int i = 0; i < snaps.size(); i++) {
                TileEntityAdvanceNetworkLink.CraftingCpuSnapshot snap = snaps.get(i);
                long maxItems = snap.startItems;
                long storedItems = Math.max(0, snap.startItems - snap.remainingItems);
                double progress = 0.0;
                if (snap.startItems > 0) {
                    progress = (double) storedItems / snap.startItems;
                }
                String finalOutputName = snap.busy ? snap.finalOutputName : null;
                long finalOutputAmount = snap.busy ? snap.finalOutputAmount : 0L;
                String name = snap.name;
                if (name == null || name.isEmpty()) {
                    name = "CPU#" + (i + 1);
                }
                long elapsedMs = snap.elapsedTime > 0
                    ? TimeUnit.MILLISECONDS.convert(snap.elapsedTime, TimeUnit.NANOSECONDS)
                    : 0L;
                entries.add(
                    new CpuEntry(
                        name,
                        storedItems,
                        maxItems,
                        progress,
                        snap.busy,
                        snap.availableStorage,
                        snap.usedStorage,
                        snap.coProcessors,
                        finalOutputName,
                        finalOutputAmount,
                        elapsedMs,
                        craftingLink.xCoord,
                        craftingLink.yCoord,
                        craftingLink.zCoord,
                        linkDim,
                        group.monitorX,
                        group.monitorY,
                        group.monitorZ,
                        monitorDim,
                        snap.remainingItems,
                        snap.startItems));
            }
            dto.cpus = entries;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to collect CPU info", e);
        }
    }

    private static StorageDto emptyDto(int networkId) {
        StorageDto dto = new StorageDto();
        dto.networkId = networkId;
        dto.timestamp = System.currentTimeMillis();
        return dto;
    }

    // ---- inner types ----

    public static class NetworkInfo {

        public int networkId;
        public int monitorDim;
        public int monitorX;
        public int monitorY;
        public int monitorZ;
        public boolean hasStorage;
        public boolean hasCrafting;
        public boolean hasNetworkLink;
    }

    public interface SnapshotCallback {

        void onResult(Object dto);
    }
}
