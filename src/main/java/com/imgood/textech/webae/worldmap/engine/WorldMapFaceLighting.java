package com.imgood.textech.webae.worldmap.engine;

import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

/**
 * Face-direction shading with smooth lighting (neighbor light interpolation),
 * ambient occlusion, and simplified GWM ShadowHDLighting face shadows.
 */
public final class WorldMapFaceLighting {

    private static final float TOP_SHADE = 1.0f;
    private static final float NORTH_SOUTH_SHADE = 0.85f;
    private static final float EAST_WEST_SHADE = 0.75f;
    private static final float BOTTOM_SHADE = 0.65f;
    private static final float AMBIENT = 0.12f;
    private static final float SKY_WEIGHT = 0.75f;
    private static final float BLOCK_WEIGHT = 0.25f;

    /** AO attenuation per occluding neighbor (0.0 = full occlusion). */
    private static final float AO_NEIGHBOR_ATTENUATION = 0.6f;
    /** Minimum AO factor after all occlusions. */
    private static final float AO_MIN = 0.25f;
    /** Smooth lighting sample radius in blocks. */
    private static final int SMOOTH_RADIUS = 1;

    private WorldMapFaceLighting() {}

    /**
     * Standard face shading with single-point skyLight/blockLight.
     */
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
        return applyFactor(rgb, factor);
    }

    /**
     * Enhanced shading with smooth lighting (neighbor interpolation), AO, and face shadow.
     * Call on the server-side Java2D path where WorldMapChunkContext is available.
     */
    public static int shadeRgbSmooth(int rgb, WorldMapBlockColorResolver.BlockFace face, int wx, int wy, int wz,
        WorldMapChunkContext ctx) {
        if (ctx == null) {
            return shadeRgb(rgb, face, 15, 0);
        }
        // Smooth lighting: sample neighbors and weighted average
        float smoothLight = smoothLightFactor(ctx, wx, wy, wz);

        // Simple AO for this face
        float ao = aoFactor(ctx, wx, wy, wz, face);

        float faceShade = faceShade(face);
        float factor = faceShade * ao * (AMBIENT + smoothLight * (1.0f - AMBIENT));
        if (factor > 1.0f) {
            factor = 1.0f;
        }
        if (factor < 0.0f) {
            factor = 0.0f;
        }
        return applyFactor(rgb, factor);
    }

    /**
     * Smooth lighting: samples skyLight and blockLight from the block and its neighbors
     * in a (2*radius+1)³ kernel, then does a weighted average (center has more weight).
     */
    private static float smoothLightFactor(WorldMapChunkContext ctx, int wx, int wy, int wz) {
        float totalSky = 0f;
        float totalBlock = 0f;
        float totalWeight = 0f;
        for (int dx = -SMOOTH_RADIUS; dx <= SMOOTH_RADIUS; dx++) {
            for (int dy = -SMOOTH_RADIUS; dy <= SMOOTH_RADIUS; dy++) {
                for (int dz = -SMOOTH_RADIUS; dz <= SMOOTH_RADIUS; dz++) {
                    int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    float w = dist == 0 ? 4f : (dist == 1 ? 2f : 1f);
                    int sky = ctx.skyLight(wx + dx, wy + dy, wz + dz);
                    int block = ctx.blockLight(wx + dx, wy + dy, wz + dz);
                    totalSky += clampLight(sky) * w;
                    totalBlock += clampLight(block) * w;
                    totalWeight += w;
                }
            }
        }
        if (totalWeight <= 0f) {
            return 1.0f;
        }
        float avgSky = totalSky / totalWeight;
        float avgBlock = totalBlock / totalWeight;
        float combined = avgSky * SKY_WEIGHT + avgBlock * BLOCK_WEIGHT;
        return combined / 15.0f;
    }

    /**
     * Simple ambient occlusion: checks the 3 vertex-adjacent blocks for each face.
     * Returns attenuation factor (1.0 = no occlusion, lower = darker).
     */
    private static float aoFactor(WorldMapChunkContext ctx, int wx, int wy, int wz,
        WorldMapBlockColorResolver.BlockFace face) {
        if (face == null) {
            return 1.0f;
        }
        int occlusions = 0;
        switch (face) {
            case TOP:
                // Top face: check NE, NW, SE, SW at y+1
                if (isOccluding(ctx, wx + 1, wy + 1, wz)) occlusions++;
                if (isOccluding(ctx, wx - 1, wy + 1, wz)) occlusions++;
                if (isOccluding(ctx, wx, wy + 1, wz + 1)) occlusions++;
                if (isOccluding(ctx, wx, wy + 1, wz - 1)) occlusions++;
                break;
            case BOTTOM:
                if (isOccluding(ctx, wx + 1, wy - 1, wz)) occlusions++;
                if (isOccluding(ctx, wx - 1, wy - 1, wz)) occlusions++;
                if (isOccluding(ctx, wx, wy - 1, wz + 1)) occlusions++;
                if (isOccluding(ctx, wx, wy - 1, wz - 1)) occlusions++;
                break;
            case NORTH:
                if (isOccluding(ctx, wx + 1, wy, wz - 1)) occlusions++;
                if (isOccluding(ctx, wx - 1, wy, wz - 1)) occlusions++;
                if (isOccluding(ctx, wx, wy + 1, wz - 1)) occlusions++;
                if (isOccluding(ctx, wx, wy - 1, wz - 1)) occlusions++;
                break;
            case SOUTH:
                if (isOccluding(ctx, wx + 1, wy, wz + 1)) occlusions++;
                if (isOccluding(ctx, wx - 1, wy, wz + 1)) occlusions++;
                if (isOccluding(ctx, wx, wy + 1, wz + 1)) occlusions++;
                if (isOccluding(ctx, wx, wy - 1, wz + 1)) occlusions++;
                break;
            case EAST:
                if (isOccluding(ctx, wx + 1, wy, wz + 1)) occlusions++;
                if (isOccluding(ctx, wx + 1, wy, wz - 1)) occlusions++;
                if (isOccluding(ctx, wx + 1, wy + 1, wz)) occlusions++;
                if (isOccluding(ctx, wx + 1, wy - 1, wz)) occlusions++;
                break;
            case WEST:
                if (isOccluding(ctx, wx - 1, wy, wz + 1)) occlusions++;
                if (isOccluding(ctx, wx - 1, wy, wz - 1)) occlusions++;
                if (isOccluding(ctx, wx - 1, wy + 1, wz)) occlusions++;
                if (isOccluding(ctx, wx - 1, wy - 1, wz)) occlusions++;
                break;
            default:
                return 1.0f;
        }
        float factor = 1.0f - (1.0f - AO_NEIGHBOR_ATTENUATION) * occlusions;
        if (factor < AO_MIN) {
            factor = AO_MIN;
        }
        return factor;
    }

    private static boolean isOccluding(WorldMapChunkContext ctx, int wx, int wy, int wz) {
        net.minecraft.block.Block block = ctx.blockAt(wx, wy, wz);
        return block != null && block.isOpaqueCube();
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

    private static int applyFactor(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r * factor);
        g = (int) (g * factor);
        b = (int) (b * factor);
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        return (r << 16) | (g << 8) | b;
    }
}
