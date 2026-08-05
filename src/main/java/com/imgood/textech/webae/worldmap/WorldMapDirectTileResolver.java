package com.imgood.textech.webae.worldmap;

import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.Config;

import cpw.mods.fml.relauncher.Side;

/**
 * SP direct tile resolver: FS terrain chain, client GL bridge for GL/AE layers, short TTL cache.
 */
public final class WorldMapDirectTileResolver {

    private static final WorldMapDirectTileResolver INSTANCE = new WorldMapDirectTileResolver();
    private static final int MAX_CACHE_ENTRIES = 32;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<String, CacheEntry>();

    private WorldMapDirectTileResolver() {}

    public static WorldMapDirectTileResolver instance() {
        return INSTANCE;
    }

    public DirectTileResult resolve(String layer, String ownerUuid, String actorUuid, int networkId, int dim,
        int chunkX, int chunkZ, int tilePx) {
        if (!Config.worldMapSpDirectServe || !WorldMapDirectCaptureBridge.isIntegratedSinglePlayer()
            || !WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)
            || !WorldMapPacketAuthorization.isValidOwnerUuid(actorUuid)
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId)
            || !WorldMapPacketAuthorization.isValidLayer(layer)
            || !WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ)
            || !WorldMapPacketAuthorization.isValidTilePx(tilePx)
            || WorldMapHdSupport.resolveAuthorizedProvider(ownerUuid, actorUuid, dim, networkId) == null) {
            return null;
        }
        String normalizedLayer = WorldMapTileLayer.isAe(layer) ? WorldMapTileLayer.AE : WorldMapTileLayer.TERRAIN;
        String key = cacheKey(normalizedLayer, ownerUuid, actorUuid, networkId, dim, chunkX, chunkZ, tilePx);
        long ttlMs = Math.max(1, Config.worldMapSpDirectCacheTtlSec) * 1000L;
        long now = System.currentTimeMillis();
        synchronized (cache) {
            pruneExpired(now, ttlMs);
            CacheEntry cached = cache.get(key);
            if (cached != null) {
                return new DirectTileResult(cached.png, cached.sourceId);
            }
        }

        DirectTileResult result;
        if (WorldMapTileLayer.isAe(normalizedLayer)) {
            byte[] png = WorldMapDirectCaptureBridge.instance()
                .requestClientCapture(normalizedLayer, ownerUuid, actorUuid, networkId, dim, chunkX, chunkZ, tilePx, 0L);
            if (png == null || png.length == 0) {
                return null;
            }
            result = new DirectTileResult(png, WorldMapTerrainSourceId.CLIENT_GL.id);
        } else {
            WorldMapTerrainCaptureResult terrain = WorldMapTerrainCaptureChain
                .captureTerrain(WorldMapView.FLAT, dim, chunkX, chunkZ, tilePx, Side.SERVER);
            if (terrain == null || !terrain.isValid()) {
                byte[] gl = WorldMapDirectCaptureBridge.instance()
                    .requestClientCapture(
                        WorldMapTileLayer.TERRAIN,
                        ownerUuid,
                        actorUuid,
                        networkId,
                        dim,
                        chunkX,
                        chunkZ,
                        tilePx,
                        0L);
                if (gl == null || gl.length == 0) {
                    return null;
                }
                result = new DirectTileResult(gl, WorldMapTerrainSourceId.CLIENT_GL.id);
            } else {
                result = new DirectTileResult(terrain.png, terrain.source.id);
            }
        }
        if (!WorldMapRenderSupport.isValidTilePng(result.png)) {
            return null;
        }

        CacheEntry entry = new CacheEntry();
        entry.png = result.png;
        entry.sourceId = result.sourceId;
        entry.storedAtMs = now;
        synchronized (cache) {
            pruneExpired(now, ttlMs);
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                evictOldest();
            }
            cache.put(key, entry);
        }
        return result;
    }

    private void pruneExpired(long now, long ttlMs) {
        for (java.util.Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            CacheEntry value = entry.getValue();
            if (value == null || now - value.storedAtMs > ttlMs) {
                cache.remove(entry.getKey(), value);
            }
        }
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestAt = Long.MAX_VALUE;
        for (java.util.Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            CacheEntry value = entry.getValue();
            if (value != null && value.storedAtMs < oldestAt) {
                oldestAt = value.storedAtMs;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    private static String cacheKey(String layer, String ownerUuid, String actorUuid, int networkId, int dim,
        int chunkX, int chunkZ, int tilePx) {
        return layer + "|" + ownerUuid + "|" + actorUuid + "|" + networkId + "|" + dim + "|" + chunkX + "|"
            + chunkZ + "|" + tilePx;
    }

    public static final class DirectTileResult {

        public final byte[] png;
        public final String sourceId;

        public DirectTileResult(byte[] png, String sourceId) {
            this.png = png;
            this.sourceId = sourceId;
        }
    }

    private static final class CacheEntry {

        byte[] png;
        String sourceId;
        long storedAtMs;
    }
}
