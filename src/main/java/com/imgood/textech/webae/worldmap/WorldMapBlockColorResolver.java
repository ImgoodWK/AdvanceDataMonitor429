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
            return sampled;
        }
        int known = WorldMapBlockPalette.knownColorFor(block, meta);
        if (known >= 0) {
            return known;
        }
        if (block.isOpaqueCube()) {
            return OPAQUE_UNKNOWN_RGB;
        }
        return TRANSPARENT_UNKNOWN_RGB;
    }
}
