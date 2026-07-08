package com.imgood.textech.webae.worldmap.dynmap;

import java.io.File;
import java.nio.file.Path;

import com.imgood.textech.AdvanceDataMonitor;

/**
 * Detects whether Dynmap/GWM pre-rendered tiles are available so the world map
 * can choose between {@code dynmap} and {@code self} terrain.
 *
 * <p>Detection succeeds when either the Dynmap core class is loadable <em>or</em>
 * a valid local tile root with at least one world perspective directory exists
 * (see {@link WorldMapDynmapTileRoot}).</p>
 */
public final class WorldMapDynmapDetector {

    private static final String DYNMAP_CORE_CLASS = "org.dynmap.DynmapCore";

    private static Boolean cachedAvailable;

    private WorldMapDynmapDetector() {}

    /**
     * Returns true when Dynmap mod class is present or local GWM/Dynmap tile files exist.
     */
    public static boolean isDynmapAvailable() {
        if (cachedAvailable != null) {
            return cachedAvailable.booleanValue();
        }
        boolean classPresent = isDynmapClassPresent();
        boolean tilesPresent = hasTileRootWithWorlds();
        cachedAvailable = Boolean.valueOf(classPresent || tilesPresent);
        if (cachedAvailable.booleanValue()) {
            AdvanceDataMonitor.LOG.info(
                "[WebAE-WorldMap] Dynmap/GWM terrain available (class={}, tiles={})",
                classPresent,
                tilesPresent);
        } else {
            AdvanceDataMonitor.LOG.info("[WebAE-WorldMap] Dynmap/GWM terrain not available");
        }
        return cachedAvailable.booleanValue();
    }

    /**
     * Resets the cached detection result so the next call re-probes.
     */
    public static void reset() {
        cachedAvailable = null;
        WorldMapDynmapTileRoot.reset();
    }

    private static boolean isDynmapClassPresent() {
        try {
            Class.forName(DYNMAP_CORE_CLASS, false, WorldMapDynmapDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE-WorldMap] Failed to probe Dynmap class", t);
            return false;
        }
    }

    /**
     * True when {@link WorldMapDynmapTileRoot} resolves and at least one world has a flat/iso perspective dir.
     */
    public static boolean hasTileRootWithWorlds() {
        Path root = WorldMapDynmapTileRoot.getTileRoot();
        if (root == null) {
            return false;
        }
        File[] worldDirs = root.toFile().listFiles();
        if (worldDirs == null) {
            return false;
        }
        for (File worldDir : worldDirs) {
            if (worldDir == null || !worldDir.isDirectory()) {
                continue;
            }
            if (WorldMapDynmapTileProvider.hasTiles(worldDir.getName())) {
                return true;
            }
        }
        return false;
    }
}
