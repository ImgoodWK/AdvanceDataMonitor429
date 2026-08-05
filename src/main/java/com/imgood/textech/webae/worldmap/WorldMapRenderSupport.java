package com.imgood.textech.webae.worldmap;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.worldmap.engine.WorldMapFlatUvRenderer;
import com.imgood.textech.webae.worldmap.engine.WorldMapIsoRayRenderer;
import com.imgood.textech.webae.worldmap.engine.WorldMapRenderEngines;

/**
 * Shared helpers for server-side world map tile renderers.
 */
public final class WorldMapRenderSupport {

    /** PNG smaller than this is treated as an empty/failed render and not cached or served. */
    public static final long MIN_VALID_TILE_BYTES = 512L;
    /** Upper bound shared by server-side tile stores and direct-capture responses. */
    public static final long MAX_VALID_TILE_BYTES = 1024L * 1024L;
    private static final int PNG_SIGNATURE_BYTES = 8;
    private static final int PNG_IHDR_HEADER_BYTES = 8;
    private static final int PNG_IHDR_DATA_BYTES = 13;
    private static final int PNG_IHDR_TOTAL_BYTES = PNG_SIGNATURE_BYTES + PNG_IHDR_HEADER_BYTES
        + PNG_IHDR_DATA_BYTES + 4;

    private WorldMapRenderSupport() {}

    public static int tilePx() {
        return tilePx(WorldMapQualityTier.MEDIUM);
    }

    public static int tilePx(WorldMapQualityTier quality) {
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        return Math.max(16, tier.serverTilePx());
    }

    public static int pxPerBlock(int tilePx) {
        return Math.max(1, tilePx / 16);
    }

