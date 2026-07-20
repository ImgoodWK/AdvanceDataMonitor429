package com.imgood.textech.webae.worldmap;

import com.imgood.textech.Config;

/**
 * Resolves effective world map quality tier with optional AE chunk boost (Phase 4).
 */
public final class WorldMapQualitySupport {

    private WorldMapQualitySupport() {}

    /**
     * Bumps tier by one step when {@code aeChunk} and {@link Config#webWorldMapAeQualityBoost} are enabled.
     */
    public static WorldMapQualityTier effectiveTier(WorldMapQualityTier requested, boolean aeChunk) {
        WorldMapQualityTier base = WorldMapQualityTier
            .clamp(requested != null ? requested : WorldMapQualityTier.MEDIUM, WorldMapQualityTier.fromConfigMax());
        if (!aeChunk || !Config.webWorldMapAeQualityBoost) {
            return base;
        }
        WorldMapQualityTier max = WorldMapQualityTier.fromConfigMax();
        WorldMapQualityTier[] values = WorldMapQualityTier.values();
        int next = base.ordinal() + 1;
        if (next >= values.length) {
            return max;
        }
        WorldMapQualityTier boosted = values[next];
        return WorldMapQualityTier.clamp(boosted, max);
    }
}
