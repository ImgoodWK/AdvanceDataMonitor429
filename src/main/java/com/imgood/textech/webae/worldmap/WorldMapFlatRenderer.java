package com.imgood.textech.webae.worldmap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

/**
 * Renders a single chunk top-down (flat view) into a PNG byte array (main thread only).
 */
public final class WorldMapFlatRenderer {

    private static volatile byte[] stripePlaceholderCache;
    private static volatile byte[] transparentPlaceholderCache;

    private WorldMapFlatRenderer() {}

    /**
     * Renders chunk terrain. Returns {@code null} when the chunk is unavailable (do not cache).
     */
    public static byte[] renderTerrain(int dim, int chunkX, int chunkZ) {
        int tilePx = Math.max(16, Config.webWorldMapTilePx);
        int pxPerBlock = Math.max(1, tilePx / 16);

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        WorldServer world = WorldMapRenderSupport.worldForDim(dim);
        if (world == null) {
            return null;
        }

        Chunk chunk = WorldMapRenderSupport.chunkFor(world, chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }

        BufferedImage img = new BufferedImage(tilePx, tilePx, BufferedImage.TYPE_INT_RGB);
        int painted = 0;

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                Block block = Blocks.air;
                int meta = 0;
                int topY = WorldMapRenderSupport.findTopSolidY(chunk, lx, lz, world);
                if (topY >= 0) {
                    block = chunk.getBlock(lx, topY, lz);
                    meta = chunk.getBlockMetadata(lx, topY, lz);
                    painted++;
                }
                int rgb = WorldMapBlockColorResolver.colorFor(block, meta);
                fillBlockPixels(img, lx, lz, pxPerBlock, rgb);
            }
        }

        if (painted <= 0) {
            return null;
        }

        return toPng(img);
    }

    public static byte[] stripePlaceholder(int tilePx) {
        int size = Math.max(16, tilePx);
        byte[] cached = stripePlaceholderCache;
        if (cached != null && cached.length > 0) {
            return cached;
        }
        synchronized (WorldMapFlatRenderer.class) {
            if (stripePlaceholderCache != null && stripePlaceholderCache.length > 0) {
                return stripePlaceholderCache;
            }
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            int light = 0x666666;
            int dark = 0x444444;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    boolean stripe = ((x + y) / 8) % 2 == 0;
                    img.setRGB(x, y, stripe ? light : dark);
                }
            }
            stripePlaceholderCache = toPng(img);
            return stripePlaceholderCache;
        }
    }

    /** Single-pixel fully transparent PNG for out-of-scope chunk tiles. */
    public static byte[] transparentPlaceholder() {
        byte[] cached = transparentPlaceholderCache;
        if (cached != null && cached.length > 0) {
            return cached;
        }
        synchronized (WorldMapFlatRenderer.class) {
            if (transparentPlaceholderCache != null && transparentPlaceholderCache.length > 0) {
                return transparentPlaceholderCache;
            }
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, 0x00000000);
            transparentPlaceholderCache = toPng(img);
            return transparentPlaceholderCache;
        }
    }

    /**
     * Finds the topmost map surface, penetrating soft blocks (leaves, grass, glass, etc.).
     */
    private static int findTopSolidY(Chunk chunk, int lx, int lz, WorldServer world) {
        int maxY = world.getActualHeight() - 1;
        if (maxY > 255) {
            maxY = 255;
        }
        for (int y = maxY; y >= 0; y--) {
            Block block = chunk.getBlock(lx, y, lz);
            if (block == null || block == Blocks.air) {
                continue;
            }
            if (isSoftBlock(block)) {
                continue;
            }
            return y;
        }
        return -1;
    }

    /** Non-opaque blocks that should not define the flat-map surface. */
    private static boolean isSoftBlock(Block block) {
        return block == Blocks.leaves || block == Blocks.leaves2 || block == Blocks.tallgrass
            || block == Blocks.deadbush || block == Blocks.yellow_flower || block == Blocks.red_flower
            || block == Blocks.double_plant || block == Blocks.vine || block == Blocks.waterlily
            || block == Blocks.snow_layer || block == Blocks.glass || block == Blocks.stained_glass
            || block == Blocks.sapling || block == Blocks.torch || block == Blocks.redstone_torch
            || block == Blocks.ladder || block == Blocks.rail || block == Blocks.detector_rail
            || block == Blocks.golden_rail || block == Blocks.activator_rail || block == Blocks.carpet
            || block == Blocks.reeds || block == Blocks.flower_pot;
    }

    private static void fillBlockPixels(BufferedImage img, int blockX, int blockZ, int pxPerBlock, int rgb) {
        int startX = blockX * pxPerBlock;
        int startZ = blockZ * pxPerBlock;
        for (int dz = 0; dz < pxPerBlock; dz++) {
            for (int dx = 0; dx < pxPerBlock; dx++) {
                img.setRGB(startX + dx, startZ + dz, rgb);
            }
        }
    }

    private static byte[] toPng(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] World map PNG encode failed", e);
            return new byte[0];
        }
    }
}
