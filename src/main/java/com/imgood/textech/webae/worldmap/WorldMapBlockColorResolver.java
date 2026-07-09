package com.imgood.textech.webae.worldmap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * Three-level color resolution for world-map tiles:
 * JAR texture sample → hardcoded palette → generic gray fallback.
 */
public final class WorldMapBlockColorResolver {

    public enum BlockFace {
        TOP,
        BOTTOM,
        NORTH,
        SOUTH,
        EAST,
        WEST
    }

    private static final int OPAQUE_UNKNOWN_RGB = 0x777777;
    private static final int TRANSPARENT_UNKNOWN_RGB = 0x555555;

    // #region agent log
    private static int statTexture;
    private static int statPalette;
    private static int statOpaqueFallback;
    private static int statTransparentFallback;

    public static void resetColorStats() {
        statTexture = 0;
        statPalette = 0;
        statOpaqueFallback = 0;
        statTransparentFallback = 0;
    }

    public static String colorStatsJson() {
        return "{\"texture\":" + statTexture + ",\"palette\":" + statPalette + ",\"opaqueFallback\":"
            + statOpaqueFallback + ",\"transparentFallback\":" + statTransparentFallback + "}";
    }
    // #endregion

    private WorldMapBlockColorResolver() {}

    /**
     * @return 24-bit RGB (0xRRGGBB)
     */
    public static int colorFor(Block block, int meta) {
        return colorFor(block, meta, BlockFace.TOP);
    }

    /**
     * @return 24-bit RGB (0xRRGGBB)
     */
    public static int colorFor(Block block, int meta, BlockFace face) {
        if (block == null || block == Blocks.air) {
            return TRANSPARENT_UNKNOWN_RGB;
        }
        BlockFace sampleFace = face != null ? face : BlockFace.TOP;
        int sampled = WorldMapBlockTextureSampler.sampleFaceColor(block, meta, sampleFace);
        if (sampled >= 0) {
            // #region agent log
            statTexture++;
            // #endregion
            return sampled;
        }
        int known = WorldMapBlockPalette.knownColorFor(block, meta);
        if (known >= 0) {
            // #region agent log
            statPalette++;
            // #endregion
            return known;
        }
        if (block.isOpaqueCube()) {
            // #region agent log
            statOpaqueFallback++;
            // #endregion
            return OPAQUE_UNKNOWN_RGB;
        }
        // #region agent log
        statTransparentFallback++;
        // #endregion
        return TRANSPARENT_UNKNOWN_RGB;
    }
}
