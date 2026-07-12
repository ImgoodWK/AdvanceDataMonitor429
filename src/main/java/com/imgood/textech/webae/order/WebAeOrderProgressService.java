package com.imgood.textech.webae.order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.assistant.AssistantCraftJobManager;
import com.imgood.textech.webae.context.WebAeOwnerContext;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * Resolves WebAE order progress from AE2 {@link ICraftingLink} + CPU step counters.
 * Uses per-(owner, networkId) short-TTL CPU snapshots — O(cpus), never expands craft trees.
 */
public final class WebAeOrderProgressService {

    private static final long CACHE_TTL_MS = 50L;

    private static final ConcurrentHashMap<String, NetworkCpuCache> CACHE = new ConcurrentHashMap<String, NetworkCpuCache>();

    private WebAeOrderProgressService() {}

    public static final class Query {

        public String ownerUuid;
        public int networkId;
        public String trackingKey;
        public String craftingId;
        public ICraftingLink link;
        public String cpuName;
        public String itemName;
        public int lastProgress;
    }

    public static final class Result {

        public String status = "pending";
        public int progressPercent;
        public boolean completed;
        public boolean onCpu;
        public long startItems;
        public long remainingItems;
        public long elapsedMs;
        public String failReason;
    }

    public static final class CpuSnap {

        public String craftingId;
        public String cpuName;
        public String finalOutputName;
        public long finalOutputAmount;
        public long startItems;
        public long remainingItems;
        public long elapsedNs;
        public boolean busy;
    }

    /**
     * Resolve progress for one tracked order. Prefer {@link ICraftingLink} done/canceled,
     * then match CPU by craftingId (or cpuName + output name fallback).
     */
    public static Result resolve(Query q) {
        Result result = new Result();
        if (q == null) {
            return result;
        }

        UUID owner = parseUuid(q.ownerUuid);
        if (q.trackingKey != null && !q.trackingKey.isEmpty() && owner != null
            && AssistantCraftJobManager.instance()
                .isCalculatingByKey(owner, q.trackingKey)) {
            result.status = "pending";
            result.progressPercent = Math.max(
                1,
                AssistantCraftJobManager.instance()
                    .getCalculationProgressPercentByKey(owner, q.trackingKey));
            return result;
        }
        // Legacy displayName calc match (quest / old callers without trackingKey)
        if ((q.trackingKey == null || q.trackingKey.isEmpty()) && q.itemName != null && owner != null
            && AssistantCraftJobManager.instance()
                .isCalculating(owner, q.itemName)) {
            result.status = "pending";
            result.progressPercent = Math.max(
                1,
                AssistantCraftJobManager.instance()
                    .getCalculationProgressPercent(owner, q.itemName));
            return result;
        }

        if (q.link != null) {
            try {
                if (q.link.isCanceled()) {
                    result.status = "cancelled";
                    result.progressPercent = Math.max(0, q.lastProgress);
                    result.completed = false;
                    return result;
                }
                if (q.link.isDone()) {
                    result.status = "completed";
                    result.progressPercent = 100;
                    result.completed = true;
                    return result;
                }
            } catch (Throwable ignored) {}
        }

        NetworkCpuCache cache = getOrRefresh(q.ownerUuid, q.networkId);
        CpuSnap snap = null;
        if (cache != null) {
            if (q.craftingId != null && !q.craftingId.isEmpty()) {
                snap = cache.byCraftingId.get(q.craftingId);
            }
            if (snap == null) {
                snap = matchFallback(cache.snaps, q.cpuName, q.itemName);
            }
        }

        if (snap != null && snap.busy) {
            result.onCpu = true;
            result.status = "crafting";
            result.startItems = snap.startItems;
            result.remainingItems = snap.remainingItems;
            result.elapsedMs = snap.elapsedNs > 0L ? snap.elapsedNs / 1_000_000L : 0L;
            if (snap.startItems > 0L) {
                long done = Math.max(0L, snap.startItems - snap.remainingItems);
                result.progressPercent = (int) Math.min(99L, done * 100L / snap.startItems);
            } else {
                result.progressPercent = Math.max(0, q.lastProgress);
            }
            return result;
        }

        // Link present but CPU no longer busy and not done/canceled yet — treat as still pending
        // only if we never had a link; otherwise keep last progress as crafting until link settles.
        if (q.link != null) {
            result.status = "crafting";
            result.progressPercent = Math.max(0, q.lastProgress);
            return result;
        }

        result.status = "pending";
        result.progressPercent = Math.max(0, q.lastProgress);
        return result;
    }

