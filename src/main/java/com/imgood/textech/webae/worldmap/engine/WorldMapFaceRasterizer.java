package com.imgood.textech.webae.worldmap.engine;

import java.awt.image.BufferedImage;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.imgood.textech.webae.worldmap.WorldMapAeCategory;
import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;

/**
 * Rasterizes a block face into a tile region using texture UV sampling, biome tint and lighting.
 */
public final class WorldMapFaceRasterizer {

    private static final int TEX_SIZE = 16;

    private WorldMapFaceRasterizer() {}

    public static void rasterizeTopFace(BufferedImage tile, int destX, int destZ, int ppb, Block block, int meta,
        int wx, int wz, WorldMapChunkContext ctx) {
        rasterizeFace(
            tile,
            destX,
            destZ,
            ppb,
            block,
            meta,
            wx,
            wz,
            ctx,
            WorldMapBlockColorResolver.BlockFace.TOP,
            0xFF);
    }

    /**
     * Rasterize block top-face shape using texture alpha; pixel RGB encodes {@code categoryId} in the R channel.
     */
    public static void rasterizeTopFaceCategoryId(BufferedImage tile, int destX, int destZ, int ppb, Block block,
        int meta, int wx, int wz, WorldMapChunkContext ctx, int categoryId) {
        rasterizeFaceCategoryId(
            tile,
            destX,
            destZ,
            ppb,
            block,
            meta,
            wx,
            wz,
            ctx,
            WorldMapBlockColorResolver.BlockFace.TOP,
            categoryId);
    }

    public static void rasterizeFaceCategoryId(BufferedImage tile, int destX, int destZ, int ppb, Block block, int meta,
        int wx, int wz, WorldMapChunkContext ctx, WorldMapBlockColorResolver.BlockFace face, int categoryId) {
        if (tile == null || block == null || block == Blocks.air || ppb <= 0 || ctx == null) {
            return;
        }
        int cid = categoryId & 0xFF;
        if (cid <= 0) {
            return;
        }
        int startX = destX * ppb;
        int startZ = destZ * ppb;
        int tileW = tile.getWidth();
        int tileH = tile.getHeight();
        if (startX >= tileW || startZ >= tileH) {
            return;
        }
        BufferedImage texture = WorldMapTextureRegistry.faceTexture(block, meta, face);
        int endX = Math.min(startX + ppb, tileW);
        int endZ = Math.min(startZ + ppb, tileH);
        for (int dz = startZ; dz < endZ; dz++) {
            for (int dx = startX; dx < endX; dx++) {
                int localX = dx - startX;
                int localZ = dz - startZ;
                int alpha = sampleAlpha(texture, localX, localZ, ppb);
                if (alpha <= 0) {
                    continue;
                }
                int argb = WorldMapAeCategory.argbForCategory(cid, alpha);
                if (argb != 0) {
                    tile.setRGB(dx, dz, argb);
                }
            }
        }
    }

