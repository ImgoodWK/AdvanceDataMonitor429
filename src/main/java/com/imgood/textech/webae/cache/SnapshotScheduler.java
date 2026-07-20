package com.imgood.textech.webae.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.webae.access.WebAeNetworkKeys;
import com.imgood.textech.webae.access.WebAeNetworkSuspendStore;
import com.imgood.textech.webae.api.handler.PatternListHandler;
import com.imgood.textech.webae.cells.NetworkCellSummaryCollector;
import com.imgood.textech.webae.cells.NetworkCellSummaryDto;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.GtMachineListDto;
import com.imgood.textech.webae.monitor.MonitorBindingCollector;
import com.imgood.textech.webae.monitor.MonitorBindingDto;
import com.imgood.textech.webae.pattern.PatternBrowseService;
import com.imgood.textech.webae.perf.SnapshotWorkerPool;
import com.imgood.textech.webae.perf.WebAePerfProfiler;
import com.imgood.textech.webae.player.WebAePlayerStateStore;
import com.imgood.textech.webae.scanner.LinkScannerBlockDto;
import com.imgood.textech.webae.scanner.LinkScannerCollector;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;
import com.imgood.textech.webae.snapshot.GtSnapshotCollector;
import com.imgood.textech.webae.topology.P2pTunnelDto;
import com.imgood.textech.webae.topology.P2pTunnelEnumerator;

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
    public static final String TYPE_NETWORKS = "networks";
    public static final String TYPE_P2P = "p2p";
    public static final String TYPE_CELLS = "cells";
    public static final String TYPE_MONITOR_BINDINGS = "monitor_bindings";
    public static final String TYPE_SCANNER = "scanner";
    public static final String TYPE_PATTERNS_RICH = "patterns_rich";

    /** Owner-scoped cache key uses networkId = -1. */
    public static final int OWNER_SCOPE_NETWORK_ID = -1;

    private static final ConcurrentHashMap<String, Long> lastActiveTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastStorageCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastGtCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastPatternBrowseCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastP2pCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastCellsCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastPatternsRichCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastNetworksCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastMonitorCollectTime = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> lastScannerCollectTime = new ConcurrentHashMap<String, Long>();
    private static final long ACTIVE_WINDOW_MS = 120_000L; // 2 minutes

    private static SnapshotCache snapshotCache;
    private static int storageCursor = 0;
    private static int gtCursor = 0;
    private static int patternBrowseCursor = 0;
    private static int p2pCursor = 0;
    private static int cellsCursor = 0;
    private static int patternsRichCursor = 0;
    private static int ownerCursor = 0;

    public static void setSnapshotCache(SnapshotCache cache) {
        snapshotCache = cache;
    }

    /**
     * Mark a (playerUuid, networkId) pair as recently active.
     */
    public static void markActive(String playerUuid, int networkId) {
        if (playerUuid == null || WebAePlayerStateStore.getInstance()
            .isDisabled(playerUuid)) {
            return;
        }
        if (networkId >= 0) {
            String networkKey = WebAeNetworkKeys.fromNetworkId(playerUuid, networkId);
            if (networkKey != null && WebAeNetworkSuspendStore.isSuspended(playerUuid, networkKey)) {
                return;
            }
        }
        String key = playerUuid + ":" + networkId;
        lastActiveTime.put(key, System.currentTimeMillis());
    }

    /** Drop all activity keys for an owner (e.g. after admin disable). */
    public static void clearActiveForOwner(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return;
        }
        String prefix = ownerUuid + ":";
        Iterator<String> iter = lastActiveTime.keySet()
            .iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            if (key != null && key.startsWith(prefix)) {
                iter.remove();
            }
        }
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

        // Network-scoped collections (storage / GT / patterns / p2p / cells)
        List<String> networkKeys = filterNetworkScopedKeys(active);
        if (!networkKeys.isEmpty()) {
            tickStorage(networkKeys, intervalMs, now);

            // GT collection (gtRefreshIntervalMs)
            int gtIntervalMs = Config.webGtRefreshIntervalMs;
            if (gtIntervalMs > 0) {
                tickGt(networkKeys, gtIntervalMs, now);
            }

            // Pattern browse pre-collection (webPatternCacheTtlMs)
            int patternIntervalMs = Config.webPatternCacheTtlMs;
            if (patternIntervalMs > 0) {
                tickPatternBrowse(networkKeys, patternIntervalMs, now);
            }

            // P2P / cells / rich patterns — slower interval (gt or topology TTL)
            int slowIntervalMs = Config.webGtRefreshIntervalMs > 0 ? Config.webGtRefreshIntervalMs
                : Config.webTopologyCacheTtlMs;
            if (slowIntervalMs <= 0) {
                slowIntervalMs = 10000;
            }
            tickKeyed(networkKeys, slowIntervalMs, now, lastP2pCollectTime, p2pCursor, new KeyedCollect() {

                @Override
                public boolean collect(String key, long nowMs) {
                    return enqueueP2pCollect(key);
                }

                @Override
                public void setCursor(int c) {
                    p2pCursor = c;
                }

                @Override
                public int getCursor() {
                    return p2pCursor;
                }
            });
            tickKeyed(networkKeys, slowIntervalMs, now, lastCellsCollectTime, cellsCursor, new KeyedCollect() {

                @Override
                public boolean collect(String key, long nowMs) {
                    return enqueueCellsCollect(key);
                }

                @Override
                public void setCursor(int c) {
                    cellsCursor = c;
                }

                @Override
                public int getCursor() {
                    return cellsCursor;
                }
            });
            int richInterval = Config.webPatternCacheTtlMs > 0 ? Config.webPatternCacheTtlMs : slowIntervalMs;
            tickKeyed(
                networkKeys,
                richInterval,
                now,
                lastPatternsRichCollectTime,
                patternsRichCursor,
                new KeyedCollect() {

                    @Override
                    public boolean collect(String key, long nowMs) {
                        return enqueuePatternsRichCollect(key);
                    }

                    @Override
                    public void setCursor(int c) {
                        patternsRichCursor = c;
                    }

                    @Override
                    public int getCursor() {
                        return patternsRichCursor;
                    }
                });
        }

        // Owner-scoped: networks list, monitor bindings, scanner
        List<String> owners = collectActiveOwners(active);
        if (!owners.isEmpty()) {
            int slowIntervalMs = Config.webGtRefreshIntervalMs > 0 ? Config.webGtRefreshIntervalMs
                : Config.webTopologyCacheTtlMs;
            if (slowIntervalMs <= 0) {
                slowIntervalMs = 10000;
            }
            int ownerInterval = Config.webPatternCacheTtlMs > 0 ? Config.webPatternCacheTtlMs : slowIntervalMs;
            tickOwners(owners, ownerInterval, now);
        }
    }

    private interface KeyedCollect {

        /** @return true when the job was accepted by the worker pool */
        boolean collect(String key, long nowMs);

        void setCursor(int c);

        int getCursor();
    }

    private static void tickKeyed(List<String> active, int intervalMs, long now,
        ConcurrentHashMap<String, Long> lastMap, int ignoredCursor, KeyedCollect collector) {
        int n = Math.max(1, intervalMs / 50);
        int perTick = Math.max(1, (active.size() + n - 1) / n);
        int size = active.size();
        int processed = 0;
        int idx = collector.getCursor() % size;
        while (processed < perTick) {
            String key = active.get(idx);
            Long last = lastMap.get(key);
            if (last == null || now - last >= intervalMs) {
                if (collector.collect(key, now)) {
                    lastMap.put(key, now);
                }
            }
            processed++;
            idx = (idx + 1) % size;
            if (processed >= size) break;
        }
        collector.setCursor(idx);
    }

    private static void tickOwners(List<String> owners, int intervalMs, long now) {
        int n = Math.max(1, intervalMs / 50);
        int perTick = Math.max(1, (owners.size() + n - 1) / n);
        int size = owners.size();
        int processed = 0;
        int idx = ownerCursor % size;
        while (processed < perTick) {
            String owner = owners.get(idx);
            Long lastN = lastNetworksCollectTime.get(owner);
            if (lastN == null || now - lastN >= intervalMs) {
                if (enqueueNetworksCollect(owner)) {
                    lastNetworksCollectTime.put(owner, now);
                }
            }
            Long lastM = lastMonitorCollectTime.get(owner);
            if (lastM == null || now - lastM >= intervalMs) {
                if (enqueueMonitorCollect(owner)) {
                    lastMonitorCollectTime.put(owner, now);
                }
            }
            Long lastS = lastScannerCollectTime.get(owner);
            if (lastS == null || now - lastS >= intervalMs) {
                if (enqueueScannerCollect(owner)) {
                    lastScannerCollectTime.put(owner, now);
                }
            }
            processed++;
            idx = (idx + 1) % size;
            if (processed >= size) break;
        }
        ownerCursor = idx;
    }

    private static List<String> filterNetworkScopedKeys(List<String> active) {
        List<String> out = new ArrayList<String>();
        for (String key : active) {
            int colonIdx = key.lastIndexOf(':');
            if (colonIdx < 0) continue;
            try {
                int nid = Integer.parseInt(key.substring(colonIdx + 1));
                if (nid >= 0) {
                    out.add(key);
                }
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return out;
    }

    private static List<String> collectActiveOwners(List<String> active) {
        Set<String> owners = new HashSet<String>();
        for (String key : active) {
            int colonIdx = key.lastIndexOf(':');
            if (colonIdx < 0) continue;
            owners.add(key.substring(0, colonIdx));
        }
        return new ArrayList<String>(owners);
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
                if (enqueueStorageCollect(key, now)) {
                    lastStorageCollectTime.put(key, now);
                }
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
                if (enqueuePatternBrowseCollect(key, now)) {
                    lastPatternBrowseCollectTime.put(key, now);
                }
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
                if (enqueueGtCollect(key, now)) {
                    lastGtCollectTime.put(key, now);
                }
            }
            processed++;
            idx = (idx + 1) % size;
            if (processed >= size) break;
        }
        gtCursor = idx;
    }

    private static boolean enqueueStorageCollect(final String key, final long now) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) {
            return false;
        }
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (networkId < 0) {
            return false;
        }
        return SnapshotWorkerPool.submitServerTask("storage:" + key, new Runnable() {

            @Override
            public void run() {
                AeSnapshotCollector
                    .enqueueCollectOnCurrentThread(playerUuid, networkId, new AeSnapshotCollector.SnapshotCallback() {

                        @Override
                        public void onResult(Object dto) {
                            if (dto != null) {
                                snapshotCache.put(playerUuid, networkId, TYPE_STORAGE, dto);
                            } else {
                                snapshotCache.markRefresh(playerUuid, networkId, TYPE_STORAGE);
                            }
                        }
                    });
            }
        });
    }

    private static boolean enqueuePatternBrowseCollect(final String key, final long now) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) {
            return false;
        }
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (networkId < 0) {
            return false;
        }
        return enqueueWorker("pattern_browse:" + key, new Runnable() {

            @Override
            public void run() {
                long t0 = WebAePerfProfiler.instance()
                    .begin();
                try {
                    PatternBrowseService.buildAndStoreCache(playerUuid, networkId);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG
                        .error("[WebAE] Pattern browse periodic collection failed for key={}", key, t);
                } finally {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    WebAePerfProfiler.instance()
                        .recordCollect("pattern_browse", ms);
                }
            }
        });
    }

    private static boolean enqueueGtCollect(final String key, final long now) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) {
            return false;
        }
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (networkId < 0) {
            return false;
        }
        // GT collection runs on the main thread (via HandlerTick task queue) so we
        // can safely look up the player, networks and monitor synchronously here.
        return enqueueWorker("gt:" + key, new Runnable() {

            @Override
            public void run() {
                long t0 = WebAePerfProfiler.instance()
                    .begin();
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
                } finally {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    WebAePerfProfiler.instance()
                        .recordCollect(TYPE_GT_MACHINES, ms);
                }
            }
        });
    }

    private static boolean enqueueP2pCollect(final String key) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) {
            return false;
        }
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (networkId < 0) {
            return false;
        }
        return enqueueWorker("p2p:" + key, new Runnable() {

            @Override
            public void run() {
                long t0 = WebAePerfProfiler.instance()
                    .begin();
                try {
                    if (!Config.webTopologyEnabled) {
                        return;
                    }
                    List<P2pTunnelDto> tunnels = P2pTunnelEnumerator.enumerate(playerUuid, networkId);
                    if (tunnels != null) {
                        snapshotCache.put(playerUuid, networkId, TYPE_P2P, tunnels);
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] P2P periodic collection failed for key={}", key, t);
                } finally {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    WebAePerfProfiler.instance()
                        .recordCollect(TYPE_P2P, ms);
                }
            }
        });
    }

    private static boolean enqueueCellsCollect(final String key) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) {
            return false;
        }
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (networkId < 0) {
            return false;
        }
        return enqueueWorker("cells:" + key, new Runnable() {

            @Override
            public void run() {
                long t0 = WebAePerfProfiler.instance()
                    .begin();
                try {
                    NetworkCellSummaryDto dto = NetworkCellSummaryCollector.collect(playerUuid, networkId);
                    if (dto != null) {
                        snapshotCache.put(playerUuid, networkId, TYPE_CELLS, dto);
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Cells periodic collection failed for key={}", key, t);
                } finally {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    WebAePerfProfiler.instance()
                        .recordCollect(TYPE_CELLS, ms);
                }
            }
        });
    }

    private static boolean enqueuePatternsRichCollect(final String key) {
        final int colonIdx = key.lastIndexOf(':');
        if (colonIdx < 0) {
            return false;
        }
        final String playerUuid = key.substring(0, colonIdx);
        final int networkId;
        try {
            networkId = Integer.parseInt(key.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (networkId < 0) {
            return false;
        }
        return enqueueWorker("patterns_rich:" + key, new Runnable() {

            @Override
            public void run() {
                long t0 = WebAePerfProfiler.instance()
                    .begin();
                try {
                    PatternListHandler.buildAndStoreCache(playerUuid, networkId);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Patterns-rich periodic collection failed for key={}", key, t);
                } finally {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    WebAePerfProfiler.instance()
                        .recordCollect(TYPE_PATTERNS_RICH, ms);
                }
            }
        });
    }

    private static boolean enqueueNetworksCollect(final String ownerUuid) {
        return enqueueWorker("networks:" + ownerUuid, new Runnable() {

            @Override
            public void run() {
                long t0 = WebAePerfProfiler.instance()
                    .begin();
                try {
                    List<NetworkInfo> networks = AeSnapshotCollector.findNetworks(ownerUuid, false);
                    if (networks != null) {
                        snapshotCache.put(ownerUuid, OWNER_SCOPE_NETWORK_ID, TYPE_NETWORKS, networks);
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG
                        .error("[WebAE] Networks periodic collection failed for owner={}", ownerUuid, t);
                } finally {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    WebAePerfProfiler.instance()
                        .recordCollect(TYPE_NETWORKS, ms);
                }
            }
        });
    }

    private static boolean enqueueMonitorCollect(final String ownerUuid) {
        return enqueueWorker("monitor_bindings:" + ownerUuid, new Runnable() {

            @Override
            public void run() {
                long t0 = WebAePerfProfiler.instance()
                    .begin();
                try {
                    List<MonitorBindingDto> list = MonitorBindingCollector.collect(ownerUuid);
                    if (list != null) {
                        snapshotCache.put(ownerUuid, OWNER_SCOPE_NETWORK_ID, TYPE_MONITOR_BINDINGS, list);
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG
                        .error("[WebAE] Monitor bindings collection failed for owner={}", ownerUuid, t);
                } finally {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    WebAePerfProfiler.instance()
                        .recordCollect(TYPE_MONITOR_BINDINGS, ms);
                }
            }
        });
    }

    private static boolean enqueueScannerCollect(final String ownerUuid) {
        return enqueueWorker("scanner:" + ownerUuid, new Runnable() {

            @Override
            public void run() {
                long t0 = WebAePerfProfiler.instance()
                    .begin();
                try {
                    List<LinkScannerBlockDto> list = LinkScannerCollector.collect(ownerUuid, null, null);
                    if (list != null) {
                        snapshotCache.put(ownerUuid, OWNER_SCOPE_NETWORK_ID, TYPE_SCANNER, list);
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Scanner collection failed for owner={}", ownerUuid, t);
                } finally {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    WebAePerfProfiler.instance()
                        .recordCollect(TYPE_SCANNER, ms);
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
            int colonIdx = key.lastIndexOf(':');
            if (colonIdx <= 0) {
                continue;
            }
            String owner = key.substring(0, colonIdx);
            if (WebAePlayerStateStore.getInstance()
                .isDisabled(owner)) {
                lastActiveTime.remove(key);
                continue;
            }
            try {
                int networkId = Integer.parseInt(key.substring(colonIdx + 1));
                if (networkId >= 0) {
                    String networkKey = WebAeNetworkKeys.fromNetworkId(owner, networkId);
                    if (networkKey != null && WebAeNetworkSuspendStore.isSuspended(owner, networkKey)) {
                        lastActiveTime.remove(key);
                        continue;
                    }
                }
            } catch (NumberFormatException ignored) {}
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
                lastP2pCollectTime.remove(key);
                lastCellsCollectTime.remove(key);
                lastPatternsRichCollectTime.remove(key);
                int colonIdx = key.lastIndexOf(':');
                if (colonIdx > 0) {
                    String owner = key.substring(0, colonIdx);
                    lastNetworksCollectTime.remove(owner);
                    lastMonitorCollectTime.remove(owner);
                    lastScannerCollectTime.remove(owner);
                }
            }
        }
    }

    /**
     * Force-collect networks list for an owner (async, main thread).
     */
    public static void forceCollectNetworks(final String ownerUuid) {
        if (snapshotCache == null || ownerUuid == null) return;
        markActive(ownerUuid, OWNER_SCOPE_NETWORK_ID);
        enqueueNetworksCollect(ownerUuid);
        lastNetworksCollectTime.put(ownerUuid, System.currentTimeMillis());
    }

    public static void forceCollectP2p(String playerUuid, int networkId) {
        if (snapshotCache == null) return;
        markActive(playerUuid, networkId);
        enqueueP2pCollect(playerUuid + ":" + networkId);
        lastP2pCollectTime.put(playerUuid + ":" + networkId, System.currentTimeMillis());
    }

    public static void forceCollectCells(String playerUuid, int networkId) {
        if (snapshotCache == null) return;
        markActive(playerUuid, networkId);
        enqueueCellsCollect(playerUuid + ":" + networkId);
        lastCellsCollectTime.put(playerUuid + ":" + networkId, System.currentTimeMillis());
    }

    public static void forceCollectPatternsRich(String playerUuid, int networkId) {
        if (snapshotCache == null) return;
        markActive(playerUuid, networkId);
        enqueuePatternsRichCollect(playerUuid + ":" + networkId);
        lastPatternsRichCollectTime.put(playerUuid + ":" + networkId, System.currentTimeMillis());
    }

    /**
     * Force-collect a single (playerUuid, networkId) storage snapshot, bypassing
     * the activity window and the throttle. Used by the admin refresh command/endpoint.
     */
    public static void forceCollectStorage(String playerUuid, int networkId) {
        if (snapshotCache == null) return;
        final String key = playerUuid + ":" + networkId;
        enqueueWorkerForced("storage:" + key, new Runnable() {

            @Override
            public void run() {
                AeSnapshotCollector
                    .enqueueCollectOnCurrentThread(playerUuid, networkId, new AeSnapshotCollector.SnapshotCallback() {

                        @Override
                        public void onResult(Object dto) {
                            if (dto != null) {
                                snapshotCache.put(playerUuid, networkId, TYPE_STORAGE, dto);
                                lastStorageCollectTime.put(key, System.currentTimeMillis());
                            }
                        }
                    });
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
        enqueueWorkerForced("pattern_browse:" + key, new Runnable() {

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
        final String key = playerUuid + ":" + networkId;
        enqueueWorkerForced("gt:" + key, new Runnable() {

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

    /**
     * Schedule snapshot collection via {@link SnapshotWorkerPool} with backpressure.
     *
     * @return true when accepted
     */
    private static boolean enqueueWorker(String label, Runnable serverWork) {
        return SnapshotWorkerPool.submitServerTask(label, serverWork);
    }

    private static void enqueueWorkerForced(String label, Runnable serverWork) {
        SnapshotWorkerPool.submitServerTaskForced(label, serverWork);
    }
}
