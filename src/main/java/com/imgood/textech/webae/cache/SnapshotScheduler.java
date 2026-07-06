package com.imgood.textech.webae.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.GtMachineListDto;
import com.imgood.textech.webae.pattern.PatternBrowseService;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;
import com.imgood.textech.webae.snapshot.GtSnapshotCollector;

/**
 * Periodic snapshot scheduler driven by {@link com.imgood.textech.handler.HandlerTick}.
 *
 * Phase-1 behavior:
 * <ul>
 * <li>Storage snapshots are collected every {@link Config#webRefreshIntervalMs} ms,
 * spread across ticks so each tick only enqueues {@code ceil(activeKeys / N)}
 * collection tasks where {@code N = intervalMs / 50ms}. Only networks active
 * within the last 2 minutes are collected.</li>
 * <li>GT machine snapshots are collected every {@link Config#webGtRefreshIntervalMs}
 * ms using the same spreading strategy but a separate cursor.</li>
 * </ul>
 */
public class SnapshotScheduler {

    public static final String TYPE_STORAGE = "storage";
    public static final String TYPE_GT_MACHINES = "gt_machines";

    private static final ConcurrentHashMap<String, Long> lastActiveTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastStorageCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastGtCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastPatternBrowseCollectTime = new ConcurrentHashMap<String, Long>();
    private static final long ACTIVE_WINDOW_MS = 120_000L; // 2 minutes

    private static SnapshotCache snapshotCache;
    private static int storageCursor = 0;
    private static int gtCursor = 0;
    private static int patternBrowseCursor = 0;

    public static void setSnapshotCache(SnapshotCache cache) {
        snapshotCache = cache;
    }

    /**
     * Mark a (playerUuid, networkId) pair as recently active.
     */
    public static void markActive(String playerUuid, int networkId) {
        String key = playerUuid + ":" + networkId;
        lastActiveTime.put(key, System.currentTimeMillis());
    }

    /**
     * Called every server tick from HandlerTick. Spreads collection work across
     * ticks to keep per-tick main-thread load bounded.
     */
    public static void onServerTick() {
        if (snapshotCache == null) {
            return;
        }
        int intervalMs = Config.webRefreshIntervalMs;
        if (intervalMs <= 0) {
            return;
        }
        long now = System.currentTimeMillis();

        // Prune stale active keys first
        pruneStale(now);

        List<String> active = collectActiveKeys(now);
        if (active.isEmpty()) {
            return;
        }

        // Storage collection (intervalMs)
        tickStorage(active, intervalMs, now);

        // GT collection (gtRefreshIntervalMs) — only keys with bound GT machines are collected
        int gtIntervalMs = Config.webGtRefreshIntervalMs;
        if (gtIntervalMs > 0) {
            tickGt(active, gtIntervalMs, now);
        }

        // Pattern browse pre-collection (webPatternCacheTtlMs)
        int patternIntervalMs = Config.webPatternCacheTtlMs;
        if (patternIntervalMs > 0) {
            tickPatternBrowse(active, patternIntervalMs, now);
        }
    }

    private static void tickStorage(List<String> active, int intervalMs, long now) {
        int n = Math.max(1, intervalMs / 50);
        int perTick = Math.max(1, (active.size() + n - 1) / n);
        int size = active.size();
        int processed = 0;
        int idx = storageCursor % size;
        while (processed < perTick) {
            String key = active.get(idx);
            Long last = lastStorageCollectTime.get(key);
            if (last == null || now - last >= intervalMs) {
                enqueueStorageCollect(key, now);
                lastStorageCollectTime.put(key, now);
            }
            processed++;
            idx = (idx + 1) % size;
            if (processed >= size) break;
        }
        storageCursor = idx;
    }

    private static void tickPatternBrowse(List<String> active, int intervalMs, long now) {
        int n = Math.max(1, intervalMs / 50);
        int perTick = Math.max(1, (active.size() + n - 1) / n);
        int size = active.size();
        int processed = 0;
        int idx = patternBrowseCursor % size;
        while (processed < perTick) {
            String key = active.get(idx);
            Long last = lastPatternBrowseCollectTime.get(key);
            if (last == null || now - last >= intervalMs) {
                enqueuePatternBrowseCollect(key, now);
                lastPatternBrowseCollectTime.put(key, now);
            }
            processed++;
            idx = (idx + 1) % size;
            if (processed >= size) break;
        }
        patternBrowseCursor = idx;
    }

    private static void tickGt(List<String> active, int intervalMs, long now) {
        int n = Math.max(1, intervalMs / 50);
        int perTick = Math.max(1, (active.size() + n - 1) / n);
        int size = active.size();
        int processed = 0;
        int idx = gtCursor % size;
        while (processed < perTick) {
            String key = active.get(idx);
            Long last = lastGtCollectTime.get(key);
            if (last == null || now - last >= intervalMs) {
                enqueueGtCollect(key, now);
                lastGtCollectTime.put(key, now);
            }
            processed++;
            idx = (idx + 1) % size;
            if (processed >= size) break;
        }
        gtCursor = idx;
    }

