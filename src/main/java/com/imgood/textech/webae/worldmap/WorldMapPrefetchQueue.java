package com.imgood.textech.webae.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;

import com.imgood.textech.Config;

/**
 * Spreads world map prefetch enqueue across server ticks instead of flooding the tile queue in one HTTP call.
 */
public final class WorldMapPrefetchQueue {

    private static final WorldMapPrefetchQueue INSTANCE = new WorldMapPrefetchQueue();
    private final Deque<PrefetchChunk> pending = new ArrayDeque<PrefetchChunk>();

    private WorldMapPrefetchQueue() {}

    public static WorldMapPrefetchQueue instance() {
        return INSTANCE;
    }

    public void schedule(String viewId, WorldMapQualityTier tier, int dim, int chunkX, int chunkZ, String ownerUuid,
        int networkId) {
        synchronized (pending) {
            pending.offerLast(new PrefetchChunk(viewId, tier, dim, chunkX, chunkZ, ownerUuid, networkId));
        }
    }

    public int pendingCount() {
        synchronized (pending) {
            return pending.size();
        }
    }

    public void onServerTick() {
        if (!Config.webWorldMapEnabled || !Config.webTopologyEnabled) {
            synchronized (pending) {
                pending.clear();
            }
            return;
        }
        int budget = Math.max(1, Config.webWorldMapTileBudgetPerTick * 16);
        for (int i = 0; i < budget; i++) {
            PrefetchChunk next;
            synchronized (pending) {
                next = pending.pollFirst();
            }
            if (next == null) {
                break;
            }
            WorldMapTileQueue.instance()
                .enqueueChunkPair(
                    next.viewId,
                    next.tier,
                    next.dim,
                    next.chunkX,
                    next.chunkZ,
                    next.ownerUuid,
                    next.networkId);
        }
    }

    private static final class PrefetchChunk {

        final String viewId;
        final WorldMapQualityTier tier;
        final int dim;
        final int chunkX;
        final int chunkZ;
        final String ownerUuid;
        final int networkId;

        PrefetchChunk(String viewId, WorldMapQualityTier tier, int dim, int chunkX, int chunkZ, String ownerUuid,
            int networkId) {
            this.viewId = viewId;
            this.tier = tier;
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownerUuid = ownerUuid;
            this.networkId = networkId;
        }
    }
}
