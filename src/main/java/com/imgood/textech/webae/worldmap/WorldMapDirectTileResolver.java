package com.imgood.textech.webae.worldmap;

import java.util.concurrent.ConcurrentHashMap;

import com.imgood.textech.Config;

import cpw.mods.fml.relauncher.Side;

/**
 * SP direct tile resolver: FS terrain chain, client GL bridge for GL/AE layers, short TTL cache.
 */
public final class WorldMapDirectTileResolver {

    private static final WorldMapDirectTileResolver INSTANCE = new WorldMapDirectTileResolver();
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<String, CacheEntry>();

    private WorldMapDirectTileResolver() {}

    public static WorldMapDirectTileResolver instance() {
        return INSTANCE;
    }

    public DirectTileResult resolve(String layer, String ownerUuid, int networkId, int dim, int chunkX, int chunkZ,
        int tilePx) {
        if (!Config.worldMapSpDirectServe || !WorldMapDirectCaptureBridge.isIntegratedSinglePlayer()) {
            return null;
        }
        String key = cacheKey(layer, ownerUuid, networkId, dim, chunkX, chunkZ, tilePx);
        long ttlMs = Math.max(1, Config.worldMapSpDirectCacheTtlSec) * 1000L;
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(key);
        if (cached != null && now - cached.storedAtMs <= ttlMs) {
            return new DirectTileResult(cached.png, cached.sourceId);
        }

        DirectTileResult result;
        if (WorldMapTileLayer.isAe(layer)) {
            byte[] png = WorldMapDirectCaptureBridge.instance()
                .requestClientCapture(layer, ownerUuid, networkId, dim, chunkX, chunkZ, tilePx, 0L);
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

        CacheEntry entry = new CacheEntry();
        entry.png = result.png;
        entry.sourceId = result.sourceId;
        entry.storedAtMs = now;
        cache.put(key, entry);
        return result;
    }

    private static String cacheKey(String layer, String ownerUuid, int networkId, int dim, int chunkX, int chunkZ,
        int tilePx) {
        return layer + "|" + ownerUuid + "|" + networkId + "|" + dim + "|" + chunkX + "|" + chunkZ + "|" + tilePx;
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
