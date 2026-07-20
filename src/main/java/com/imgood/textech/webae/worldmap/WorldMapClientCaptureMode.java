package com.imgood.textech.webae.worldmap;

import com.imgood.textech.Config;

/**
 * Client-side GL capture policy for world map terrain tiles.
 */
public final class WorldMapClientCaptureMode {

    public static final String OFF = "off";
    public static final String ULTRA_ONLY = "ultra_only";
    public static final String WHEN_ONLINE = "when_online";

    private WorldMapClientCaptureMode() {}

    public static String normalized() {
        String raw = Config.worldMapClientCaptureMode;
        if (raw == null || raw.trim()
            .isEmpty()) {
            return WHEN_ONLINE;
        }
        return raw.trim()
            .toLowerCase();
    }

    public static boolean isEnabled() {
        return !OFF.equals(normalized());
    }

    /** True when client GL should be preferred over server software render for this tier. */
    public static boolean shouldUseClientForTier(WorldMapQualityTier tier) {
        if (!Config.webWorldMapClientHdEnabled || tier == null) {
            return false;
        }
        String mode = normalized();
        if (OFF.equals(mode)) {
            return false;
        }
        if (ULTRA_ONLY.equals(mode)) {
            return tier.isHdEligible();
        }
        return true;
    }
}
