package com.imgood.textech.webae.worldmap.dynmap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.worldmap.WorldMapPacketAuthorization;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;

/**
 * Reads pre-rendered Dynmap HD PNG tiles from the local tile root.
 *
 * <p>
 * Tile lookup path:
 * {@code <tileRoot>/<worldName>/<perspective>/<zoomPrefix>/<tileX>_<tileZ>.png}
 * </p>
 */
public final class WorldMapDynmapTileProvider {

    public static final int MAX_DYNMAP_ZOOM = 6;
    public static final int MAX_TILE_COORDINATE = WorldMapPacketAuthorization.MAX_CHUNK_COORDINATE;

    private WorldMapDynmapTileProvider() {}

    public static boolean isValidRequest(String worldName, int zoom, int tileX, int tileZ) {
        return WorldMapDynmapTileRoot.isValidWorldName(worldName) && zoom >= 0
            && zoom <= MAX_DYNMAP_ZOOM
            && Math.abs((long) tileX) <= MAX_TILE_COORDINATE
            && Math.abs((long) tileZ) <= MAX_TILE_COORDINATE;
    }

    public static boolean isValidPerspective(String perspective) {
        return "flat".equals(perspective) || "iso_SE_30_hires".equals(perspective);
    }

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
        if (!isValidRequest(worldName, zoom, tileX, tileZ) || !isValidPerspective(perspective)) {
            return null;
        }
        Path worldDir = WorldMapDynmapTileRoot.resolveWorldTiles(worldName);
        if (worldDir == null) {
            return null;
        }

        // Try primary zoom prefix (z_)
        String zoomPrefix = WorldMapDynmapCoordMapper.zoomPrefix(zoom);
        Path tilePath = worldDir.resolve(perspective)
            .resolve(zoomPrefix)
            .resolve(tileX + "_" + tileZ + ".png");
        byte[] data = readFile(tilePath, worldDir);
        if (data != null) {
            return data;
        }

        // Try alternative zoom prefix (zz_)
        String altZoomPrefix = WorldMapDynmapCoordMapper.altZoomPrefix(zoom);
        Path altTilePath = worldDir.resolve(perspective)
            .resolve(altZoomPrefix)
            .resolve(tileX + "_" + tileZ + ".png");
        return readFile(altTilePath, worldDir);
    }

    /**
     * Checks whether a world has any Dynmap tiles available.
     */
    public static boolean hasTiles(String worldName, String perspective) {
        if (!WorldMapDynmapTileRoot.isValidWorldName(worldName) || !isValidPerspective(perspective)) {
            return false;
        }
        Path worldDir = WorldMapDynmapTileRoot.resolveWorldTiles(worldName);
        if (worldDir == null) {
            return false;
        }
        Path perspectiveDir = worldDir.resolve(perspective);
        return perspectiveDir.toFile()
            .isDirectory();
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
            if (worldDir.resolve(p)
                .toFile()
                .isDirectory()) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readFile(Path path, Path worldDir) {
        if (path == null || worldDir == null) {
            return null;
        }
        Path normalizedWorld = worldDir.toAbsolutePath()
            .normalize();
        Path normalizedPath = path.toAbsolutePath()
            .normalize();
        if (!normalizedPath.startsWith(normalizedWorld)) {
            return null;
        }
        InputStream input = null;
        try {
            if (!Files.isRegularFile(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            Path realWorld = normalizedWorld.toRealPath();
            Path realPath = normalizedPath.toRealPath();
            if (!realPath.startsWith(realWorld) || !Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            long size = Files.size(realPath);
            if (size < WorldMapRenderSupport.MIN_VALID_TILE_BYTES || size > WorldMapRenderSupport.MAX_VALID_TILE_BYTES
                || size > Integer.MAX_VALUE) {
                return null;
            }
            int expected = (int) size;
            byte[] data = new byte[expected];
            input = Files.newInputStream(realPath);
            int offset = 0;
            while (offset < expected) {
                int read = input.read(data, offset, expected - offset);
                if (read < 0) {
                    return null;
                }
                if (read == 0) {
                    continue;
                }
                offset += read;
            }
            return WorldMapRenderSupport.isValidTilePng(data) ? data : null;
        } catch (IOException e) {
            AdvanceDataMonitor.LOG.debug("[WebAE-WorldMap] Failed to read Dynmap tile: {}", normalizedPath, e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {}
            }
        }
        return null;
    }
}
