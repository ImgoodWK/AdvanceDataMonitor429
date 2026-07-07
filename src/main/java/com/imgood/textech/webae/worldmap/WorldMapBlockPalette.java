package com.imgood.textech.webae.worldmap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * Maps block + metadata to RGB colors for world map chunk tiles.
 */
public final class WorldMapBlockPalette {

    /** Default gray for unknown blocks ({@code #555555}). */
    private static final int UNKNOWN_RGB = 0x555555;

    private WorldMapBlockPalette() {}

    /**
     * @return 24-bit RGB (0xRRGGBB), or {@code -1} when this block has no hardcoded entry
     */
    public static int knownColorFor(Block block, int meta) {
        if (block == null || block == Blocks.air) {
            return -1;
        }

        if (block == Blocks.grass) {
            return meta == 0 ? 0x5B8733 : 0x4A6F2A;
        }
        if (block == Blocks.dirt) {
            return 0x8B6914;
        }
        if (block == Blocks.stone) {
            return 0x808080;
        }
        if (block == Blocks.cobblestone) {
            return 0x6A6A6A;
        }
        if (block == Blocks.sand) {
            return 0xDBD3A0;
        }
        if (block == Blocks.gravel) {
            return 0x8C8C8C;
        }
        if (block == Blocks.water || block == Blocks.flowing_water) {
            return 0x3366CC;
        }
        if (block == Blocks.lava || block == Blocks.flowing_lava) {
            return 0xCC3300;
        }
        if (block == Blocks.snow || block == Blocks.snow_layer) {
            return 0xF0F0FF;
        }
        if (block == Blocks.ice) {
            return 0xA0D8EF;
        }
        if (block == Blocks.log || block == Blocks.log2) {
            return 0x6B4F2A;
        }
        if (block == Blocks.leaves || block == Blocks.leaves2) {
            return 0x2D5016;
        }
        if (block == Blocks.bedrock) {
            return 0x333333;
        }
        if (block == Blocks.sandstone) {
            return 0xD8C87A;
        }
        if (block == Blocks.netherrack) {
            return 0x6B3030;
        }
        if (block == Blocks.soul_sand) {
            return 0x4A3A28;
        }
        if (block == Blocks.glowstone) {
            return 0xC8B060;
        }
        if (block == Blocks.end_stone) {
            return 0xDDDD99;
        }
        if (block == Blocks.clay) {
            return 0x9AA0A8;
        }
        if (block == Blocks.mycelium) {
            return 0x6B4F6B;
        }
        if (block == Blocks.wool) {
            return woolColor(meta & 0xF);
        }
        if (block == Blocks.planks) {
            return 0xB8945F;
        }
        if (block == Blocks.stonebrick) {
            return 0x757575;
        }
        if (block == Blocks.obsidian) {
            return 0x1A0A2E;
        }
        if (block == Blocks.glass) {
            return 0xC8D8E8;
        }
        if (block == Blocks.tallgrass || block == Blocks.deadbush) {
            return 0x6B8E23;
        }
        if (block == Blocks.farmland) {
            return 0x6B4F1A;
        }
        if (block == Blocks.hardened_clay || block == Blocks.stained_hardened_clay) {
            return block == Blocks.stained_hardened_clay ? woolColor(meta & 0xF) : 0x985E45;
        }

        return -1;
    }

    /**
     * @return 24-bit RGB (0xRRGGBB)
     */
    public static int colorFor(Block block, int meta) {
        int known = knownColorFor(block, meta);
        if (known >= 0) {
            return known;
        }
        if (block == null || block == Blocks.air) {
            return UNKNOWN_RGB;
        }
        if (block.isOpaqueCube()) {
            return 0x777777;
        }
        return UNKNOWN_RGB;
    }

    private static int woolColor(int meta) {
        switch (meta) {
            case 0:
                return 0xE9ECEC;
            case 1:
                return 0xD87F33;
            case 2:
                return 0xB24CD8;
            case 3:
                return 0x6699D8;
            case 4:
                return 0xFFD83D;
            case 5:
                return 0x7ECC55;
            case 6:
                return 0xF38BAA;
            case 7:
                return 0x3D3D3D;
            case 8:
                return 0x8E8E8E;
            case 9:
                return 0xF27636;
            case 10:
                return 0xBD80B2;
            case 11:
                return 0x3AB3DA;
            case 12:
                return 0xDECF2A;
            case 13:
                return 0x41CD34;
            case 14:
                return 0xD88198;
            case 15:
                return 0x1D1D21;
            default:
                return 0xE9ECEC;
        }
    }
}
