package com.imgood.textech.webae.worldmap.engine;

import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;

/**
 * Resolves configured world-map render engine modes.
 */
public final class WorldMapRenderEngines {

    public static final String LEGACY = "legacy";
    public static final String UV = "uv";
    public static final String RAY = "ray";

    private WorldMapRenderEngines() {}

    public static boolean useUvFlat() {
        String mode = Config.webWorldMapRenderEngine;
        if (mode == null || mode.trim()
            .isEmpty()) {
            return true;
        }
        return !LEGACY.equalsIgnoreCase(mode.trim());
    }

    public static boolean useRayOblique() {
        return useRayOblique(null);
    }

    public static boolean useRayOblique(WorldMapQualityTier tier) {
        String mode = Config.webWorldMapObliqueEngine;
        if (mode == null || mode.trim()
            .isEmpty()) {
            return true;
        }
        if (!RAY.equalsIgnoreCase(mode.trim())) {
            return false;
        }
        if (tier == null) {
            return true;
        }
        if (tier == WorldMapQualityTier.LOW || tier == WorldMapQualityTier.MEDIUM) {
            String lowMode = Config.webWorldMapLowTierObliqueEngine;
            if (lowMode != null && LEGACY.equalsIgnoreCase(lowMode.trim())) {
                return false;
            }
        }
        return true;
    }

    public static String flatEngineId() {
        return useUvFlat() ? UV : LEGACY;
    }

    public static String obliqueEngineId() {
        return useRayOblique() ? RAY : LEGACY;
    }

    public static String obliqueEngineId(WorldMapQualityTier tier) {
        return useRayOblique(tier) ? RAY : LEGACY;
    }
}