    private static int sampleAlpha(BufferedImage texture, int localX, int localZ, int ppb) {
        if (texture == null) {
            return 0xFF;
        }
        int u = localX * TEX_SIZE / ppb;
        int v = localZ * TEX_SIZE / ppb;
        if (u < 0 || u >= texture.getWidth() || v < 0 || v >= texture.getHeight()) {
            return 0xFF;
        }
        int argb = texture.getRGB(u, v);
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) {
            int rgb = WorldMapTextureRegistry.samplePixelRgb(texture, u, v);
            return rgb >= 0 ? 0xFF : 0;
        }
        return a;
    }

    public static void rasterizeFace(BufferedImage tile, int destX, int destZ, int ppb, Block block, int meta,
        int wx, int wz, WorldMapChunkContext ctx, WorldMapBlockColorResolver.BlockFace face, int alpha) {
        if (tile == null || block == null || block == Blocks.air || ppb <= 0 || ctx == null) {
            return;
        }
        int startX = destX * ppb;
        int startZ = destZ * ppb;
        int tileW = tile.getWidth();
        int tileH = tile.getHeight();
        if (startX >= tileW || startZ >= tileH) {
            return;
        }

        BufferedImage texture = WorldMapTextureRegistry.faceTexture(block, meta, face);
        BiomeGenBase biome = ctx.biome(wx, wz);
        int y = ctx.findTopSolidY(wx, wz);
        if (y < 0) {
            y = ctx.findTopBlockY(wx, wz);
        }
        int tint = WorldMapBiomeTint.tintFor(block, meta, biome, wx, y, wz);
        int fallback = WorldMapBlockColorResolver.colorFor(block, meta, face);

        int endX = startX + ppb;
        int endZ = startZ + ppb;
        if (endX > tileW) {
            endX = tileW;
        }
        if (endZ > tileH) {
            endZ = tileH;
        }

        for (int dz = startZ; dz < endZ; dz++) {
            for (int dx = startX; dx < endX; dx++) {
                int localX = dx - startX;
                int localZ = dz - startZ;
                int rgb = sampleRgb(texture, localX, localZ, ppb, fallback);
                if (WorldMapBiomeTint.needsTint(block)) {
                    rgb = WorldMapBiomeTint.applyTint(rgb, tint);
                }
                rgb = WorldMapFaceLighting.shadeRgbSmooth(rgb, face, wx, y, wz, ctx);
                int a = alpha & 0xFF;
                if (a >= 255) {
                    tile.setRGB(dx, dz, 0xFF000000 | rgb);
                } else if (a > 0) {
                    int existing = tile.getRGB(dx, dz);
                    int blended = blendArgb(existing, rgb, a);
                    tile.setRGB(dx, dz, blended);
                }
            }
        }
    }

    /**
     * Soft overlay on solid surface: render soft block top with alpha over existing solid pixels.
     */
    public static void rasterizeSoftOverlay(BufferedImage tile, int destX, int destZ, int ppb, Block softBlock,
        int softMeta, int wx, int wz, WorldMapChunkContext ctx) {
        rasterizeFace(
            tile,
            destX,
            destZ,
            ppb,
            softBlock,
            softMeta,
            wx,
            wz,
            ctx,
            WorldMapBlockColorResolver.BlockFace.TOP,
            0xB0);
    }

    /**
     * Samples a single face texel with biome tint and lighting (for ray-trace hits).
     *
     * @param texU texture U 0–15
     * @param texV texture V 0–15
     * @return 24-bit RGB or {@code -1} when fully transparent
     */
    public static int sampleFaceRgb(Block block, int meta, WorldMapBlockColorResolver.BlockFace face, int texU,
        int texV, int wx, int wy, int wz, WorldMapChunkContext ctx) {
        if (block == null || block == Blocks.air || ctx == null || face == null) {
            return -1;
        }
        BufferedImage texture = WorldMapTextureRegistry.faceTexture(block, meta, face);
        int fallback = WorldMapBlockColorResolver.colorFor(block, meta, face);
        int rgb = fallback & 0xFFFFFF;
        if (texture != null) {
            int sampled = WorldMapTextureRegistry.samplePixelRgb(texture, texU & 15, texV & 15);
            if (sampled >= 0) {
                rgb = sampled;
            }
        }
        BiomeGenBase biome = ctx.biome(wx, wz);
        if (WorldMapBiomeTint.needsTint(block)) {
            int tint = WorldMapBiomeTint.tintFor(block, meta, biome, wx, wy, wz);
            rgb = WorldMapBiomeTint.applyTint(rgb, tint);
        }
        int sky = ctx.skyLight(wx, wy, wz);
        int blockLight = ctx.blockLight(wx, wy, wz);
        return WorldMapFaceLighting.shadeRgbSmooth(rgb, face, wx, wy, wz, ctx);
    }

    private static int sampleRgb(BufferedImage texture, int localX, int localZ, int ppb, int fallback) {
        if (texture == null) {
            return fallback & 0xFFFFFF;
        }
        int u = localX * TEX_SIZE / ppb;
        int v = localZ * TEX_SIZE / ppb;
        int sampled = WorldMapTextureRegistry.samplePixelRgb(texture, u, v);
        if (sampled >= 0) {
            return sampled;
        }
        return fallback & 0xFFFFFF;
    }

    private static int blendArgb(int dstArgb, int srcRgb, int srcAlpha) {
        int dstA = (dstArgb >> 24) & 0xFF;
        int dstR = (dstArgb >> 16) & 0xFF;
        int dstG = (dstArgb >> 8) & 0xFF;
        int dstB = dstArgb & 0xFF;
        int srcR = (srcRgb >> 16) & 0xFF;
        int srcG = (srcRgb >> 8) & 0xFF;
        int srcB = srcRgb & 0xFF;
        float sa = srcAlpha / 255.0f;
        float da = dstA / 255.0f;
        float outA = sa + da * (1.0f - sa);
        if (outA <= 0.001f) {
            return 0;
        }
        int outR = (int) ((srcR * sa + dstR * da * (1.0f - sa)) / outA);
        int outG = (int) ((srcG * sa + dstG * da * (1.0f - sa)) / outA);
        int outB = (int) ((srcB * sa + dstB * da * (1.0f - sa)) / outA);
        int outAlpha = (int) (outA * 255.0f);
        if (outAlpha > 255) {
            outAlpha = 255;
        }
        return (outAlpha << 24) | (outR << 16) | (outG << 8) | outB;
    }
}
