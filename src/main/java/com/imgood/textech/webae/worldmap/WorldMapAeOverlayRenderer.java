package com.imgood.textech.webae.worldmap;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.imgood.textech.Config;
import com.imgood.textech.compat.WorldMapBlockCompat;

/**
 * Server-side transparent PNG overlay for AE placements within one chunk.
 */
public final class WorldMapAeOverlayRenderer {

    private WorldMapAeOverlayRenderer() {}

    public static byte[] render(String ownerUuid, int networkId, WorldMapView view, int dim, int chunkX, int chunkZ) {
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
            return renderFlat(dim, chunkX, chunkZ, placements);
        }
        if (view.isOblique()) {
            return renderOblique(dim, chunkX, chunkZ, placements, view.obliqueDirection);
        }
        return null;
    }

    private static byte[] renderFlat(int dim, int chunkX, int chunkZ, List<WorldMapAePlacementRecord> placements) {
        int tilePx = WorldMapRenderSupport.tilePx();
        int pxPerBlock = WorldMapRenderSupport.pxPerBlock(tilePx);
        WorldServer world = WorldMapRenderSupport.worldForDim(dim);
        if (world == null) {
            return null;
        }
        Chunk chunk = WorldMapRenderSupport.chunkFor(world, chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }

        Map<Long, WorldMapAePlacementRecord> topByColumn = new HashMap<Long, WorldMapAePlacementRecord>();
        for (WorldMapAePlacementRecord placement : placements) {
            if (placement == null) {
                continue;
            }
            long key = columnKey(placement.x, placement.z);
            WorldMapAePlacementRecord existing = topByColumn.get(key);
            if (existing == null || placement.y > existing.y) {
                topByColumn.put(key, placement);
            }
        }
        if (topByColumn.isEmpty()) {
            return null;
        }

        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_ARGB);
        int painted = 0;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (WorldMapAePlacementRecord placement : topByColumn.values()) {
            int lx = placement.x - baseX;
            int lz = placement.z - baseZ;
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
                continue;
            }
            Block block = world.getBlock(placement.x, placement.y, placement.z);
            int meta = world.getBlockMetadata(placement.x, placement.y, placement.z);
            if (block == null || block == Blocks.air) {
                block = chunk.getBlock(lx, placement.y, lz);
                meta = chunk.getBlockMetadata(lx, placement.y, lz);
            }
            int rgb = WorldMapBlockCompat.colorForPlacement(placement.iconItemId, block, meta);
            paintBlockPixels(img, lx, lz, pxPerBlock, 0xFF000000 | (rgb & 0xFFFFFF));
            painted++;
        }
        if (painted <= 0) {
            return null;
        }
        return WorldMapRenderSupport.toPng(img);
    }

    private static byte[] renderOblique(int dim, int chunkX, int chunkZ, List<WorldMapAePlacementRecord> placements,
        WorldMapObliqueDirection direction) {
        if (direction == null) {
            direction = WorldMapObliqueDirection.SE;
        }
        int tilePx = WorldMapRenderSupport.tilePx();
        int pxPerBlock = WorldMapRenderSupport.pxPerBlock(tilePx);
        WorldServer world = WorldMapRenderSupport.worldForDim(dim);
        if (world == null) {
            return null;
        }
        Chunk chunk = WorldMapRenderSupport.chunkFor(world, chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }

        int centerX = tilePx / 2;
        int centerY = tilePx / 4;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        List<DrawColumn> columns = new ArrayList<DrawColumn>();
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

        Collections.sort(
            columns,
            new Comparator<DrawColumn>() {

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
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        int painted = 0;
        for (DrawColumn col : columns) {
            Block block = world.getBlock(col.placement.x, col.y, col.placement.z);
            int meta = world.getBlockMetadata(col.placement.x, col.y, col.placement.z);
            if (block == null || block == Blocks.air) {
                block = chunk.getBlock(col.sampleX, col.y, col.sampleZ);
                meta = chunk.getBlockMetadata(col.sampleX, col.y, col.sampleZ);
            }
            int rgb = WorldMapBlockCompat.colorForPlacement(col.placement.iconItemId, block, meta);
            int topColor = 0xFF000000 | (rgb & 0xFFFFFF);
            int sideColor = darken(rgb, 0.72f);
            int px = centerX + (col.lx - col.lz) * pxPerBlock / 2;
            int py = centerY + (col.lx + col.lz) * pxPerBlock / 4 - (col.y - minY) * pxPerBlock / 2;
            g.setColor(new java.awt.Color(topColor, true));
            g.fillRect(px, py, pxPerBlock, pxPerBlock);
            g.setColor(new java.awt.Color(0xFF000000 | sideColor, true));
            g.fillRect(px, py + pxPerBlock, pxPerBlock, Math.max(1, pxPerBlock / 3));
            painted++;
        }
        g.dispose();
        if (painted <= 0) {
            return null;
        }
        return WorldMapRenderSupport.toPng(img);
    }

    private static void paintBlockPixels(BufferedImage img, int lx, int lz, int pxPerBlock, int argb) {
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

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    private static int darken(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r * factor);
        g = (int) (g * factor);
        b = (int) (b * factor);
        return (r << 16) | (g << 8) | b;
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
