package com.imgood.textech.webae.worldmap;

import java.awt.image.BufferedImage;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.engine.WorldMapAeObliqueRayRenderer;
import com.imgood.textech.webae.worldmap.engine.WorldMapChunkContext;
import com.imgood.textech.webae.worldmap.engine.WorldMapFaceRasterizer;
import com.imgood.textech.webae.worldmap.engine.WorldMapRenderEngines;

/**
 * Server-side transparent PNG overlay for AE placements within one chunk.
 * Pixels encode {@link WorldMapAeCategory} id in the R channel for client-side tinting.
 */
public final class WorldMapAeOverlayRenderer {

    private WorldMapAeOverlayRenderer() {}

    public static byte[] render(String ownerUuid, int networkId, WorldMapView view, int dim, int chunkX, int chunkZ) {
        return render(ownerUuid, networkId, view, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ);
    }

    public static byte[] render(String ownerUuid, int networkId, WorldMapView view, WorldMapQualityTier quality,
        int dim, int chunkX, int chunkZ) {
        if (!Config.webWorldMapAeOverlayEnabled) {
            return null;
        }
        List<WorldMapAePlacementRecord> placements = WorldMapAePlacementSupport.filterChunk(
            WorldMapAePlacementSupport.loadForNetwork(ownerUuid, networkId),
            dim,
            chunkX,
            chunkZ);
        if (placements.isEmpty()) {
            return null;
        }
        if (view == null) {
            return null;
        }
        if (view == WorldMapView.FLAT) {
            return renderFlat(quality, dim, chunkX, chunkZ, placements);
        }
        if (view.isOblique()) {
            return renderOblique(quality, dim, chunkX, chunkZ, placements, view.obliqueDirection);
        }
        return null;
    }

