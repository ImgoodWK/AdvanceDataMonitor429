package com.imgood.textech.webae.worldmap.dynmap;

import java.io.File;
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
        if (root == null || worldName == null
            || worldName.trim()
                .isEmpty()) {
            return null;
        }
        Path worldDir = root.resolve(worldName.trim());
        if (!worldDir.toFile()
            .isDirectory()) {
            return null;
        }
        return worldDir;
    }

    /**
     * Resets cached root so the next call re-scans.
     */
    public static void reset() {
        cachedTileRoot = null;
    }
}
