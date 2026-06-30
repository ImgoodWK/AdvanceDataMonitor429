package com.imgood.textech.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.world.ChunkCoordIntPair;

import com.imgood.textech.Config;
import com.imgood.textech.utils.BlockPos;

/**
 * Server-side chunk-indexed registry of grapple anchor blocks.
 * Updated on block place/break only â€?no world scans at query time.
 */
public final class GrappleNodeIndex {

    public static final GrappleNodeIndex INSTANCE = new GrappleNodeIndex();

    private final Map<Integer, Map<ChunkCoordIntPair, List<BlockPos>>> byDimension = new HashMap<Integer, Map<ChunkCoordIntPair, List<BlockPos>>>();

    private GrappleNodeIndex() {}

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
        GrapplePlayerState.onNodeIndexChanged(dimId, chunk);
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
        GrapplePlayerState.onNodeIndexChanged(dimId, chunk);
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

    public List<BlockPos> queryAroundAnchor(int dimId, int anchorX, int anchorZ) {
        return queryRadius(dimId, anchorX, anchorZ, Config.grappleScanChunkRadius);
    }

    public boolean contains(int dimId, int x, int y, int z) {
        Map<ChunkCoordIntPair, List<BlockPos>> dimMap = byDimension.get(dimId);
        if (dimMap == null) {
            return false;
        }
        List<BlockPos> list = dimMap.get(new ChunkCoordIntPair(x >> 4, z >> 4));
        if (list == null) {
            return false;
        }
        BlockPos target = new BlockPos(x, y, z);
        for (BlockPos pos : list) {
            if (pos.equals(target)) {
                return true;
            }
        }
        return false;
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
