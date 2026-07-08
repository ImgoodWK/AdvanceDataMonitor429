package com.imgood.textech.webae.worldmap.engine;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.imgood.textech.Config;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;

/**
 * Multi-chunk snapshot centered on a target chunk for cross-boundary world-coordinate lookups.
 */
public final class WorldMapChunkContext {

    private final WorldServer world;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int padding;
    private final Chunk[][] chunks;

    private WorldMapChunkContext(WorldServer world, int centerChunkX, int centerChunkZ, int padding,
        Chunk[][] chunks) {
        this.world = world;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.padding = padding;
        this.chunks = chunks;
    }

    public static WorldMapChunkContext create(WorldServer world, int chunkX, int chunkZ) {
        return create(world, chunkX, chunkZ, Config.webWorldMapChunkPadding);
    }

    public static WorldMapChunkContext create(WorldServer world, int chunkX, int chunkZ, int padding) {
        if (world == null) {
            return null;
        }
        int pad = Math.max(0, Math.min(4, padding));
        int span = pad * 2 + 1;
        Chunk[][] grid = new Chunk[span][span];
        for (int dz = -pad; dz <= pad; dz++) {
            for (int dx = -pad; dx <= pad; dx++) {
                int cx = chunkX + dx;
                int cz = chunkZ + dz;
                grid[dx + pad][dz + pad] = WorldMapRenderSupport.chunkFor(world, cx, cz);
            }
        }
        Chunk center = grid[pad][pad];
        if (center == null) {
            return null;
        }
        return new WorldMapChunkContext(world, chunkX, chunkZ, pad, grid);
    }

    public WorldServer world() {
        return world;
    }

    public int centerChunkX() {
        return centerChunkX;
    }

    public int centerChunkZ() {
        return centerChunkZ;
    }

    public int padding() {
        return padding;
    }

    public Block blockAt(int wx, int y, int wz) {
        Chunk chunk = chunkAtWorld(wx, wz);
        if (chunk == null || y < 0) {
            return Blocks.air;
        }
        int maxY = world.getActualHeight() - 1;
        if (maxY > 255) {
            maxY = 255;
        }
        if (y > maxY) {
            return Blocks.air;
        }
        int lx = wx & 15;
        int lz = wz & 15;
        Block block = chunk.getBlock(lx, y, lz);
        return block != null ? block : Blocks.air;
    }

    public int blockMeta(int wx, int y, int wz) {
        Chunk chunk = chunkAtWorld(wx, wz);
        if (chunk == null || y < 0) {
            return 0;
        }
        int lx = wx & 15;
        int lz = wz & 15;
        return chunk.getBlockMetadata(lx, y, lz);
    }

    public net.minecraft.tileentity.TileEntity tileEntityAt(int wx, int y, int wz) {
        if (world == null || y < 0) {
            return null;
        }
        Chunk chunk = chunkAtWorld(wx, wz);
        if (chunk == null) {
            return null;
        }
        return world.getTileEntity(wx, y, wz);
    }

    public int skyLight(int wx, int y, int wz) {
        if (world == null || y < 0) {
            return 15;
        }
        int combined = world.getLightBrightnessForSkyBlocks(wx, y, wz, 0);
        if (combined < 0) {
            return 0;
        }
        if (combined > 15) {
            return 15;
        }
        return combined;
    }

    public int blockLight(int wx, int y, int wz) {
        if (world == null || y < 0) {
            return 0;
        }
        return world.getBlockLightValue(wx, y, wz);
    }

    public BiomeGenBase biome(int wx, int wz) {
        return world.getBiomeGenForCoords(wx, wz);
    }

    /**
     * Topmost non-air block Y (includes soft blocks).
     */
    public int findTopBlockY(int wx, int wz) {
        int maxY = world.getActualHeight() - 1;
        if (maxY > 255) {
            maxY = 255;
        }
        Chunk chunk = chunkAtWorld(wx, wz);
        if (chunk == null) {
            return -1;
        }
        int lx = wx & 15;
        int lz = wz & 15;
        int mappedY = chunk.getHeightValue(lx, lz);
        int startY = mappedY > 0 ? mappedY : maxY;
        if (startY > maxY) {
            startY = maxY;
        }
        for (int y = startY; y >= 0; y--) {
            Block block = blockAt(wx, y, wz);
            if (block != null && block != Blocks.air) {
                return y;
            }
        }
        return -1;
    }

    /**
     * Topmost solid map surface, penetrating soft blocks (leaves, grass, glass, etc.).
     */
    public int findTopSolidY(int wx, int wz) {
        int maxY = world.getActualHeight() - 1;
        if (maxY > 255) {
            maxY = 255;
        }
        Chunk chunk = chunkAtWorld(wx, wz);
        if (chunk == null) {
            return -1;
        }
        int lx = wx & 15;
        int lz = wz & 15;
        int mappedY = chunk.getHeightValue(lx, lz);
        int startY = mappedY > 0 ? mappedY : maxY;
        if (startY > maxY) {
            startY = maxY;
        }
        for (int y = startY; y >= 0; y--) {
            Block block = blockAt(wx, y, wz);
            if (block == null || block == Blocks.air) {
                continue;
            }
            if (WorldMapRenderSupport.isSoftBlock(block)) {
                continue;
            }
            return y;
        }
        return -1;
    }

    /**
     * Soft block directly above the solid surface, if any.
     */
    public int findSoftOverlayY(int wx, int wz) {
        int solidY = findTopSolidY(wx, wz);
        int topY = findTopBlockY(wx, wz);
        if (topY < 0) {
            return -1;
        }
        if (solidY < 0) {
            Block top = blockAt(wx, topY, wz);
            if (top != null && WorldMapRenderSupport.isSoftBlock(top)) {
                return topY;
            }
            return -1;
        }
        if (topY <= solidY) {
            return -1;
        }
        Block top = blockAt(wx, topY, wz);
        if (top != null && WorldMapRenderSupport.isSoftBlock(top)) {
            return topY;
        }
        return -1;
    }

    private Chunk chunkAtWorld(int wx, int wz) {
        int cx = wx >> 4;
        int cz = wz >> 4;
        int dx = cx - centerChunkX;
        int dz = cz - centerChunkZ;
        if (dx < -padding || dx > padding || dz < -padding || dz > padding) {
            return null;
        }
        return chunks[dx + padding][dz + padding];
    }
}
