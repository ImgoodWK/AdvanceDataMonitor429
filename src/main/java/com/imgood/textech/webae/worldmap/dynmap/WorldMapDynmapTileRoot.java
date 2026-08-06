package com.imgood.textech.webae.worldmap.dynmap;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

/**
 * Resolves the local Dynmap tile root directory.
 *
 * <p>
 * Lookup order:
 * <ol>
 * <li>Configured {@code worldMapDynmapTileRoot} (absolute or relative to instance root)</li>
 * <li>Default {@code dynmap/web/tiles/} relative to the server/instance root</li>
 * </ol>
 * </p>
 */
public final class WorldMapDynmapTileRoot {

    private static final String DEFAULT_RELATIVE = "dynmap" + File.separator + "web" + File.separator + "tiles";

    private static Path cachedTileRoot;

    private WorldMapDynmapTileRoot() {}

    /**
     * Returns the resolved Dynmap tile root directory, or {@code null} when none is configured or found.
     */
    public static Path getTileRoot() {
        if (cachedTileRoot != null) {
            return cachedTileRoot;
        }
        String configured = Config.worldMapDynmapTileRoot;
        if (configured != null && !configured.trim()
            .isEmpty()) {
            File configuredDir = new File(configured.trim());
            if (configuredDir.isAbsolute() && configuredDir.isDirectory()) {
                cachedTileRoot = configuredDir.toPath();
                AdvanceDataMonitor.LOG.info("[WebAE-WorldMap] Dynmap tile root from config: {}", cachedTileRoot);
                return cachedTileRoot;
            }
            // Try as relative to the current working directory (instance root)
            File relativeDir = new File(configured.trim());
            if (relativeDir.isDirectory()) {
                cachedTileRoot = relativeDir.toPath();
                AdvanceDataMonitor.LOG.info(
                    "[WebAE-WorldMap] Dynmap tile root from config (relative): {}",
                    cachedTileRoot.toAbsolutePath());
                return cachedTileRoot;
            }
            AdvanceDataMonitor.LOG
                .warn("[WebAE-WorldMap] Configured worldMapDynmapTileRoot '{}' is not a valid directory", configured);
        }
        // Fallback: dynmap/web/tiles/
        File defaultDir = new File(DEFAULT_RELATIVE);
        if (defaultDir.isDirectory()) {
            cachedTileRoot = defaultDir.toPath();
            AdvanceDataMonitor.LOG
                .info("[WebAE-WorldMap] Dynmap tile root from default: {}", cachedTileRoot.toAbsolutePath());
            return cachedTileRoot;
        }
        AdvanceDataMonitor.LOG.info("[WebAE-WorldMap] No Dynmap tile root found");
        return null;
    }

    /**
     * Resolves the tiles subdirectory for a specific world name.
     * Returns {@code null} when the root or world directory does not exist.
     */
    public static Path resolveWorldTiles(String worldName) {
        Path root = getTileRoot();
        if (root == null || !isValidWorldName(worldName)) {
            return null;
        }
        Path normalizedRoot = root.toAbsolutePath()
            .normalize();
        Path worldDir = normalizedRoot.resolve(worldName.trim())
            .normalize();
        if (!worldDir.startsWith(normalizedRoot) || !worldDir.toFile()
            .isDirectory()) {
            return null;
        }
        try {
            Path realRoot = normalizedRoot.toRealPath();
            Path realWorld = worldDir.toRealPath();
            return realWorld.startsWith(realRoot) ? realWorld : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** World names are one directory segment, never a path expression. */
    public static boolean isValidWorldName(String worldName) {
        if (worldName == null) {
            return false;
        }
        String value = worldName.trim();
        if (value.isEmpty() || ".".equals(value)
            || "..".equals(value)
            || value.getBytes(StandardCharsets.UTF_8).length > 128) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resets cached root so the next call re-scans.
     */
    public static void reset() {
        cachedTileRoot = null;
    }
}
