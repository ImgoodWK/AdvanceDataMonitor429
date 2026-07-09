package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotMode;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapChunkCropper;
import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapDetector;

/**
 * Progressive terrain tile fallback: lower cached tiers, then Dynmap chunk crop.
 */
public final class WorldMapTerrainFallback {

    public static final class Result {

        public final byte[] png;
        /** cached | dynmap_crop */
        public final String source;
        /** Quality tier of the returned PNG (may differ from requested). */
        public final WorldMapQualityTier servedTier;
        /** True when a higher tier is still being generated. */
        public final boolean upgrading;

        Result(byte[] png, String source, WorldMapQualityTier servedTier, boolean upgrading) {
            this.png = png;
            this.source = source;
            this.servedTier = servedTier;
            this.upgrading = upgrading;
        }
    }

    private WorldMapTerrainFallback() {}

    public static Result find(String view, String layer, WorldMapQualityTier requested, int dim, int chunkX,
        int chunkZ) {
        if (!Config.webWorldMapProgressiveFallback || WorldMapTileLayer.isAe(layer)) {
            return null;
        }
        WorldMapQualityTier req = requested != null ? requested : WorldMapQualityTier.MEDIUM;

        WorldMapQualityTier lower = findLowerCachedTier(view, layer, req, dim, chunkX, chunkZ);
        if (lower != null) {
            byte[] png = readCached(view, layer, lower, dim, chunkX, chunkZ);
            if (png != null) {
                // #region agent log
                AgentDebugLog91f018.log(
                    "D",
                    "WorldMapTerrainFallback.find",
                    "lower tier cached fallback",
                    "{\"chunkX\":" + chunkX + ",\"chunkZ\":" + chunkZ + ",\"servedTier\":\"" + lower
                        + "\",\"requested\":\"" + req + "\"}");
                // #endregion
                return new Result(png, "cached", lower, !lower.equals(req));
            }
        }

        if (shouldTryDynmapCrop()) {
            int targetPx = WorldMapRenderSupport.tilePx(req);
            byte[] dynmap = WorldMapDynmapChunkCropper.cropChunkPng(view, dim, chunkX, chunkZ, targetPx);
            if (WorldMapRenderSupport.isValidTilePng(dynmap)) {
                // #region agent log
                AgentDebugLog91f018.log(
                    "D",
                    "WorldMapTerrainFallback.find",
                    "dynmap crop fallback",
                    "{\"chunkX\":" + chunkX + ",\"chunkZ\":" + chunkZ + ",\"targetPx\":" + targetPx + "}");
                // #endregion
                return new Result(dynmap, "dynmap_crop", req, true);
            }
        }
        return null;
    }

    private static boolean shouldTryDynmapCrop() {
        if (WorldMapSnapshotMode.isClientOnly()) {
            return false;
        }
        if (!WorldMapDynmapDetector.isDynmapAvailable()) {
            return false;
        }
        String source = Config.worldMapTerrainSource;
        if (source == null || source.trim().isEmpty()) {
            source = "auto";
        }
        source = source.trim().toLowerCase();
        return "self".equals(source) || "auto".equals(source);
    }

    private static WorldMapQualityTier findLowerCachedTier(String view, String layer, WorldMapQualityTier requested,
        int dim, int chunkX, int chunkZ) {
        WorldMapQualityTier[] order = WorldMapQualityTier.values();
        int reqIndex = requested.ordinal();
        for (int i = reqIndex - 1; i >= 0; i--) {
            WorldMapQualityTier tier = order[i];
            if (WorldMapTileCache.exists(view, layer, tier, dim, chunkX, chunkZ, 0)) {
                return tier;
            }
        }
        return null;
    }

    private static byte[] readCached(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX,
        int chunkZ) {
        File file = WorldMapTileCache.getExisting(view, layer, tier, dim, chunkX, chunkZ, 0);
        if (file == null || !file.isFile()) {
            return null;
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            if (read <= 0) {
                return null;
            }
            if (read < data.length) {
                byte[] trimmed = new byte[read];
                System.arraycopy(data, 0, trimmed, 0, read);
                return trimmed;
            }
            return data;
        } catch (IOException e) {
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
