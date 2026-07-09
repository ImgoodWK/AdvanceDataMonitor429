package com.imgood.textech.webae.worldmap;

import com.imgood.textech.Config;

/**
 * World map snapshot acquisition mode. {@code client_only} disables all server-side tile rendering.
 */
public final class WorldMapSnapshotMode {

    public static final String CLIENT_ONLY = "client_only";
    public static final String LEGACY = "legacy";

    private WorldMapSnapshotMode() {}

    public static String normalized() {
        String raw = Config.worldMapSnapshotMode;
        if (raw == null || raw.trim().isEmpty()) {
            return CLIENT_ONLY;
        }
        raw = raw.trim().toLowerCase();
        if (LEGACY.equals(raw)) {
            return LEGACY;
        }
        return CLIENT_ONLY;
    }

    public static boolean isClientOnly() {
        return CLIENT_ONLY.equals(normalized());
    }

    public static boolean isLegacy() {
        return LEGACY.equals(normalized());
    }
}
