package com.imgood.textech.webae.worldmap;

import java.io.File;
import java.io.FileOutputStream;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;

/**
 * Disk cache for world map chunk PNG tiles at
 * {@code TeXTech/WebAE/map-tiles/{view}/q{tier}/z{level}/[{ae}/]{dim}/{cx}/{cz}.png}.
 * Legacy {@code config/textech/web-map-tiles/} is read-only fallback when the new cache is empty.
 */
public final class WorldMapTileCache {

    private static File root() {
        return TeXTechDataDir.webAeDir("map-tiles");
    }

    private static File legacyConfigRoot() {
        return TeXTechDataDir.legacyWebMapTilesDir();
    }

    private static final String FLAT_VIEW = WorldMapView.FLAT.id;

    private WorldMapTileCache() {}

    public static File tileFile(String view, int dim, int chunkX, int chunkZ) {
        return tileFile(view, WorldMapTileLayer.TERRAIN, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static File tileFile(String view, String layer, int dim, int chunkX, int chunkZ) {
        return tileFile(view, layer, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static File tileFile(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ) {
        return tileFile(view, layer, quality, dim, chunkX, chunkZ, 0);
    }

    public static File tileFile(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ, int zoomLevel) {
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        int level = Math.max(0, zoomLevel);
        return tileFileAt(root(), view, layer, tier, dim, chunkX, chunkZ, level);
    }

    private static File tileFileAt(File base, String view, String layer, WorldMapQualityTier tier, int dim,
        int chunkX, int chunkZ, int zoomLevel) {
        int level = Math.max(0, zoomLevel);
        String cacheView = WorldMapTileLayer.cacheViewPath(view, layer);
        return new File(
            new File(
                new File(
                    new File(new File(new File(base, cacheView), "q" + tier.id), "z" + level),
                    String.valueOf(dim)),
                String.valueOf(chunkX)),
            chunkZ + ".png");
    }

    /** @deprecated Use {@link #tileFile(String, String, WorldMapQualityTier, int, int, int)}. */
    public static File tileFile(int dim, int chunkX, int chunkZ) {
        return tileFile(FLAT_VIEW, dim, chunkX, chunkZ);
    }

    private static File legacyTierlessFile(File base, String view, String layer, WorldMapQualityTier tier, int dim,
        int chunkX, int chunkZ) {
        String cacheView = WorldMapTileLayer.cacheViewPath(view, layer);
        return new File(
            new File(
                new File(new File(new File(base, cacheView), "q" + tier.id), String.valueOf(dim)),
                String.valueOf(chunkX)),
            chunkZ + ".png");
    }

    private static File legacyTierlessFile(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX,
        int chunkZ) {
        return legacyTierlessFile(root(), view, layer, tier, dim, chunkX, chunkZ);
    }

    private static File legacyFlatFile(File base, int dim, int chunkX, int chunkZ) {
        return new File(new File(new File(base, String.valueOf(dim)), String.valueOf(chunkX)), chunkZ + ".png");
    }

    private static File legacyFlatFile(int dim, int chunkX, int chunkZ) {
        return legacyFlatFile(root(), dim, chunkX, chunkZ);
    }

    public static boolean exists(String view, int dim, int chunkX, int chunkZ) {
        return exists(view, WorldMapTileLayer.TERRAIN, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static boolean exists(String view, String layer, int dim, int chunkX, int chunkZ) {
        return exists(view, layer, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static boolean exists(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ) {
        return exists(view, layer, quality, dim, chunkX, chunkZ, 0);
    }

    public static boolean exists(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ, int zoomLevel) {
        return getExisting(view, layer, quality, dim, chunkX, chunkZ, zoomLevel) != null;
    }

    /** @deprecated Use {@link #exists(String, String, WorldMapQualityTier, int, int, int)}. */
    public static boolean exists(int dim, int chunkX, int chunkZ) {
        return exists(FLAT_VIEW, dim, chunkX, chunkZ);
    }

    public static File getExisting(String view, int dim, int chunkX, int chunkZ) {
        return getExisting(view, WorldMapTileLayer.TERRAIN, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static File getExisting(String view, String layer, int dim, int chunkX, int chunkZ) {
        return getExisting(view, layer, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static File getExisting(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ) {
        return getExisting(view, layer, quality, dim, chunkX, chunkZ, 0);
    }

    public static File getExisting(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ, int zoomLevel) {
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        int level = Math.max(0, zoomLevel);
        File file = tileFile(view, layer, tier, dim, chunkX, chunkZ, level);
        File valid = validateExistingFile(file, view, layer, tier, dim, chunkX, chunkZ, level);
        if (valid != null) {
            return valid;
        }
        if (level == 0
            && tier == WorldMapQualityTier.MEDIUM
            && WorldMapTileLayer.TERRAIN.equals(WorldMapTileLayer.normalize(layer))
            && FLAT_VIEW.equals(normalizeView(view))) {
            valid = findLegacyMediumTerrain(view, layer, tier, dim, chunkX, chunkZ, level, root());
            if (valid != null) {
                return valid;
            }
            File legacyRoot = legacyConfigRoot();
            if (legacyRoot.isDirectory()) {
                return findLegacyMediumTerrain(view, layer, tier, dim, chunkX, chunkZ, level, legacyRoot);
            }
        }
        return null;
    }

    private static File findLegacyMediumTerrain(String view, String layer, WorldMapQualityTier tier, int dim,
        int chunkX, int chunkZ, int level, File base) {
        File legacyTierless = legacyTierlessFile(base, view, layer, tier, dim, chunkX, chunkZ);
        File valid = validateExistingFile(legacyTierless, view, layer, tier, dim, chunkX, chunkZ, level);
        if (valid != null) {
            return valid;
        }
        File legacyFlat = legacyFlatFile(base, dim, chunkX, chunkZ);
        return validateExistingFile(legacyFlat, view, layer, tier, dim, chunkX, chunkZ, level);
    }

    /** @deprecated Use {@link #getExisting(String, String, WorldMapQualityTier, int, int, int)}. */
    public static File getExisting(int dim, int chunkX, int chunkZ) {
        return getExisting(FLAT_VIEW, dim, chunkX, chunkZ);
    }

    public static void write(String view, int dim, int chunkX, int chunkZ, byte[] png) {
        write(view, WorldMapTileLayer.TERRAIN, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ, png);
    }

    public static void write(String view, String layer, int dim, int chunkX, int chunkZ, byte[] png) {
        write(view, layer, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ, png);
    }

    public static void write(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        byte[] png) {
        write(view, layer, quality, dim, chunkX, chunkZ, 0, png);
    }

    public static void write(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        int zoomLevel, byte[] png) {
        if (!WorldMapRenderSupport.isValidTilePng(png)) {
            return;
        }
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        int level = Math.max(0, zoomLevel);
        File file = tileFile(view, layer, tier, dim, chunkX, chunkZ, level);
        writeFile(file, view, layer, tier, dim, chunkX, chunkZ, level, png);
    }

    /** Writes a client-uploaded HD tile and marks it for {@code X-WorldMap-Tile-Quality: hd}. */
    public static void writeHd(String view, int dim, int chunkX, int chunkZ, byte[] png) {
        writeHd(view, WorldMapTileLayer.TERRAIN, WorldMapQualityTier.ULTRA, dim, chunkX, chunkZ, png);
    }

    public static void writeHd(String view, String layer, int dim, int chunkX, int chunkZ, byte[] png) {
        writeHd(view, layer, WorldMapQualityTier.ULTRA, dim, chunkX, chunkZ, png);
    }

    public static void writeHd(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        byte[] png) {
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.ULTRA;
        write(view, layer, tier, dim, chunkX, chunkZ, png);
        markHd(view, layer, tier, dim, chunkX, chunkZ);
    }

    public static boolean isHd(String view, int dim, int chunkX, int chunkZ) {
        return isHd(view, WorldMapTileLayer.TERRAIN, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static boolean isHd(String view, String layer, int dim, int chunkX, int chunkZ) {
        return isHd(view, layer, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static boolean isHd(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ) {
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        File marker = hdMarkerFile(view, layer, tier, dim, chunkX, chunkZ, 0);
        return marker.isFile();
    }

    /** @deprecated Use {@link #write(String, String, WorldMapQualityTier, int, int, int, byte[])}. */
    public static void write(int dim, int chunkX, int chunkZ, byte[] png) {
        write(FLAT_VIEW, dim, chunkX, chunkZ, png);
    }

    public static void invalidate(String view, int dim, int chunkX, int chunkZ) {
        invalidateAllTiers(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ);
    }

    public static void invalidate(String view, String layer, int dim, int chunkX, int chunkZ) {
        invalidateAllTiers(view, layer, dim, chunkX, chunkZ);
    }

    public static void invalidate(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ) {
        invalidateChunk(view, layer, quality, dim, chunkX, chunkZ);
    }

    /** Clears all quality tiers and zoom levels for one world chunk (including parent zoom tiles). */
    public static void invalidateChunk(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX,
        int chunkZ) {
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        int maxLevel = com.imgood.textech.webae.worldmap.engine.WorldMapZoomPyramid.configuredLevels();
        for (int level = 0; level < maxLevel; level++) {
            int tileX = com.imgood.textech.webae.worldmap.engine.WorldMapZoomPyramid.tileIndex(chunkX, level);
            int tileZ = com.imgood.textech.webae.worldmap.engine.WorldMapZoomPyramid.tileIndex(chunkZ, level);
            deleteTier(view, layer, tier, dim, tileX, tileZ, level);
        }
        if (tier == WorldMapQualityTier.MEDIUM
            && WorldMapTileLayer.TERRAIN.equals(WorldMapTileLayer.normalize(layer))
            && FLAT_VIEW.equals(normalizeView(view))) {
            deleteIfPresent(legacyTierlessFile(view, layer, tier, dim, chunkX, chunkZ));
            deleteIfPresent(hdMarkerFile(view, layer, tier, dim, chunkX, chunkZ, 0));
            deleteIfPresent(legacyFlatFile(dim, chunkX, chunkZ));
        }
    }

    /** Clears all quality tiers and zoom levels for one chunk/tile coordinate. */
    public static void invalidateAllTiers(String view, String layer, int dim, int chunkX, int chunkZ) {
        for (WorldMapQualityTier tier : WorldMapQualityTier.values()) {
            invalidateChunk(view, layer, tier, dim, chunkX, chunkZ);
        }
    }

    /** @deprecated Use {@link #invalidateChunk(String, String, WorldMapQualityTier, int, int, int)}. */
    public static void invalidateAllZoomLevels(String view, String layer, WorldMapQualityTier quality, int dim,
        int chunkX, int chunkZ) {
        invalidateChunk(view, layer, quality, dim, chunkX, chunkZ);
    }

    /** @deprecated Use {@link #invalidate(String, String, WorldMapQualityTier, int, int, int)}. */
    public static void invalidate(int dim, int chunkX, int chunkZ) {
        invalidate(FLAT_VIEW, dim, chunkX, chunkZ);
    }

    private static File validateExistingFile(File file, String view, String layer, WorldMapQualityTier tier, int dim,
        int chunkX, int chunkZ, int zoomLevel) {
        if (file.isFile() && file.length() >= WorldMapRenderSupport.MIN_VALID_TILE_BYTES) {
            return file;
        }
        if (file.isFile() && file.length() > 0L) {
            deleteIfPresent(file);
            if (zoomLevel == 0) {
                deleteIfPresent(hdMarkerFile(view, layer, tier, dim, chunkX, chunkZ, 0));
            }
        }
        return null;
    }

    private static void deleteTier(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX,
        int chunkZ, int zoomLevel) {
        deleteIfPresent(tileFile(view, layer, tier, dim, chunkX, chunkZ, zoomLevel));
        if (zoomLevel == 0) {
            deleteIfPresent(hdMarkerFile(view, layer, tier, dim, chunkX, chunkZ, 0));
        }
    }

    private static void writeFile(File file, String view, String layer, WorldMapQualityTier tier, int dim, int chunkX,
        int chunkZ, int zoomLevel, byte[] png) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to create world map tile dir: {}", parent.getAbsolutePath());
            }
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(png);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error(
                "[WebAE] Failed to write world map tile view={} layer={} tier={} dim={} cx={} cz={}",
                view,
                layer,
                tier.id,
                dim,
                chunkX,
                chunkZ,
                e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static void deleteIfPresent(File file) {
        if (file.isFile()) {
            if (!file.delete()) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Could not delete stale world map tile {}", file.getAbsolutePath());
            }
        }
    }

    private static File hdMarkerFile(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX,
        int chunkZ, int zoomLevel) {
        return new File(
            tileFile(view, layer, tier, dim, chunkX, chunkZ, zoomLevel).getParentFile(),
            chunkZ + ".hd");
    }

    private static void markHd(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX, int chunkZ) {
        markHd(view, layer, tier, dim, chunkX, chunkZ, 0);
    }

    private static void markHd(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX, int chunkZ,
        int zoomLevel) {
        File marker = hdMarkerFile(view, layer, tier, dim, chunkX, chunkZ, zoomLevel);
        File parent = marker.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to create world map HD marker dir: {}", parent.getAbsolutePath());
            }
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(marker);
            fos.write(1);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] Failed to write world map HD marker view={} layer={} tier={} dim={} cx={} cz={}",
                view,
                layer,
                tier.id,
                dim,
                chunkX,
                chunkZ,
                e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static String normalizeView(String view) {
        if (view == null || view.trim()
            .isEmpty()) {
            return FLAT_VIEW;
        }
        return view.trim()
            .toLowerCase();
    }
}
