package com.imgood.textech.webae.worldmap.engine;

import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

/**
 * Lightweight face-direction shading multiplied by block/sky light (Dynmap ShadowHDLighting lite).
 */
public final class WorldMapFaceLighting {

    private static final float TOP_SHADE = 1.0f;
    private static final float NORTH_SOUTH_SHADE = 0.85f;
    private static final float EAST_WEST_SHADE = 0.75f;
    private static final float BOTTOM_SHADE = 0.65f;
    private static final float AMBIENT = 0.12f;
    private static final float SKY_WEIGHT = 0.75f;
    private static final float BLOCK_WEIGHT = 0.25f;

    private WorldMapFaceLighting() {}

    public static int shadeRgb(int rgb, WorldMapBlockColorResolver.BlockFace face, int skyLight,
        int blockLight) {
        float faceShade = faceShade(face);
        float light = lightFactor(skyLight, blockLight);
        float factor = faceShade * (AMBIENT + light * (1.0f - AMBIENT));
        if (factor > 1.0f) {
            factor = 1.0f;
        }
        if (factor < 0.0f) {
            factor = 0.0f;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r * factor);
        g = (int) (g * factor);
        b = (int) (b * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static float faceShade(WorldMapBlockColorResolver.BlockFace face) {
        if (face == null) {
            return TOP_SHADE;
        }
        switch (face) {
            case TOP:
                return TOP_SHADE;
            case BOTTOM:
                return BOTTOM_SHADE;
            case NORTH:
            case SOUTH:
                return NORTH_SOUTH_SHADE;
            case EAST:
            case WEST:
                return EAST_WEST_SHADE;
            default:
                return TOP_SHADE;
        }
    }

    private static float lightFactor(int skyLight, int blockLight) {
        int sky = clampLight(skyLight);
        int block = clampLight(blockLight);
        float combined = sky * SKY_WEIGHT + block * BLOCK_WEIGHT;
        return combined / 15.0f;
    }

    private static int clampLight(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 15) {
            return 15;
        }
        return value;
    }
}
