package com.imgood.textech.webae.worldmap;

import com.imgood.textech.Config;

/**
 * World map tile quality tiers: low / medium / high / ultra.
 * Ultra uses client GL HD (512px) when online; server fallback is 256px standard.
 */
public enum WorldMapQualityTier {

    LOW("low", "adm.webae.worldmap.quality.low", 64, 4, false),
    MEDIUM("medium", "adm.webae.worldmap.quality.medium", 128, 8, false),
    HIGH("high", "adm.webae.worldmap.quality.high", 256, 16, false),
    ULTRA("ultra", "adm.webae.worldmap.quality.ultra", 512, 32, true);

    public static final int ULTRA_FALLBACK_TILE_PX = 256;
    public static final int ULTRA_FALLBACK_PX_PER_BLOCK = 16;

    public final String id;
    public final String labelKey;
    public final int tilePx;
    public final int pxPerBlock;
    public final boolean hdCapable;

    WorldMapQualityTier(String id, String labelKey, int tilePx, int pxPerBlock, boolean hdCapable) {
        this.id = id;
        this.labelKey = labelKey;
        this.tilePx = tilePx;
        this.pxPerBlock = pxPerBlock;
        this.hdCapable = hdCapable;
    }

    /** Server-side standard render resolution (ultra offline uses 256px fallback). */
    public int serverTilePx() {
        if (this == ULTRA) {
            return ULTRA_FALLBACK_TILE_PX;
        }
        return applyLegacyMediumOverride(tilePx);
    }

    public int serverPxPerBlock() {
        if (this == ULTRA) {
            return ULTRA_FALLBACK_PX_PER_BLOCK;
        }
        int px = applyLegacyMediumOverride(tilePx);
        return Math.max(1, px / 16);
    }

    /** Client HD FBO size (ultra only). */
    public int hdTilePx() {
        return tilePx;
    }

    public static WorldMapQualityTier fromId(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return MEDIUM;
        }
        String id = raw.trim()
            .toLowerCase();
        for (WorldMapQualityTier tier : values()) {
            if (tier.id.equals(id)) {
                return tier;
            }
        }
        return MEDIUM;
    }

    public static WorldMapQualityTier fromConfigMax() {
        return fromId(Config.webWorldMapMaxQualityTier);
    }

    public static WorldMapQualityTier fromConfigDefault() {
        return fromId(Config.webWorldMapDefaultQualityTier);
    }

    /** AE overlay tier from {@link Config#worldMapAeOverlayQualityTier}, clamped to max. */
    public static WorldMapQualityTier fromConfigAeOverlay() {
        return clamp(fromId(Config.worldMapAeOverlayQualityTier), fromConfigMax());
    }

    /** Clamps {@code requested} to {@code max} by ordinal. */
    public static WorldMapQualityTier clamp(WorldMapQualityTier requested, WorldMapQualityTier max) {
        if (requested == null) {
            requested = MEDIUM;
        }
        if (max == null) {
            max = ULTRA;
        }
        if (requested.ordinal() > max.ordinal()) {
            return max;
        }
        return requested;
    }

    public static WorldMapQualityTier resolveEffective(String qualityParam) {
        WorldMapQualityTier requested = qualityParam == null || qualityParam.isEmpty()
            ? fromConfigDefault()
            : fromId(qualityParam);
        return clamp(requested, fromConfigMax());
    }

    public int ordinalRank() {
        return ordinal();
    }

    public boolean isUltra() {
        return this == ULTRA;
    }

    /** True for quality tiers that are eligible for client HD rendering (high, ultra). */
    public boolean isHdEligible() {
        return this == HIGH || this == ULTRA;
    }

    private static int applyLegacyMediumOverride(int defaultPx) {
        int legacy = Config.webWorldMapTilePx;
        if (legacy != 128 && legacy >= 32 && legacy <= 512) {
            return legacy;
        }
        return defaultPx;
    }
}
