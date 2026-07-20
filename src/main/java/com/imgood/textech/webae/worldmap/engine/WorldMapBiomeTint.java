package com.imgood.textech.webae.worldmap.engine;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Biome color multipliers for grass, foliage, water and stems (MC 1.7.10 style).
 */
public final class WorldMapBiomeTint {

    private static final int NEUTRAL = 0xFFFFFF;

    private WorldMapBiomeTint() {}

    /**
     * @return 24-bit RGB tint multiplier (0xRRGGBB), or {@link #NEUTRAL} when no tint applies
     */
    public static int tintFor(Block block, int meta, BiomeGenBase biome, int wx, int y, int wz) {
        if (block == null || biome == null) {
            return NEUTRAL;
        }
        if (block == Blocks.grass || block == Blocks.tallgrass) {
            return biome.getBiomeGrassColor(wx, y, wz) & 0xFFFFFF;
        }
        if (block == Blocks.leaves || block == Blocks.leaves2
            || block == Blocks.vine
            || block == Blocks.waterlily
            || block == Blocks.reeds) {
            return biome.getBiomeFoliageColor(wx, y, wz) & 0xFFFFFF;
        }
        if (block == Blocks.water || block == Blocks.flowing_water) {
            return waterTint(biome);
        }
        if (block == Blocks.sapling || block == Blocks.double_plant) {
            if (meta == 0 || meta == 3) {
                return biome.getBiomeGrassColor(wx, y, wz) & 0xFFFFFF;
            }
            return biome.getBiomeFoliageColor(wx, y, wz) & 0xFFFFFF;
        }
        return NEUTRAL;
    }

    public static boolean needsTint(Block block) {
        if (block == null) {
            return false;
        }
        return block == Blocks.grass || block == Blocks.tallgrass
            || block == Blocks.leaves
            || block == Blocks.leaves2
            || block == Blocks.vine
            || block == Blocks.waterlily
            || block == Blocks.reeds
            || block == Blocks.water
            || block == Blocks.flowing_water
            || block == Blocks.sapling
            || block == Blocks.double_plant;
    }

    public static int applyTint(int rgb, int tint) {
        if (tint == NEUTRAL || tint < 0) {
            return rgb & 0xFFFFFF;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int tr = (tint >> 16) & 0xFF;
        int tg = (tint >> 8) & 0xFF;
        int tb = tint & 0xFF;
        r = r * tr / 255;
        g = g * tg / 255;
        b = b * tb / 255;
        return (r << 16) | (g << 8) | b;
    }

    private static int waterTint(BiomeGenBase biome) {
        int color = biome.color & 0xFFFFFF;
        if (color == 0) {
            return 0x3366CC;
        }
        return color;
    }
}