    private static void enqueueStorageCollect(final String key, final long now) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) return;
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return;
        }
        AeSnapshotCollector.enqueueCollect(playerUuid, networkId, new AeSnapshotCollector.SnapshotCallback() {

            @Override
            public void onResult(Object dto) {
                if (dto != null) {
                    snapshotCache.put(playerUuid, networkId, TYPE_STORAGE, dto);
                }
            }
        });
    }

    private static void enqueuePatternBrowseCollect(final String key, final long now) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) return;
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return;
        }
        HandlerTickEnqueue.enqueue(new Runnable() {

            @Override
            public void run() {
                try {
                    PatternBrowseService.buildAndStoreCache(playerUuid, networkId);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG
                        .error("[WebAE] Pattern browse periodic collection failed for key={}", key, t);
                }
            }
        });
    }

    private static void enqueueGtCollect(final String key, final long now) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) return;
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return;
        }
        // GT collection runs on the main thread (via HandlerTick task queue) so we
        // can safely look up the player, networks and monitor synchronously here.
        HandlerTickEnqueue.enqueue(new Runnable() {

            @Override
            public void run() {
                try {
                    List<NetworkInfo> networks = AeSnapshotCollector.findNetworks(playerUuid);
                    if (networkId < 0 || networkId >= networks.size()) return;
                    TileEntityAdvanceDataMonitor monitor = WebAeOwnerContext.getMonitor(playerUuid, networkId);
                    if (monitor == null) return;
                    GtMachineListDto dto = GtSnapshotCollector.collect(playerUuid, networkId, monitor);
                    if (dto != null) {
                        snapshotCache.put(playerUuid, networkId, TYPE_GT_MACHINES, dto);
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] GT periodic collection failed for key={}", key, t);
                }
            }
        });
    }

    private static List<String> collectActiveKeys(long now) {
        Set<String> snapshot = new HashSet<String>(lastActiveTime.keySet());
        List<String> active = new ArrayList<String>(snapshot.size());
        for (String key : snapshot) {
            Long t = lastActiveTime.get(key);
            if (t == null) continue;
            if (now - t > ACTIVE_WINDOW_MS) {
                lastActiveTime.remove(key);
                continue;
            }
            active.add(key);
        }
        return active;
    }

    private static void pruneStale(long now) {
        Iterator<String> it = lastActiveTime.keySet()
            .iterator();
        while (it.hasNext()) {
            String key = it.next();
            Long t = lastActiveTime.get(key);
            if (t == null || now - t > ACTIVE_WINDOW_MS) {
                it.remove();
                lastStorageCollectTime.remove(key);
                lastGtCollectTime.remove(key);
                lastPatternBrowseCollectTime.remove(key);
            }
        }
    }

    /**
     * Force-collect a single (playerUuid, networkId) storage snapshot, bypassing
     * the activity window and the throttle. Used by the admin refresh command/endpoint.
     */
    public static void forceCollectStorage(String playerUuid, int networkId) {
        if (snapshotCache == null) return;
        final String key = playerUuid + ":" + networkId;
        AeSnapshotCollector.enqueueCollect(playerUuid, networkId, new AeSnapshotCollector.SnapshotCallback() {

            @Override
            public void onResult(Object dto) {
                if (dto != null) {
                    snapshotCache.put(playerUuid, networkId, TYPE_STORAGE, dto);
                    lastStorageCollectTime.put(key, System.currentTimeMillis());
                }
            }
        });
    }

    /**
     * Force-collect GT machines for a single (playerUuid, networkId). Runs on main thread.
     */
    /**
     * Force-rebuild pattern browse cache for a single (playerUuid, networkId). Runs on main thread.
     */
    public static void forceCollectPatternBrowse(final String playerUuid, final int networkId) {
        final String key = playerUuid + ":" + networkId;
        HandlerTickEnqueue.enqueue(new Runnable() {

            @Override
            public void run() {
                try {
                    PatternBrowseService.buildAndStoreCache(playerUuid, networkId);
                    lastPatternBrowseCollectTime.put(key, System.currentTimeMillis());
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error(
                        "[WebAE] Forced pattern browse collection failed for player={} network={}",
                        playerUuid,
                        networkId,
                        t);
                }
            }
        });
    }

    public static void forceCollectGt(final String playerUuid, final int networkId) {
        if (snapshotCache == null) return;
        HandlerTickEnqueue.enqueue(new Runnable() {

            @Override
            public void run() {
                try {
                    List<NetworkInfo> networks = AeSnapshotCollector.findNetworks(playerUuid);
                    if (networkId < 0 || networkId >= networks.size()) return;
                    TileEntityAdvanceDataMonitor monitor = WebAeOwnerContext.getMonitor(playerUuid, networkId);
                    if (monitor == null) return;
                    GtMachineListDto dto = GtSnapshotCollector.collect(playerUuid, networkId, monitor);
                    if (dto != null) {
                        snapshotCache.put(playerUuid, networkId, TYPE_GT_MACHINES, dto);
                        lastGtCollectTime.put(playerUuid + ":" + networkId, System.currentTimeMillis());
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error(
                        "[WebAE] Forced GT collection failed for player={} network={}",
                        playerUuid,
                        networkId,
                        t);
                }
            }
        });
    }

    public static int activeNetworkCount() {
        return lastActiveTime.size();
    }

    private static EntityPlayerMP findPlayer(String playerUuid) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP mp = (EntityPlayerMP) obj;
                if (mp.getUniqueID()
                    .toString()
                    .equals(playerUuid)) {
                    return mp;
                }
            }
        }
        return null;
    }

    /**
     * Thin wrapper around {@link com.imgood.textech.handler.HandlerTick#enqueueServerTask}
     * to keep the cache layer decoupled from the handler package.
     */
    private static final class HandlerTickEnqueue {

        static void enqueue(Runnable r) {
            com.imgood.textech.handler.HandlerTick.enqueueServerTask(r);
        }
    }
}
