package com.imgood.textech.webae.cache;

import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.Config;

/**
 * Thread-safe snapshot cache for WebAE console data.
 * Caches DTOs keyed by (playerUuid, networkId, dataType) with TTL-based expiration.
 * The TTL defaults to {@code webRefreshIntervalMs * 3} to tolerate collection jitter
 * instead of a hard-coded 30 seconds.
 */
public class SnapshotCache {

    private static final long FALLBACK_MAX_AGE_MS = 30_000L;
    private static final SnapshotCache INSTANCE = new SnapshotCache();

    public static SnapshotCache instance() {
        return INSTANCE;
    }

    public SnapshotCache() {}

    /** Current TTL in milliseconds, derived from {@link Config#webRefreshIntervalMs}. */
    public long currentMaxAgeMs() {
        int interval = Config.webRefreshIntervalMs;
        if (interval <= 0) return FALLBACK_MAX_AGE_MS;
        long ttl = (long) interval * 3L;
        return ttl > 0 ? ttl : FALLBACK_MAX_AGE_MS;
    }

    private static String buildKey(String playerUuid, int networkId, String dataType) {
        return playerUuid + ":" + networkId + ":" + dataType;
    }

    public void put(String playerUuid, int networkId, String dataType, Object dto) {
        String key = buildKey(playerUuid, networkId, dataType);
        cache.put(key, new CachedEntry(dto, System.currentTimeMillis()));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String playerUuid, int networkId, String dataType) {
        String key = buildKey(playerUuid, networkId, dataType);
        CachedEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.timestamp > currentMaxAgeMs()) {
            cache.remove(key);
            return null;
        }
        return (T) entry.data;
    }

    /**
     * Read a cached entry without TTL eviction — used for "stale-but-available"
     * responses when the cache has not been refreshed yet.
     */
    @SuppressWarnings("unchecked")
    public <T> T getStale(String playerUuid, int networkId, String dataType) {
        String key = buildKey(playerUuid, networkId, dataType);
        CachedEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        return (T) entry.data;
    }

    /** Timestamp of the last cached entry, or 0 if missing. */
    public long timestampOf(String playerUuid, int networkId, String dataType) {
        String key = buildKey(playerUuid, networkId, dataType);
        CachedEntry entry = cache.get(key);
        return entry != null ? entry.timestamp : 0L;
    }

    /**
     * Monotonic version token for cursor pagination — changes when the cached snapshot is replaced
     * or invalidated so clients can detect stale cursors.
     */
    public long snapshotVersion(String playerUuid, int networkId, String dataType) {
        return timestampOf(playerUuid, networkId, dataType);
    }

    public boolean isFresh(String playerUuid, int networkId, String dataType) {
        return get(playerUuid, networkId, dataType) != null;
    }

    public void markRefresh(String playerUuid, int networkId, String dataType) {
        String key = buildKey(playerUuid, networkId, dataType);
        CachedEntry entry = cache.get(key);
        if (entry != null) {
            entry.timestamp = System.currentTimeMillis();
        }
    }

    public void invalidateAll(String playerUuid, int networkId) {
        String prefix = playerUuid + ":" + networkId + ":";
        for (String key : cache.keySet()) {
            if (key.startsWith(prefix)) {
                cache.remove(key);
            }
        }
    }

    public void invalidateAll(String playerUuid) {
        String prefix = playerUuid + ":";
        for (String key : cache.keySet()) {
            if (key.startsWith(prefix)) {
                cache.remove(key);
            }
        }
    }

    public void invalidateType(String playerUuid, int networkId, String dataType) {
        cache.remove(buildKey(playerUuid, networkId, dataType));
    }

    /** Remove all entries whose key ends with {@code :dataType}. */
    public void invalidateTypeSuffix(String dataType) {
        if (dataType == null || dataType.isEmpty()) {
            return;
        }
        String suffix = ":" + dataType;
        for (String key : cache.keySet()) {
            if (key != null && key.endsWith(suffix)) {
                cache.remove(key);
            }
        }
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }

    /** Iterate cached storage snapshots (includes stale entries). */
    public void forEachStorageSnapshot(StorageSnapshotConsumer consumer) {
        if (consumer == null) return;
        for (java.util.Map.Entry<String, CachedEntry> entry : cache.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.endsWith(":storage")) continue;
            CachedEntry cached = entry.getValue();
            if (cached == null || !(cached.data instanceof com.imgood.textech.webae.dto.StorageDto)) continue;
            consumer.accept((com.imgood.textech.webae.dto.StorageDto) cached.data);
        }
    }

    /** Iterate cached storage snapshots for one owner (includes stale entries). */
    public void forEachStorageSnapshotForOwner(String ownerUuid, StorageSnapshotConsumer consumer) {
        if (consumer == null || ownerUuid == null || ownerUuid.isEmpty()) {
            return;
        }
        String prefix = ownerUuid + ":";
        for (java.util.Map.Entry<String, CachedEntry> entry : cache.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix) || !key.endsWith(":storage")) {
                continue;
            }
            CachedEntry cached = entry.getValue();
            if (cached == null || !(cached.data instanceof com.imgood.textech.webae.dto.StorageDto)) {
                continue;
            }
            consumer.accept((com.imgood.textech.webae.dto.StorageDto) cached.data);
        }
    }

    public interface StorageSnapshotConsumer {

        void accept(com.imgood.textech.webae.dto.StorageDto dto);
    }

    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<String, CachedEntry>();

    private static class CachedEntry {

        volatile Object data;
        volatile long timestamp;

        CachedEntry(Object data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}
