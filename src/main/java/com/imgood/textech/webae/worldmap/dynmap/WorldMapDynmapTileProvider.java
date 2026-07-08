package com.imgood.textech.webae.worldmap.dynmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.imgood.textech.AdvanceDataMonitor;

/**
 * Reads pre-rendered Dynmap HD PNG tiles from the local tile root.
 *
 * <p>Tile lookup path:
 * {@code <tileRoot>/<worldName>/<perspective>/<zoomPrefix>/<tileX>_<tileZ>.png}</p>
 */
public final class WorldMapDynmapTileProvider {

    private WorldMapDynmapTileProvider() {}

    /**
     * Reads a Dynmap tile PNG from disk.
     *
     * @param worldName   the world name as used in the Dynmap tiles directory
     * @param perspective the Dynmap perspective prefix (e.g. {@code flat}, {@code iso_SE_30_hires})
     * @param zoom        zoom level (0 = native)
     * @param tileX       tile X index
     * @param tileZ       tile Z index
     * @return tile PNG bytes, or {@code null} when no tile exists
     */
    public static byte[] getTile(String worldName, String perspective, int zoom, int tileX, int tileZ) {
        Path worldDir = WorldMapDynmapTileRoot.resolveWorldTiles(worldName);
        if (worldDir == null) {
            return null;
        }

        // Try primary zoom prefix (z_)
        String zoomPrefix = WorldMapDynmapCoordMapper.zoomPrefix(zoom);
        Path tilePath = worldDir.resolve(perspective).resolve(zoomPrefix)
            .resolve(tileX + "_" + tileZ + ".png");
        byte[] data = readFile(tilePath);
        if (data != null) {
            return data;
        }

        // Try alternative zoom prefix (zz_)
        String altZoomPrefix = WorldMapDynmapCoordMapper.altZoomPrefix(zoom);
        Path altTilePath = worldDir.resolve(perspective).resolve(altZoomPrefix)
            .resolve(tileX + "_" + tileZ + ".png");
        return readFile(altTilePath);
    }

    /**
     * Checks whether a world has any Dynmap tiles available.
     */
    public static boolean hasTiles(String worldName, String perspective) {
        Path worldDir = WorldMapDynmapTileRoot.resolveWorldTiles(worldName);
        if (worldDir == null) {
            return false;
        }
        Path perspectiveDir = worldDir.resolve(perspective);
        return perspectiveDir.toFile().isDirectory();
    }

    /**
     * Checks whether a world has any Dynmap tiles available (any perspective).
     */
    public static boolean hasTiles(String worldName) {
        Path worldDir = WorldMapDynmapTileRoot.resolveWorldTiles(worldName);
        if (worldDir == null) {
            return false;
        }
        String[] candidates = { "flat", "iso_SE_30_hires" };
        for (String p : candidates) {
            if (worldDir.resolve(p).toFile().isDirectory()) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readFile(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.debug("[WebAE-WorldMap] Failed to read Dynmap tile: {}", path, e);
        }
        return null;
    }
}
