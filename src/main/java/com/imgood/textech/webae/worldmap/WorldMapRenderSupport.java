package com.imgood.textech.webae.worldmap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.DimensionManager;
import net.minecraft.world.chunk.Chunk;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

/**
 * Shared helpers for server-side world map tile renderers.
 */
public final class WorldMapRenderSupport {

    /** PNG smaller than this is treated as an empty/failed render and not cached or served. */
    public static final long MIN_VALID_TILE_BYTES = 512L;

    private WorldMapRenderSupport() {}

    public static int tilePx() {
        return Math.max(16, Config.webWorldMapTilePx);
    }

    public static int pxPerBlock(int tilePx) {
        return Math.max(1, tilePx / 16);
    }

    public static WorldServer worldForDim(int dim) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        if (dim >= 0 && dim < server.worldServers.length) {
            WorldServer indexed = server.worldServers[dim];
            if (indexed != null) {
                return indexed;
            }
        }
        World world = DimensionManager.getWorld(dim);
        return world instanceof WorldServer ? (WorldServer) world : null;
    }

    public static Chunk chunkFor(WorldServer world, int chunkX, int chunkZ) {
        if (world == null) {
            return null;
        }
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        if (!world.blockExists(blockX, 64, blockZ)) {
            try {
                world.getChunkProvider()
                    .loadChunk(chunkX, chunkZ);
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug(
                    "[WebAE] World map chunk load failed dim={} cx={} cz={}: {}",
                    world.provider.dimensionId,
                    chunkX,
                    chunkZ,
                    t.getMessage());
            }
        }
        return world.getChunkFromChunkCoords(chunkX, chunkZ);
    }

    public static boolean isValidTilePng(byte[] png) {
        return png != null && png.length >= MIN_VALID_TILE_BYTES;
    }

    /**
     * Finds the topmost map surface, penetrating soft blocks (leaves, grass, glass, etc.).
     */
    public static int findTopSolidY(Chunk chunk, int lx, int lz, WorldServer world) {
        if (chunk == null || world == null) {
            return -1;
        }
        int wx = (chunk.xPosition << 4) + lx;
        int wz = (chunk.zPosition << 4) + lz;
        int mappedY = chunk.getHeightValue(lx, lz);
        int maxY = world.getActualHeight() - 1;
        if (maxY > 255) {
            maxY = 255;
        }
        int startY = mappedY > 0 ? mappedY : maxY;
        if (startY > maxY) {
            startY = maxY;
        }
        for (int y = startY; y >= 0; y--) {
            Block block = world.getBlock(wx, y, wz);
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

    public static boolean isSoftBlock(Block block) {
        return block == Blocks.leaves || block == Blocks.leaves2 || block == Blocks.tallgrass
            || block == Blocks.deadbush || block == Blocks.yellow_flower || block == Blocks.red_flower
            || block == Blocks.double_plant || block == Blocks.vine || block == Blocks.waterlily
            || block == Blocks.snow_layer || block == Blocks.glass || block == Blocks.stained_glass
            || block == Blocks.sapling || block == Blocks.torch || block == Blocks.redstone_torch
            || block == Blocks.ladder || block == Blocks.rail || block == Blocks.detector_rail
            || block == Blocks.golden_rail || block == Blocks.activator_rail || block == Blocks.carpet
            || block == Blocks.reeds || block == Blocks.flower_pot;
    }

    public static boolean isMapSolid(Block block) {
        return block != null && block != Blocks.air && !isSoftBlock(block);
    }

    public static void fillRect(BufferedImage img, int x0, int y0, int w, int h, int rgb) {
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        int x1 = x0 + w;
        int y1 = y0 + h;
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x1 > imgW) {
            x1 = imgW;
        }
        if (y1 > imgH) {
            y1 = imgH;
        }
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                img.setRGB(x, y, rgb);
            }
        }
    }

    public static void fillBlockPixels(BufferedImage img, int blockX, int blockZ, int pxPerBlock, int rgb) {
        fillRect(img, blockX * pxPerBlock, blockZ * pxPerBlock, pxPerBlock, pxPerBlock, rgb);
    }

    public static byte[] toPng(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] World map PNG encode failed", e);
            return new byte[0];
        }
    }

    public static byte[] renderForView(WorldMapView view, int dim, int chunkX, int chunkZ) {
        return renderForView(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, null, -1);
    }

    public static byte[] renderForView(WorldMapView view, String layer, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId) {
        if (view == null) {
            return null;
        }
        if (WorldMapTileLayer.isAe(layer)) {
            if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
                return null;
            }
            return WorldMapAeOverlayRenderer.render(ownerUuid, networkId, view, dim, chunkX, chunkZ);
        }
        if (view == WorldMapView.FLAT) {
            return WorldMapFlatRenderer.renderTerrain(dim, chunkX, chunkZ);
        }
        if (view.isOblique()) {
            return WorldMapIsoRenderer.renderTerrain(dim, chunkX, chunkZ, view.obliqueDirection);
        }
        return null;
    }
}