    public static int pxPerBlock(WorldMapQualityTier quality) {
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        return tier.serverPxPerBlock();
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

    /**
     * Thread-safe: returns the chunk only if it is already loaded. Does NOT trigger a chunk load.
     * Intended for worker threads that must not call {@code loadChunk} concurrently.
     */
    public static Chunk chunkIfLoaded(WorldServer world, int chunkX, int chunkZ) {
        if (world == null) {
            return null;
        }
        if (world.getChunkProvider()
            .chunkExists(chunkX, chunkZ)) {
            return world.getChunkFromChunkCoords(chunkX, chunkZ);
        }
        return null;
    }

    /**
     * Pre-loads a chunk on the main thread so it is ready when a worker thread needs it.
     */
    public static void preloadChunk(int dim, int chunkX, int chunkZ) {
        WorldServer world = worldForDim(dim);
        if (world == null) {
            return;
        }
        chunkFor(world, chunkX, chunkZ);
    }

    /**
     * Pre-loads a padded region of chunks on the main thread so worker threads always
     * find them via {@link #chunkIfLoaded}. The padding must match {@code Config.webWorldMapChunkPadding}.
     * <p>
     * ⚠️ Deprecated for main-thread use — prefer {@link #preloadChunkRegionIfLoaded} to avoid
     * triggering synchronous chunk generation on the server tick.
     */
    @Deprecated
    public static void preloadChunkRegion(int dim, int centerChunkX, int centerChunkZ, int padding) {
        WorldServer world = worldForDim(dim);
        if (world == null) {
            return;
        }
        int pad = Math.max(0, Math.min(4, padding));
        for (int dz = -pad; dz <= pad; dz++) {
            for (int dx = -pad; dx <= pad; dx++) {
                chunkFor(world, centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }

    /**
     * Pre-loads chunk references for a padded region without triggering chunk loads.
     * Uses {@link #chunkIfLoaded} exclusively so worker threads can access loaded chunks
     * via the returned {@link Chunk} references. Chunks that are not already loaded are
     * silently skipped — rendering will produce empty tiles for them, and they will be
     * retried when the chunk loads naturally.
     * <p>
     * This is the recommended method for use in {@code onServerTick()} to avoid
     * synchronous chunk generation stalling the main server thread.
     */
    public static void preloadChunkRegionIfLoaded(int dim, int centerChunkX, int centerChunkZ, int padding) {
        WorldServer world = worldForDim(dim);
        if (world == null) {
            return;
        }
        int pad = Math.max(0, Math.min(4, padding));
        for (int dz = -pad; dz <= pad; dz++) {
            for (int dx = -pad; dx <= pad; dx++) {
                chunkIfLoaded(world, centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }

    public static boolean isValidTilePng(byte[] png) {
        return isValidBoundedPng(png, MIN_VALID_TILE_BYTES, MAX_VALID_TILE_BYTES, 2048);
    }

    /**
     * Validates the bounded PNG header without inflating untrusted image data.
     * The helper is also used by icon uploads, whose valid minimum is smaller
     * than the minimum accepted for rendered world-map tiles.
     */
    public static boolean isValidBoundedPng(byte[] png, long minBytes, long maxBytes, int maxDimension) {
        if (png == null || minBytes < 0L || maxBytes < minBytes || maxDimension <= 0 || png.length < minBytes
            || png.length > maxBytes) {
            return false;
        }
        return hasValidPngHeader(png, png.length, maxDimension);
    }

    /**
     * Performs the same bounded header check for a disk tile without asking
     * ImageIO to inflate untrusted image data.
     */
    public static boolean isValidTilePng(File file) {
        if (file == null || !file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return false;
        }
        long length = file.length();
        if (length < MIN_VALID_TILE_BYTES || length > MAX_VALID_TILE_BYTES) {
            return false;
        }
        byte[] header = new byte[PNG_IHDR_TOTAL_BYTES];
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            int offset = 0;
            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);
                if (read < 0) {
                    return false;
                }
                if (read == 0) {
                    continue;
                }
                offset += read;
            }
            return hasValidPngHeader(header, header.length, 2048);
        } catch (IOException e) {
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static boolean hasValidPngHeader(byte[] png, int length, int maxDimension) {
        if (png == null || length < PNG_IHDR_TOTAL_BYTES) {
            return false;
        }
        int[] signature = { 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a };
        for (int i = 0; i < signature.length; i++) {
            if ((png[i] & 0xff) != signature[i]) {
                return false;
            }
        }
        long ihdrLength = uint32(png, PNG_SIGNATURE_BYTES);
        if (ihdrLength != PNG_IHDR_DATA_BYTES || png[12] != 'I' || png[13] != 'H' || png[14] != 'D'
            || png[15] != 'R') {
            return false;
        }
        long width = uint32(png, 16);
        long height = uint32(png, 20);
        if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension) {
            return false;
        }

        int bitDepth = png[24] & 0xff;
        int colorType = png[25] & 0xff;
        int compressionMethod = png[26] & 0xff;
        int filterMethod = png[27] & 0xff;
        int interlaceMethod = png[28] & 0xff;
        if (!isValidPngColorDepth(colorType, bitDepth) || compressionMethod != 0 || filterMethod != 0
            || (interlaceMethod != 0 && interlaceMethod != 1)) {
            return false;
        }

        CRC32 crc = new CRC32();
        crc.update(png, PNG_SIGNATURE_BYTES + 4, 4 + PNG_IHDR_DATA_BYTES);
        return crc.getValue() == uint32(png, 29);
    }

    private static boolean isValidPngColorDepth(int colorType, int bitDepth) {
        switch (colorType) {
            case 0:
                return bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 2:
                return bitDepth == 8 || bitDepth == 16;
            case 3:
                return bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            case 4:
            case 6:
                return bitDepth == 8 || bitDepth == 16;
            default:
                return false;
        }
    }

    private static long uint32(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 24)
            | ((long) (bytes[offset + 1] & 0xff) << 16)
            | ((long) (bytes[offset + 2] & 0xff) << 8)
            | (long) (bytes[offset + 3] & 0xff);
    }

    /**
     * True when the chunk is loaded and has no solid surface blocks (intentionally empty terrain).
     */
    public static boolean isLoadedEmptyTerrainChunk(int dim, int chunkX, int chunkZ) {
        WorldServer world = worldForDim(dim);
        if (world == null) {
            return false;
        }
        Chunk chunk = chunkIfLoaded(world, chunkX, chunkZ);
        if (chunk == null) {
            return false;
        }
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                if (findTopSolidY(chunk, lx, lz, world) >= 0) {
                    return false;
                }
            }
        }
        return true;
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
        return block == Blocks.leaves || block == Blocks.leaves2
            || block == Blocks.tallgrass
            || block == Blocks.deadbush
            || block == Blocks.yellow_flower
            || block == Blocks.red_flower
            || block == Blocks.double_plant
            || block == Blocks.vine
            || block == Blocks.waterlily
            || block == Blocks.snow_layer
            || block == Blocks.glass
            || block == Blocks.stained_glass
            || block == Blocks.sapling
            || block == Blocks.torch
            || block == Blocks.redstone_torch
            || block == Blocks.ladder
            || block == Blocks.rail
            || block == Blocks.detector_rail
            || block == Blocks.golden_rail
            || block == Blocks.activator_rail
            || block == Blocks.carpet
            || block == Blocks.reeds
            || block == Blocks.flower_pot;
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
        return renderForView(
            view,
            WorldMapTileLayer.TERRAIN,
            WorldMapQualityTier.MEDIUM,
            dim,
            chunkX,
            chunkZ,
            null,
            -1);
    }

    public static byte[] renderForView(WorldMapView view, String layer, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId) {
        return renderForView(view, layer, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ, ownerUuid, networkId);
    }

    public static byte[] renderForView(WorldMapView view, String layer, WorldMapQualityTier quality, int dim,
        int chunkX, int chunkZ, String ownerUuid, int networkId) {
        if (view == null) {
            return null;
        }
        WorldMapQualityTier tier = quality != null ? quality : WorldMapQualityTier.MEDIUM;
        if (WorldMapTileLayer.isAe(layer)) {
            if (ownerUuid == null || ownerUuid.isEmpty() || networkId < 0) {
                return null;
            }
            return WorldMapAeOverlayRenderer.render(ownerUuid, networkId, view, tier, dim, chunkX, chunkZ);
        }
        if (view == WorldMapView.FLAT) {
            if (WorldMapRenderEngines.useUvFlat()) {
                return WorldMapFlatUvRenderer.renderTerrain(tier, dim, chunkX, chunkZ);
            }
            return WorldMapFlatRenderer.renderTerrain(tier, dim, chunkX, chunkZ);
        }
        if (view.isOblique()) {
            WorldMapObliqueDirection direction = view.obliqueDirection != null ? view.obliqueDirection
                : WorldMapObliqueDirection.SE;
            if (WorldMapRenderEngines.useRayOblique(tier)) {
                return WorldMapIsoRayRenderer.renderTerrain(tier, dim, chunkX, chunkZ, direction);
            }
            return WorldMapIsoRenderer.renderTerrain(tier, dim, chunkX, chunkZ, direction);
        }
        return null;
    }
}
