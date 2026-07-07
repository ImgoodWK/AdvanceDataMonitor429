package com.imgood.textech.webae.topology;

import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.Config;

/**
 * Manual snapshot cache for topology graphs. Snapshots are captured only via
 * {@link #captureSnapshot(String, int, String)} (or forced refresh); GET never rebuilds automatically.
 * Cold-start loads from {@link TopologySnapshotStore} when memory is empty.
 */
public final class TopologyCache {

    private static final TopologyCache INSTANCE = new TopologyCache();
    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<String, CachedEntry>();
    private final ConcurrentHashMap<String, Long> lastCaptureByNetwork = new ConcurrentHashMap<String, Long>();

    private TopologyCache() {}

    public static TopologyCache instance() {
        return INSTANCE;
    }

    public CachedResult getCached(String ownerUuid, int networkId, String mode) {
        if (!Config.webTopologyEnabled) {
            return null;
        }
        String key = cacheKey(ownerUuid, networkId, mode);
        CachedEntry entry = cache.get(key);
        if (entry != null) {
            return new CachedResult(entry.snapshot, true, entry.cachedAt, true);
        }

        TopologySnapshot disk = loadFromDisk(ownerUuid, networkId, mode);
        if (disk == null) {
            return null;
        }
        long cachedAt = disk.timestamp > 0 ? disk.timestamp : System.currentTimeMillis();
        put(ownerUuid, networkId, mode, disk);
        return new CachedResult(disk, true, cachedAt, true);
    }

    public CaptureResult captureSnapshot(String ownerUuid, int networkId, String mode, boolean force) {
        if (!Config.webTopologyEnabled) {
            return CaptureResult.disabled();
        }
        String networkKey = ownerUuid + ":" + networkId;
        ensureCooldownLoaded(ownerUuid, networkId);

        long now = System.currentTimeMillis();
        Long last = lastCaptureByNetwork.get(networkKey);
        long cooldownMs = Math.max(1000L, Config.webTopologyCacheTtlMs);
        if (!force && last != null && now - last < cooldownMs) {
            return CaptureResult.cooldown(cooldownMs - (now - last), getCached(ownerUuid, networkId, mode));
        }

        TopologySnapshot snapshot = TopologySnapshot.build(ownerUuid, networkId, mode);
        put(ownerUuid, networkId, mode, snapshot);
        lastCaptureByNetwork.put(networkKey, now);

        if (Config.webTopologySnapshotPersist) {
            TopologySnapshotStore.save(ownerUuid, networkId, mode, snapshot, now);
        }
        return CaptureResult.captured(snapshot, now);
    }

    public long remainingCooldownMs(String ownerUuid, int networkId) {
        ensureCooldownLoaded(ownerUuid, networkId);
        Long last = lastCaptureByNetwork.get(ownerUuid + ":" + networkId);
        if (last == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - last;
        long cooldownMs = Math.max(1000L, Config.webTopologyCacheTtlMs);
        return Math.max(0L, cooldownMs - elapsed);
    }

    public void put(String ownerUuid, int networkId, String mode, TopologySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        cache.put(cacheKey(ownerUuid, networkId, mode), new CachedEntry(snapshot, System.currentTimeMillis()));
    }

    private static void ensureCooldownLoaded(String ownerUuid, int networkId) {
        String networkKey = ownerUuid + ":" + networkId;
        if (INSTANCE.lastCaptureByNetwork.containsKey(networkKey)) {
            return;
        }
        if (!Config.webTopologySnapshotPersist) {
            return;
        }
        long last = TopologySnapshotStore.loadLastCaptureAt(ownerUuid, networkId);
        if (last > 0L) {
            INSTANCE.lastCaptureByNetwork.put(networkKey, last);
        }
    }

    private static TopologySnapshot loadFromDisk(String ownerUuid, int networkId, String mode) {
        if (!Config.webTopologySnapshotPersist) {
            return null;
        }
        ensureCooldownLoaded(ownerUuid, networkId);
        return TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, mode);
    }

    public static void invalidateAll() {
        INSTANCE.cache.clear();
        INSTANCE.lastCaptureByNetwork.clear();
    }

    public static void invalidateOwner(String ownerUuid) {
        if (ownerUuid == null) {
            return;
        }
        String prefix = ownerUuid + ":";
        java.util.Iterator<java.util.Map.Entry<String, CachedEntry>> it = INSTANCE.cache.entrySet()
            .iterator();
        while (it.hasNext()) {
            if (it.next()
                .getKey()
                .startsWith(prefix)) {
                it.remove();
            }
        }
        java.util.Iterator<java.util.Map.Entry<String, Long>> it2 = INSTANCE.lastCaptureByNetwork.entrySet()
            .iterator();
        while (it2.hasNext()) {
            if (it2.next()
                .getKey()
                .startsWith(prefix)) {
                it2.remove();
            }
        }
    }

    private static String cacheKey(String ownerUuid, int networkId, String mode) {
        String m = mode == null ? "logical" : mode.toLowerCase();
        if (!"spatial".equals(m)) {
            m = "logical";
        }
        return ownerUuid + ":" + networkId + ":" + m;
    }

    private static final class CachedEntry {

        final TopologySnapshot snapshot;
        final long cachedAt;

        CachedEntry(TopologySnapshot snapshot, long cachedAt) {
            this.snapshot = snapshot;
            this.cachedAt = cachedAt;
        }
    }

    public static final class CachedResult {

        public final TopologySnapshot snapshot;
        public final boolean cached;
        public final long timestamp;
        /** True when loaded from disk (memory was cold). */
        public final boolean persisted;

        public CachedResult(TopologySnapshot snapshot, boolean cached, long timestamp, boolean persisted) {
            this.snapshot = snapshot;
            this.cached = cached;
            this.timestamp = timestamp;
            this.persisted = persisted;
        }
    }

    public static final class CaptureResult {

        public final boolean success;
        public final boolean cooldown;
        public final boolean disabled;
        public final long cooldownRemainingMs;
        public final TopologySnapshot snapshot;
        public final long timestamp;

        private CaptureResult(boolean success, boolean cooldown, boolean disabled, long cooldownRemainingMs,
            TopologySnapshot snapshot, long timestamp) {
            this.success = success;
            this.cooldown = cooldown;
            this.disabled = disabled;
            this.cooldownRemainingMs = cooldownRemainingMs;
            this.snapshot = snapshot;
            this.timestamp = timestamp;
        }

        public static CaptureResult captured(TopologySnapshot snapshot, long timestamp) {
            return new CaptureResult(true, false, false, 0L, snapshot, timestamp);
        }

        public static CaptureResult cooldown(long remainingMs, CachedResult existing) {
            TopologySnapshot snap = existing != null ? existing.snapshot : null;
            long ts = existing != null ? existing.timestamp : 0L;
            return new CaptureResult(false, true, false, remainingMs, snap, ts);
        }

        public static CaptureResult disabled() {
            return new CaptureResult(false, false, true, 0L, null, 0L);
        }
    }
}
