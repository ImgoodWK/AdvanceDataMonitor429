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
 *
 * <p>Storage fingerprints are tracked per (ownerUuid:networkId) so that
 * unchanged networks skip the expensive DTO-building pass. When the
 * fingerprint matches the previous collection, {@code collect()} returns
 * {@code null} to signal &quot;no change needed&quot; — the scheduler reuses
 * the cached snapshot and only bumps its timestamp via
 * {@link com.imgood.textech.webae.cache.SnapshotCache#markRefresh}.</p>
 */
public class AeSnapshotCollector {

    private static final Set<String> KNOWN_ASPECTS = buildKnownAspects();

    /** Lightweight content fingerprint per (ownerUuid:networkId). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> storageFingerprints =
        new java.util.concurrent.ConcurrentHashMap<String, Long>();

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

        // Quick fingerprint check: skip expensive DTO building when storage is unchanged.
        final String fpKey = ownerUuid + ":" + networkId;
        long fp = computeStorageFingerprint(group);
        Long prevFp = storageFingerprints.get(fpKey);
        if (prevFp != null && prevFp.longValue() == fp && fp != 0L) {
            return null; // unchanged — scheduler keeps cached snapshot alive via markRefresh
        }

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
        storageFingerprints.put(fpKey, Long.valueOf(fp));
        return dto;
    }

    /**
     * Compute a lightweight content fingerprint from the AE storage grid.
     * This is O(n) in the number of distinct items but avoids expensive per-item
     * operations like {@code getDisplayName()} and {@code itemRegistry.getNameForObject()}.
     * The fingerprint covers items, fluids, and CPU status.
     */
    private static long computeStorageFingerprint(NetworkGroup group) {
        long fp = 0;
        if (group.storageLink != null) {
            try {
                IGridNode node = ((IGridHost) group.storageLink).getGridNode(ForgeDirection.UNKNOWN);
                if (node != null && node.getGrid() != null) {
                    IStorageGrid sg = node.getGrid().getCache(IStorageGrid.class);
                    if (sg != null) {
                        // Items fingerprint: count + total amount + hash of first 20 items
                        int itemCount = 0;
                        long itemTotal = 0;
                        long itemHash = 0;
                        for (IAEItemStack stored : sg.getItemInventory().getStorageList()) {
                            if (stored == null) continue;
                            itemCount++;
                            long amount = stored.getStackSize();
                            itemTotal += amount;
                            if (itemCount <= 20) {
                                ItemStack stack = stored.getItemStack();
                                if (stack != null && stack.getItem() != null) {
                                    itemHash = itemHash * 31
                                        + (stack.getItem().hashCode() ^ stack.getItemDamage());
                                    itemHash ^= amount;
                                }
                            }
                        }
                        fp ^= ((long) itemCount << 48) ^ (itemTotal << 16) ^ itemHash;

                        // Fluids fingerprint: count + total amount
                        int fluidCount = 0;
                        long fluidTotal = 0;
                        long fluidHash = 0;
                        for (IAEFluidStack stored : sg.getFluidInventory().getStorageList()) {
                            if (stored == null) continue;
                            fluidCount++;
                            fluidTotal += stored.getStackSize();
                            if (fluidCount <= 10) {
                                net.minecraftforge.fluids.FluidStack fs = stored.getFluidStack();
                                if (fs != null && fs.getFluid() != null) {
                                    fluidHash = fluidHash * 31
                                        + fs.getFluid().hashCode();
                                    fluidHash ^= stored.getStackSize();
                                }
                            }
                        }
                        fp ^= ((long) fluidCount << 40) ^ (fluidTotal << 8) ^ fluidHash;
                    }
                }
            } catch (Exception ignored) {
                return 0L; // force full collect on error
            }
        }
        if (group.craftingLink != null) {
            try {
                group.craftingLink.updateCraftingStats();
                List<TileEntityAdvanceNetworkLink.CraftingCpuSnapshot> snaps = group.craftingLink.getCpuSnapshots();
                long cpuFp = 0;
                for (int i = 0; i < Math.min(snaps.size(), 8); i++) {
                    TileEntityAdvanceNetworkLink.CraftingCpuSnapshot snap = snaps.get(i);
                    cpuFp = cpuFp * 31 + (snap.busy ? 1 : 0);
                    cpuFp = cpuFp * 31 + snap.startItems;
                    cpuFp = cpuFp * 31 + snap.remainingItems;
                    if (snap.name != null) cpuFp = cpuFp * 31 + snap.name.hashCode();
                }
                fp ^= (cpuFp << 4) ^ snaps.size();
            } catch (Exception ignored) {
                return 0L;
            }
        }
        return fp;
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
     * Returns stale cached data when the storage fingerprint is unchanged.
     */
    public static StorageDto collectBlocking(String ownerUuid, int networkId, long timeoutMs) {
        final StorageDto[] holder = new StorageDto[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    StorageDto result = collect(ownerUuid, networkId);
                    if (result == null) {
                        // storage unchanged — return stale cache if available
                        result = com.imgood.textech.webae.cache.SnapshotCache.instance()
                            .getStale(ownerUuid, networkId, "storage");
                    }
                    holder[0] = result;
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
            // Pre-sort by amount descending so paginated reads can skip re-sorting.
            java.util.Collections.sort(items, new java.util.Comparator<ItemEntry>() {
                @Override
                public int compare(ItemEntry a, ItemEntry b) {
                    int c = Long.compare(b.amount, a.amount);
                    if (c != 0) return c;
                    String na = a.displayName != null ? a.displayName : "";
                    String nb = b.displayName != null ? b.displayName : "";
                    return na.compareToIgnoreCase(nb);
                }
            });
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
            java.util.Collections.sort(fluids, new java.util.Comparator<FluidEntry>() {
                @Override
                public int compare(FluidEntry a, FluidEntry b) {
                    int c = Long.compare(b.amount, a.amount);
                    if (c != 0) return c;
                    return safeStrCmp(a.fluidName, b.fluidName);
                }
                private int safeStrCmp(String sa, String sb) {
                    if (sa == null) sa = "";
                    if (sb == null) sb = "";
                    return sa.compareToIgnoreCase(sb);
                }
            });
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
            java.util.Collections.sort(essentias, new java.util.Comparator<EssentiaEntry>() {
                @Override
                public int compare(EssentiaEntry a, EssentiaEntry b) {
                    int c = Long.compare(b.amount, a.amount);
                    if (c != 0) return c;
                    String sa = a.aspect != null ? a.aspect : "";
                    String sb = b.aspect != null ? b.aspect : "";
                    return sa.compareToIgnoreCase(sb);
                }
            });
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
