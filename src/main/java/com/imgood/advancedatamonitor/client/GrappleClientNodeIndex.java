package com.imgood.advancedatamonitor.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.imgood.advancedatamonitor.loader.LoaderBlock;
import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side chunk-indexed registry of grapple anchor blocks.
 * Updated on chunk load/unload — avoids cubic world scans when selecting nodes.
 */
@SideOnly(Side.CLIENT)
public final class GrappleClientNodeIndex {

    public static final GrappleClientNodeIndex INSTANCE = new GrappleClientNodeIndex();

    private final Map<Integer, Map<ChunkCoordIntPair, List<BlockPos>>> byDimension = new HashMap<Integer, Map<ChunkCoordIntPair, List<BlockPos>>>();

    private GrappleClientNodeIndex() {}

    public void addNode(int dimId, int x, int y, int z) {
        Map<ChunkCoordIntPair, List<BlockPos>> dimMap = getDimMap(dimId);
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(x >> 4, z >> 4);
        List<BlockPos> list = dimMap.get(chunk);
        if (list == null) {
            list = new ArrayList<BlockPos>();
            dimMap.put(chunk, list);
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (!list.contains(pos)) {
            list.add(pos);
        }
    }

    public void removeNode(int dimId, int x, int y, int z) {
        Map<ChunkCoordIntPair, List<BlockPos>> dimMap = byDimension.get(dimId);
        if (dimMap == null) {
            return;
        }
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(x >> 4, z >> 4);
        List<BlockPos> list = dimMap.get(chunk);
        if (list == null) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        Iterator<BlockPos> it = list.iterator();
        while (it.hasNext()) {
            if (it.next()
                .equals(pos)) {
                it.remove();
                break;
            }
        }
        if (list.isEmpty()) {
            dimMap.remove(chunk);
        }
    }

    public void clearChunk(int dimId, int chunkX, int chunkZ) {
        Map<ChunkCoordIntPair, List<BlockPos>> dimMap = byDimension.get(dimId);
        if (dimMap != null) {
            dimMap.remove(new ChunkCoordIntPair(chunkX, chunkZ));
        }
    }

    /** Rebuild one chunk by scanning blocks (matches server {@code HandlerGrapple} indexing). */
    public void rebuildChunkFromBlocks(World world, Chunk chunk, int dimId) {
        if (world == null || chunk == null) {
            return;
        }
        clearChunk(dimId, chunk.xPosition, chunk.zPosition);
        int baseX = chunk.xPosition * 16;
        int baseZ = chunk.zPosition * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    int wx = baseX + x;
                    int wz = baseZ + z;
                    if (world.getBlock(wx, y, wz) == LoaderBlock.grappleAnchor) {
                        addNode(dimId, wx, y, wz);
                    }
                }
            }
        }
    }

    public List<BlockPos> queryRadius(int dimId, int centerX, int centerZ, int chunkRadius) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        Map<ChunkCoordIntPair, List<BlockPos>> dimMap = byDimension.get(dimId);
        if (dimMap == null) {
            return result;
        }
        int centerChunkX = centerX >> 4;
        int centerChunkZ = centerZ >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkCoordIntPair chunk = new ChunkCoordIntPair(centerChunkX + dx, centerChunkZ + dz);
                List<BlockPos> list = dimMap.get(chunk);
                if (list != null) {
                    result.addAll(list);
                }
            }
        }
        return result;
    }

    private Map<ChunkCoordIntPair, List<BlockPos>> getDimMap(int dimId) {
        Map<ChunkCoordIntPair, List<BlockPos>> dimMap = byDimension.get(dimId);
        if (dimMap == null) {
            dimMap = new HashMap<ChunkCoordIntPair, List<BlockPos>>();
            byDimension.put(dimId, dimMap);
        }
        return dimMap;
    }
}