    /**
     * Snapshot CPU info for a named CPU on an owner network (no player proximity search).
     */
    public static com.imgood.textech.webae.dto.OrderStatus.CpuInfo snapshotCpuInfo(String ownerUuid, int networkId,
        String cpuName) {
        if (cpuName == null || cpuName.trim()
            .isEmpty()) {
            return null;
        }
        NetworkCpuCache cache = getOrRefresh(ownerUuid, networkId);
        if (cache == null) {
            return null;
        }
        String want = cpuName.trim();
        for (int i = 0; i < cache.snaps.size(); i++) {
            CpuSnap s = cache.snaps.get(i);
            if (s.cpuName != null && s.cpuName.equals(want)) {
                com.imgood.textech.webae.dto.OrderStatus.CpuInfo info = new com.imgood.textech.webae.dto.OrderStatus.CpuInfo();
                // coProcessors / storage filled below from live CPU if possible
                ICraftingCPU cpu = findLiveCpu(ownerUuid, networkId, want);
                if (cpu != null) {
                    info.coProcessors = cpu.getCoProcessors();
                    info.storage = cpu.getAvailableStorage();
                    info.parallelism = Math.max(1, cpu.getCoProcessors() + 1);
                } else {
                    info.parallelism = 1;
                }
                return info;
            }
        }
        ICraftingCPU cpu = findLiveCpu(ownerUuid, networkId, want);
        if (cpu == null) {
            return null;
        }
        com.imgood.textech.webae.dto.OrderStatus.CpuInfo info = new com.imgood.textech.webae.dto.OrderStatus.CpuInfo();
        info.coProcessors = cpu.getCoProcessors();
        info.storage = cpu.getAvailableStorage();
        info.parallelism = Math.max(1, cpu.getCoProcessors() + 1);
        return info;
    }

    public static void invalidate(String ownerUuid, int networkId) {
        if (ownerUuid == null) {
            return;
        }
        CACHE.remove(cacheKey(ownerUuid, networkId));
    }

    private static CpuSnap matchFallback(List<CpuSnap> snaps, String cpuName, String itemName) {
        if (snaps == null || snaps.isEmpty()) {
            return null;
        }
        for (int i = 0; i < snaps.size(); i++) {
            CpuSnap s = snaps.get(i);
            if (!s.busy) {
                continue;
            }
            if (cpuName != null && !cpuName.isEmpty() && s.cpuName != null && !cpuName.equals(s.cpuName)) {
                continue;
            }
            if (itemName != null && !itemName.isEmpty() && s.finalOutputName != null) {
                if (s.finalOutputName.contains(itemName) || itemName.contains(s.finalOutputName)) {
                    return s;
                }
                continue;
            }
            if (cpuName != null && !cpuName.isEmpty() && cpuName.equals(s.cpuName)) {
                return s;
            }
        }
        return null;
    }

    private static NetworkCpuCache getOrRefresh(String ownerUuid, int networkId) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return null;
        }
        String key = cacheKey(ownerUuid, networkId);
        long now = System.currentTimeMillis();
        NetworkCpuCache existing = CACHE.get(key);
        if (existing != null && now - existing.fetchedAtMs <= CACHE_TTL_MS) {
            return existing;
        }
        NetworkCpuCache fresh = fetch(ownerUuid, networkId, now);
        if (fresh != null) {
            CACHE.put(key, fresh);
        }
        return fresh;
    }

    private static NetworkCpuCache fetch(String ownerUuid, int networkId, long now) {
        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        if (grid == null) {
            return null;
        }
        ICraftingGrid craftingGrid;
        try {
            craftingGrid = grid.getCache(ICraftingGrid.class);
        } catch (Throwable t) {
            return null;
        }
        if (craftingGrid == null) {
            return null;
        }
        Collection<ICraftingCPU> cpus;
        try {
            cpus = craftingGrid.getCpus();
        } catch (Throwable t) {
            return null;
        }
        NetworkCpuCache cache = new NetworkCpuCache();
        cache.fetchedAtMs = now;
        if (cpus == null || cpus.isEmpty()) {
            return cache;
        }
        for (ICraftingCPU cpu : cpus) {
            if (cpu == null) {
                continue;
            }
            CpuSnap snap = new CpuSnap();
            snap.cpuName = cpu.getName();
            snap.busy = cpu.isBusy();
            snap.startItems = cpu.getStartItemCount();
            snap.remainingItems = cpu.getRemainingItemCount();
            snap.elapsedNs = cpu.getElapsedTime();
            IAEItemStack out = cpu.getFinalOutput();
            if (out != null) {
                try {
                    snap.finalOutputName = out.getItemStack()
                        .getDisplayName();
                } catch (Throwable t) {
                    snap.finalOutputName = String.valueOf(out);
                }
                snap.finalOutputAmount = out.getStackSize();
            }
            try {
                if (cpu instanceof CraftingCPUCluster) {
                    ICraftingLink last = ((CraftingCPUCluster) cpu).getLastCraftingLink();
                    if (last != null) {
                        snap.craftingId = last.getCraftingID();
                    }
                }
            } catch (Throwable ignored) {}
            cache.snaps.add(snap);
            if (snap.craftingId != null && !snap.craftingId.isEmpty()) {
                cache.byCraftingId.put(snap.craftingId, snap);
            }
        }
        return cache;
    }

    private static ICraftingCPU findLiveCpu(String ownerUuid, int networkId, String cpuName) {
        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        if (grid == null || cpuName == null) {
            return null;
        }
        try {
            ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
            if (craftingGrid == null) {
                return null;
            }
            Collection<ICraftingCPU> cpus = craftingGrid.getCpus();
            if (cpus == null) {
                return null;
            }
            for (ICraftingCPU cpu : cpus) {
                if (cpu != null && cpuName.equals(cpu.getName())) {
                    return cpu;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String cacheKey(String ownerUuid, int networkId) {
        return ownerUuid + "#" + networkId;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class NetworkCpuCache {

        long fetchedAtMs;
        final List<CpuSnap> snaps = new ArrayList<CpuSnap>();
        final Map<String, CpuSnap> byCraftingId = new HashMap<String, CpuSnap>();
    }
}
