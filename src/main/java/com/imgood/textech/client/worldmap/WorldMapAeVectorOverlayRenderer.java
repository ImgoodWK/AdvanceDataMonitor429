package com.imgood.textech.client.worldmap;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapAeCategory;
import com.imgood.textech.webae.worldmap.WorldMapAePlacementRecord;
import com.imgood.textech.webae.worldmap.WorldMapAePlacementSupport;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapView;
import com.imgood.textech.webae.worldmap.engine.WorldMapChunkContext;
import com.imgood.textech.webae.worldmap.engine.WorldMapFaceRasterizer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side AE overlay renderer: category-ID PNG with texture-shaped device blocks
 * (via {@link WorldMapFaceRasterizer}) and vector cable/part lines.
 */
@SideOnly(Side.CLIENT)
public final class WorldMapAeVectorOverlayRenderer {

    private WorldMapAeVectorOverlayRenderer() {}

    public static byte[] render(
        Object worldIgnored,
        String ownerUuid,
        int networkId,
        WorldMapView view,
        int dim,
        int chunkX,
        int chunkZ) {
        if (!Config.webWorldMapAeOverlayEnabled || view == null) {
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
        if (view != WorldMapView.FLAT) {
            return null;
        }
        WorldMapQualityTier tier = WorldMapQualityTier.fromConfigAeOverlay();
        return renderFlat(dim, chunkX, chunkZ, placements, tier);
    }

    private static byte[] renderFlat(
        int dim,
        int chunkX,
        int chunkZ,
        List<WorldMapAePlacementRecord> placements,
        WorldMapQualityTier quality) {
        int tilePx = quality.isHdEligible() ? quality.hdTilePx() : WorldMapRenderSupport.tilePx(quality);
        int pxPerBlock = quality.isHdEligible() ? quality.pxPerBlock : WorldMapRenderSupport.pxPerBlock(quality);
        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int painted = 0;

        WorldMapChunkContext ctx = null;
        if (WorldMapRenderSupport.worldForDim(dim) != null) {
            ctx = WorldMapChunkContext.create(WorldMapRenderSupport.worldForDim(dim), chunkX, chunkZ);
        }

        Collections.sort(
            placements,
            new Comparator<WorldMapAePlacementRecord>() {

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

        Set<String> cableCells = new HashSet<String>();
        for (WorldMapAePlacementRecord placement : placements) {
            if (placement != null && isCableOrPart(placement)) {
                cableCells.add(cellKey(placement.x, placement.z));
            }
        }

        float cableWidthPx = (float) Math.max(1.0D, resolveCableWidthBlocks() * pxPerBlock);
        float partWidthPx = (float) Math.max(1.0D, resolvePartWidthBlocks() * pxPerBlock);

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
                paintCableConnections(g, placement, cableCells, lx, lz, pxPerBlock, categoryId,
                    "part".equals(placement.kind) ? partWidthPx : cableWidthPx);
                painted++;
                continue;
            }
            if (paintDeviceBlock(img, ctx, placement, lx, lz, pxPerBlock, categoryId)) {
                painted++;
            } else {
                paintCategoryDot(img, lx, lz, pxPerBlock, categoryId);
                painted++;
            }
        }
        g.dispose();
        if (painted <= 0) {
            return null;
        }
        return WorldMapRenderSupport.toPng(img);
    }

    private static boolean paintDeviceBlock(
        BufferedImage img,
        WorldMapChunkContext ctx,
        WorldMapAePlacementRecord placement,
        int lx,
        int lz,
        int pxPerBlock,
        int categoryId) {
        if (ctx == null || placement == null) {
            return false;
        }
        Block block = ctx.blockAt(placement.x, placement.y, placement.z);
        if (block == null || block == Blocks.air) {
            return false;
        }
        int meta = ctx.blockMeta(placement.x, placement.y, placement.z);
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
        return true;
    }

    private static void paintCableConnections(
        Graphics2D g,
        WorldMapAePlacementRecord placement,
        Set<String> cableCells,
        int lx,
        int lz,
        int pxPerBlock,
        int categoryId,
        float lineWidthPx) {
        int argb = WorldMapAeCategory.argbForCategory(categoryId, 0xFF);
        if (argb == 0) {
            return;
        }
        int cx = lx * pxPerBlock + pxPerBlock / 2;
        int cy = lz * pxPerBlock + pxPerBlock / 2;
        g.setColor(new java.awt.Color(argb, true));
        g.setStroke(new BasicStroke(lineWidthPx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] dir : dirs) {
            String neighbor = cellKey(placement.x + dir[0], placement.z + dir[1]);
            if (!cableCells.contains(neighbor)) {
                continue;
            }
            int nx = (lx + dir[0]) * pxPerBlock + pxPerBlock / 2;
            int ny = (lz + dir[1]) * pxPerBlock + pxPerBlock / 2;
            g.drawLine(cx, cy, nx, ny);
        }
        int dot = Math.max(2, (int) Math.ceil(lineWidthPx));
        g.fillOval(cx - dot / 2, cy - dot / 2, dot, dot);
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

    private static boolean isCableOrPart(WorldMapAePlacementRecord placement) {
        return "cable".equals(placement.kind) || "part".equals(placement.kind);
    }

    private static String cellKey(int x, int z) {
        return x + "," + z;
    }

    private static double resolveCableWidthBlocks() {
        double width = Config.worldMapAeCableWidthBlocks;
        if (width < 0.125D) {
            width = 0.125D;
        }
        if (width > 1.0D) {
            width = 1.0D;
        }
        return width;
    }

    private static double resolvePartWidthBlocks() {
        double width = Config.worldMapAePartWidthBlocks;
        if (width <= 0.0D) {
            return resolveCableWidthBlocks();
        }
        if (width < 0.125D) {
            width = 0.125D;
        }
        if (width > 1.0D) {
            width = 1.0D;
        }
        return width;
    }
}