    private static byte[] renderFlat(WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        List<WorldMapAePlacementRecord> placements) {
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

        java.util.Collections.sort(
            placements,
            new java.util.Comparator<WorldMapAePlacementRecord>() {

                @Override
                public int compare(WorldMapAePlacementRecord a, WorldMapAePlacementRecord b) {
                    if (a == null && b == null) {
                        return 0;
                    }
                    if (a == null) {
                        return -1;
                    }
                    if (b == null) {
                        return 1;
                    }
                    if (a.y != b.y) {
                        return a.y - b.y;
                    }
                    if (a.z != b.z) {
                        return a.z - b.z;
                    }
                    return a.x - b.x;
                }
            });

        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_ARGB);
        int painted = 0;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (WorldMapAePlacementRecord placement : placements) {
            if (placement == null) {
                continue;
            }
            int lx = placement.x - baseX;
            int lz = placement.z - baseZ;
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
                continue;
            }
            int categoryId = WorldMapAeCategory.resolve(placement).id;
            if (isCableOrPart(placement)) {
                paintCategoryDot(img, lx, lz, pxPerBlock, categoryId);
                painted++;
                continue;
            }
            Block block = ctx.blockAt(placement.x, placement.y, placement.z);
            int meta = ctx.blockMeta(placement.x, placement.y, placement.z);
            if (block == null || block == Blocks.air) {
                paintCategoryDot(img, lx, lz, pxPerBlock, categoryId);
            } else {
                WorldMapFaceRasterizer.rasterizeTopFaceCategoryId(
                    img,
                    lx,
                    lz,
                    pxPerBlock,
                    block,
                    meta,
                    placement.x,
                    placement.z,
                    ctx,
                    categoryId);
            }
            painted++;
        }
        if (painted <= 0) {
            return null;
        }
        return WorldMapRenderSupport.toPng(img);
    }

    private static boolean isCableOrPart(WorldMapAePlacementRecord placement) {
        return "cable".equals(placement.kind) || "part".equals(placement.kind);
    }

    private static void paintCategoryDot(BufferedImage img, int lx, int lz, int pxPerBlock, int categoryId) {
        int argb = WorldMapAeCategory.argbForCategory(categoryId, 0xFF);
        if (argb == 0) {
            return;
        }
        int cx = lx * pxPerBlock + pxPerBlock / 2;
        int cy = lz * pxPerBlock + pxPerBlock / 2;
        int radius = Math.max(2, pxPerBlock / 8);
        int x0 = cx - radius;
        int y0 = cy - radius;
        int x1 = cx + radius;
        int y1 = cy + radius;
        int w = img.getWidth();
        int h = img.getHeight();
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x1 > w) {
            x1 = w;
        }
        if (y1 > h) {
            y1 = h;
        }
        int radiusSq = radius * radius;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radiusSq) {
                    img.setRGB(x, y, argb);
                }
            }
        }
    }

    private static byte[] renderOblique(WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        List<WorldMapAePlacementRecord> placements, WorldMapObliqueDirection direction) {
        if (direction == null) {
            direction = WorldMapObliqueDirection.SE;
        }
        if (WorldMapRenderEngines.useRayOblique(quality)) {
            return WorldMapAeObliqueRayRenderer.render(quality, dim, chunkX, chunkZ, direction, placements);
        }
        return renderObliqueLegacy(quality, dim, chunkX, chunkZ, placements, direction);
    }

    /** Legacy column painter with category ID colors (fallback when ray engine disabled). */
    private static byte[] renderObliqueLegacy(WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        List<WorldMapAePlacementRecord> placements, WorldMapObliqueDirection direction) {
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

        int centerX = tilePx / 2;
        int centerY = tilePx / 4;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        List<DrawColumn> columns = new java.util.ArrayList<DrawColumn>();
        for (WorldMapAePlacementRecord placement : placements) {
            if (placement == null || "part".equals(placement.kind)) {
                continue;
            }
            int lx = placement.x - baseX;
            int lz = placement.z - baseZ;
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
                continue;
            }
            int[] mapped = new int[2];
            direction.mapLocal(lx, lz, mapped);
            columns.add(new DrawColumn(lx, lz, mapped[0], mapped[1], placement.y, placement));
        }
        if (columns.isEmpty()) {
            return null;
        }

        java.util.Collections.sort(
            columns,
            new java.util.Comparator<DrawColumn>() {

                @Override
                public int compare(DrawColumn a, DrawColumn b) {
                    int sumA = a.lx + a.lz;
                    int sumB = b.lx + b.lz;
                    if (sumA != sumB) {
                        return sumB - sumA;
                    }
                    if (a.lz != b.lz) {
                        return b.lz - a.lz;
                    }
                    if (a.lx != b.lx) {
                        return b.lx - a.lx;
                    }
                    return a.y - b.y;
                }
            });

        int minY = Integer.MAX_VALUE;
        for (DrawColumn col : columns) {
            if (col.y < minY) {
                minY = col.y;
            }
        }
        if (minY == Integer.MAX_VALUE) {
            minY = 0;
        }

        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_ARGB);
        int painted = 0;
        for (DrawColumn col : columns) {
            int categoryId = WorldMapAeCategory.resolve(col.placement).id;
            int px = centerX + (col.lx - col.lz) * pxPerBlock / 2;
            int py = centerY + (col.lx + col.lz) * pxPerBlock / 4 - (col.y - minY) * pxPerBlock / 2;
            paintCategoryRect(img, px, py, pxPerBlock, categoryId);
            paintCategoryRect(img, px, py + pxPerBlock, pxPerBlock, Math.max(1, pxPerBlock / 3), categoryId);
            painted++;
        }
        if (painted <= 0) {
            return null;
        }
        return WorldMapRenderSupport.toPng(img);
    }

    private static void paintCategoryBlockPixels(BufferedImage img, int lx, int lz, int pxPerBlock, int categoryId) {
        int argb = WorldMapAeCategory.argbForCategory(categoryId, 0xFF);
        if (argb == 0) {
            return;
        }
        int x0 = lx * pxPerBlock;
        int y0 = lz * pxPerBlock;
        int x1 = x0 + pxPerBlock;
        int y1 = y0 + pxPerBlock;
        int w = img.getWidth();
        int h = img.getHeight();
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x1 > w) {
            x1 = w;
        }
        if (y1 > h) {
            y1 = h;
        }
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                img.setRGB(x, y, argb);
            }
        }
    }

    private static void paintCategoryRect(BufferedImage img, int px, int py, int size, int categoryId) {
        paintCategoryRect(img, px, py, size, size, categoryId);
    }

    private static void paintCategoryRect(BufferedImage img, int px, int py, int w, int h, int categoryId) {
        int argb = WorldMapAeCategory.argbForCategory(categoryId, 0xFF);
        if (argb == 0) {
            return;
        }
        int x0 = px;
        int y0 = py;
        int x1 = px + w;
        int y1 = py + h;
        int iw = img.getWidth();
        int ih = img.getHeight();
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x1 > iw) {
            x1 = iw;
        }
        if (y1 > ih) {
            y1 = ih;
        }
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                img.setRGB(x, y, argb);
            }
        }
    }

    private static final class DrawColumn {

        final int lx;
        final int lz;
        final int sampleX;
        final int sampleZ;
        final int y;
        final WorldMapAePlacementRecord placement;

        DrawColumn(int lx, int lz, int sampleX, int sampleZ, int y, WorldMapAePlacementRecord placement) {
            this.lx = lx;
            this.lz = lz;
            this.sampleX = sampleX;
            this.sampleZ = sampleZ;
            this.y = y;
            this.placement = placement;
        }
    }
}
