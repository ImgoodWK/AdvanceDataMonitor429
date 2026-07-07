package com.imgood.textech.webae.worldmap;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * Lightweight isometric (SE) chunk renderer using column painter's algorithm.
 */
public final class WorldMapIsoRenderer {

    private WorldMapIsoRenderer() {}

    public static byte[] renderTerrain(int dim, int chunkX, int chunkZ) {
        return renderTerrain(dim, chunkX, chunkZ, WorldMapObliqueDirection.SE);
    }

    public static byte[] renderTerrain(int dim, int chunkX, int chunkZ, WorldMapObliqueDirection direction) {
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

        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_RGB);
        int centerX = tilePx / 2;
        int centerY = tilePx / 4;

        int[] mapped = new int[2];
        List<Column> columns = new ArrayList<Column>();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                direction.mapLocal(lx, lz, mapped);
                int sampleX = mapped[0];
                int sampleZ = mapped[1];
                int topY = WorldMapRenderSupport.findTopSolidY(chunk, sampleX, sampleZ, world);
                if (topY >= 0) {
                    columns.add(new Column(lx, lz, sampleX, sampleZ, topY));
                }
            }
        }

        Collections.sort(
            columns,
            new Comparator<Column>() {

                @Override
                public int compare(Column a, Column b) {
                    int sumA = a.lx + a.lz;
                    int sumB = b.lx + b.lz;
                    if (sumA != sumB) {
                        return sumB - sumA;
                    }
                    if (a.lz != b.lz) {
                        return b.lz - a.lz;
                    }
                    return b.lx - a.lx;
                }
            });

        int minTopY = Integer.MAX_VALUE;
        for (Column col : columns) {
            if (col.topY < minTopY) {
                minTopY = col.topY;
            }
        }
        if (minTopY == Integer.MAX_VALUE) {
            minTopY = 0;
        }

        if (columns.isEmpty()) {
            return null;
        }

        for (Column col : columns) {
            drawColumn(img, chunk, world, col.sampleX, col.sampleZ, col.lx, col.lz, col.topY, minTopY, centerX,
                centerY, pxPerBlock);
        }

        return WorldMapRenderSupport.toPng(img);
    }

    private static void drawColumn(BufferedImage img, Chunk chunk, WorldServer world, int sampleX, int sampleZ, int lx,
        int lz, int topY, int minTopY, int centerX, int centerY, int pxPerBlock) {
        Block block = chunk.getBlock(sampleX, topY, sampleZ);
        int meta = chunk.getBlockMetadata(sampleX, topY, sampleZ);
        if (!WorldMapRenderSupport.isMapSolid(block)) {
            return;
        }

        int southNeighborY = neighborTopY(chunk, world, sampleX, sampleZ + 1);
        int eastNeighborY = neighborTopY(chunk, world, sampleX + 1, sampleZ);

        int wallSouth = southNeighborY >= 0 ? topY - southNeighborY : 0;
        int wallEast = eastNeighborY >= 0 ? topY - eastNeighborY : 0;
        if (wallSouth < 0) {
            wallSouth = 0;
        }
        if (wallEast < 0) {
            wallEast = 0;
        }

        int topColor = WorldMapBlockColorResolver.colorFor(block, meta, WorldMapBlockColorResolver.BlockFace.TOP);
        int southColor = WorldMapBlockColorResolver.colorFor(block, meta, WorldMapBlockColorResolver.BlockFace.SOUTH);
        int eastColor = WorldMapBlockColorResolver.colorFor(block, meta, WorldMapBlockColorResolver.BlockFace.EAST);

        int half = pxPerBlock / 2;
        int quarter = Math.max(1, pxPerBlock / 4);
        int cx = centerX + (lx - lz) * half;
        // Use chunk-local elevation so underground AE bases are not pushed off-canvas by absolute Y.
        int relY = topY - minTopY;
        int cy = centerY + (lx + lz) * quarter - relY * half;

        if (wallSouth > 0) {
            drawSouthWall(img, cx, cy, half, quarter, wallSouth, southColor);
        }
        if (wallEast > 0) {
            drawEastWall(img, cx, cy, half, quarter, wallEast, eastColor);
        }
        drawTopDiamond(img, cx, cy, half, quarter, topColor);
    }

    private static int neighborTopY(Chunk chunk, WorldServer world, int lx, int lz) {
        if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
            return -1;
        }
        return WorldMapRenderSupport.findTopSolidY(chunk, lx, lz, world);
    }

    private static void drawTopDiamond(BufferedImage img, int cx, int cy, int half, int quarter, int rgb) {
        for (int dy = -quarter; dy <= quarter; dy++) {
            int span = half - Math.abs(dy) * half / Math.max(1, quarter);
            if (span <= 0) {
                continue;
            }
            for (int dx = -span; dx <= span; dx++) {
                setPixel(img, cx + dx, cy + dy, rgb);
            }
        }
    }

    private static void drawSouthWall(BufferedImage img, int cx, int cy, int half, int quarter, int heightBlocks,
        int rgb) {
        int wallPx = heightBlocks * half;
        for (int row = 0; row < wallPx; row++) {
            int y = cy + quarter + row;
            int offset = row * half / Math.max(1, wallPx);
            for (int dx = -half + offset; dx <= half - offset; dx++) {
                setPixel(img, cx + dx, y, rgb);
            }
        }
    }

    private static void drawEastWall(BufferedImage img, int cx, int cy, int half, int quarter, int heightBlocks,
        int rgb) {
        int wallPx = heightBlocks * half;
        for (int row = 0; row < wallPx; row++) {
            int y = cy + quarter + row;
            int offset = row * half / Math.max(1, wallPx);
            for (int dx = offset; dx <= half; dx++) {
                setPixel(img, cx + dx, y, rgb);
            }
        }
    }

    private static void setPixel(BufferedImage img, int x, int y, int rgb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return;
        }
        img.setRGB(x, y, rgb);
    }

    private static final class Column {

        final int lx;
        final int lz;
        final int sampleX;
        final int sampleZ;
        final int topY;

        Column(int lx, int lz, int sampleX, int sampleZ, int topY) {
            this.lx = lx;
            this.lz = lz;
            this.sampleX = sampleX;
            this.sampleZ = sampleZ;
            this.topY = topY;
        }
    }
}
