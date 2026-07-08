package com.imgood.textech.webae.worldmap.engine;

import java.awt.image.BufferedImage;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.imgood.textech.webae.worldmap.WorldMapFlatRenderer;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;

/**
 * Flat top-down chunk renderer using per-pixel texture UV, biome tint and smooth lighting.
 */
public final class WorldMapFlatUvRenderer {

    private WorldMapFlatUvRenderer() {}

    public static byte[] renderTerrain(WorldMapQualityTier quality, int dim, int chunkX, int chunkZ) {
        int tilePx = WorldMapRenderSupport.tilePx(quality);
        int pxPerBlock = WorldMapRenderSupport.pxPerBlock(quality);

        WorldServer world = WorldMapRenderSupport.worldForDim(dim);
        if (world == null) {
            return null;
        }

        WorldMapChunkContext ctx = WorldMapChunkContext.create(world, chunkX, chunkZ);
        if (ctx == null) {
            return null;
        }

        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_RGB);
        int painted = 0;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int solidY = ctx.findTopSolidY(wx, wz);
                if (solidY < 0) {
                    continue;
                }
                Block solid = ctx.blockAt(wx, solidY, wz);
                int solidMeta = ctx.blockMeta(wx, solidY, wz);
                if (solid == null || solid == Blocks.air) {
                    continue;
                }
                WorldMapFaceRasterizer.rasterizeTopFace(img, lx, lz, pxPerBlock, solid, solidMeta, wx, wz, ctx);
                painted++;

                int softY = ctx.findSoftOverlayY(wx, wz);
                if (softY >= 0) {
                    Block soft = ctx.blockAt(wx, softY, wz);
                    int softMeta = ctx.blockMeta(wx, softY, wz);
                    if (soft != null && soft != Blocks.air) {
                        WorldMapFaceRasterizer.rasterizeSoftOverlay(img, lx, lz, pxPerBlock, soft, softMeta, wx, wz,
                            ctx);
                    }
                }
            }
        }

        if (painted <= 0) {
            return null;
        }
        return WorldMapRenderSupport.toPng(img);
    }

    public static byte[] stripePlaceholder(int tilePx) {
        return WorldMapFlatRenderer.stripePlaceholder(tilePx);
    }

    public static byte[] transparentPlaceholder() {
        return WorldMapFlatRenderer.transparentPlaceholder();
    }
}
