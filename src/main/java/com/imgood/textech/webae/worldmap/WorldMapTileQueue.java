package com.imgood.textech.webae.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWebMapTileJob;
import com.imgood.textech.webae.worldmap.engine.WorldMapZoomPyramid;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Server-side queue for deferred chunk tile rendering with per-tick budget.
 */
public final class WorldMapTileQueue {

    private static final int MAX_QUEUE = 4096;
    private static final WorldMapTileQueue INSTANCE = new WorldMapTileQueue();

    private final Deque<TileKey> queue = new ArrayDeque<TileKey>();
    private final Set<String> queuedKeys = new LinkedHashSet<String>();

    private WorldMapTileQueue() {}

    public static WorldMapTileQueue instance() {
        return INSTANCE;
    }

    public void enqueue(String view, int dim, int chunkX, int chunkZ) {
        enqueue(view, WorldMapTileLayer.TERRAIN, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ, null, -1);
    }

    public void enqueue(String view, int dim, int chunkX, int chunkZ, String ownerUuid, int networkId) {
        enqueue(view, WorldMapTileLayer.TERRAIN, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ, ownerUuid, networkId);
    }

    public void enqueue(String view, String layer, int dim, int chunkX, int chunkZ, String ownerUuid, int networkId) {
        enqueue(view, layer, WorldMapQualityTier.MEDIUM, dim, chunkX, chunkZ, ownerUuid, networkId);
    }

    public void enqueue(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId) {
        if (!Config.webWorldMapEnabled || !Config.webTopologyEnabled) {
            return;
        }
        if (WorldMapTileLayer.isAe(layer) && !Config.webWorldMapAeOverlayEnabled) {
            return;
        }
        WorldMapView parsed = WorldMapView.fromId(view);
        if (parsed == null || !WorldMapView.isEnabled(parsed)) {
            return;
        }
        WorldMapQualityTier tier = WorldMapQualityTier.clamp(
            quality != null ? quality : WorldMapQualityTier.MEDIUM,
            WorldMapQualityTier.fromConfigMax());
        if (WorldMapTileCache.exists(parsed.id, layer, tier, dim, chunkX, chunkZ)) {
            WorldMapTileProgressTracker.instance()
                .markDone(networkId, parsed.id, tier, dim, chunkX, chunkZ, layer);
            return;
        }
        if (shouldSkipServerUltraTerrain(tier, layer, ownerUuid)) {
            dispatchHdJobIfNeeded(parsed.id, layer, tier, dim, chunkX, chunkZ, ownerUuid, networkId);
            WorldMapTileProgressTracker.instance()
                .markQueued(networkId, parsed.id, tier, dim, chunkX, chunkZ, layer);
            return;
        }
        String key = tileKey(parsed.id, layer, tier, dim, chunkX, chunkZ);
        synchronized (this) {
            if (queuedKeys.contains(key)) {
                return;
            }
            if (queue.size() >= MAX_QUEUE) {
                TileKey dropped = queue.pollFirst();
                if (dropped != null) {
                    queuedKeys.remove(dropped.key);
                }
            }
            TileKey entry = new TileKey(parsed.id, layer, tier, dim, chunkX, chunkZ, ownerUuid, networkId, key);
            queue.offerLast(entry);
            queuedKeys.add(key);
        }
        WorldMapTileProgressTracker.instance()
            .markQueued(networkId, parsed.id, tier, dim, chunkX, chunkZ, layer);
        dispatchHdJobIfNeeded(parsed.id, layer, tier, dim, chunkX, chunkZ, ownerUuid, networkId);
    }

    /**
     * Enqueues terrain + ae tiles for one chunk. AE is skipped (marked empty) when the chunk has no AE devices.
     */
    public void enqueueChunkPair(String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId) {
        List<WorldMapAePlacementRecord> inChunk = WorldMapAePlacementSupport.filterChunk(
            WorldMapAePlacementSupport.loadForNetwork(ownerUuid, networkId),
            dim,
            chunkX,
            chunkZ);
        WorldMapView parsed = WorldMapView.fromId(view);
        WorldMapQualityTier tier = WorldMapQualityTier.clamp(
            quality != null ? quality : WorldMapQualityTier.MEDIUM,
            WorldMapQualityTier.fromConfigMax());
        boolean aeChunk = !inChunk.isEmpty();
        WorldMapQualityTier terrainTier = WorldMapQualitySupport.effectiveTier(tier, aeChunk);
        enqueue(view, WorldMapTileLayer.TERRAIN, terrainTier, dim, chunkX, chunkZ, ownerUuid, networkId);
        if (!Config.webWorldMapAeOverlayEnabled) {
            return;
        }
        if (parsed != null && inChunk.isEmpty()) {
            WorldMapTileProgressTracker.instance()
                .markEmpty(networkId, parsed.id, tier, dim, chunkX, chunkZ, WorldMapTileLayer.AE);
            return;
        }
        enqueue(view, WorldMapTileLayer.AE, quality, dim, chunkX, chunkZ, ownerUuid, networkId);
    }

    private static void dispatchHdJobIfNeeded(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX,
        int chunkZ, String ownerUuid, int networkId) {
        if (tier == null || !tier.isUltra() || !WorldMapHdSupport.isHdEnabled() || ownerUuid == null
            || ownerUuid.isEmpty()) {
            return;
        }
        EntityPlayerMP provider = WorldMapHdSupport.resolveHdProvider(ownerUuid, null);
        if (provider == null) {
            return;
        }
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new PacketWebMapTileJob(view, layer, tier.id, dim, chunkX, chunkZ, networkId),
            provider);
    }

    /** @deprecated Use {@link #enqueue(String, String, WorldMapQualityTier, int, int, int, String, int)}. */
    public void enqueue(int dim, int chunkX, int chunkZ) {
        enqueue(WorldMapView.FLAT.id, dim, chunkX, chunkZ);
    }

    public void onServerTick() {
        if (!Config.webWorldMapEnabled || !Config.webTopologyEnabled) {
            return;
        }
        int budget = Config.webWorldMapTileBudgetPerTick;
        if (budget <= 0) {
            budget = 1;
        }
        int rayBudget = Config.webWorldMapRayBudgetPerTick;
        if (rayBudget <= 0) {
            rayBudget = 1;
        }
        int rayUsed = 0;
        for (int i = 0; i < budget; i++) {
            TileKey next = pollNext();
            if (next == null) {
                break;
            }
            if (WorldMapTileCache.exists(next.view, next.layer, next.quality, next.dim, next.chunkX, next.chunkZ)) {
                WorldMapTileProgressTracker.instance()
                    .markDone(next.networkId, next.view, next.quality, next.dim, next.chunkX, next.chunkZ, next.layer);
                continue;
            }
            if (shouldSkipServerUltraTerrain(next.quality, next.layer, next.ownerUuid)) {
                dispatchHdJobIfNeeded(
                    next.view,
                    next.layer,
                    next.quality,
                    next.dim,
                    next.chunkX,
                    next.chunkZ,
                    next.ownerUuid,
                    next.networkId);
                requeueFront(next);
                continue;
            }
            WorldMapView view = WorldMapView.fromId(next.view);
            if (view == null || !WorldMapView.isEnabled(view)) {
                continue;
            }
            boolean isRayOblique = view.isOblique() && !WorldMapTileLayer.isAe(next.layer)
                && com.imgood.textech.webae.worldmap.engine.WorldMapRenderEngines.useRayOblique(next.quality);
            if (isRayOblique) {
                if (rayUsed >= rayBudget) {
                    requeueFront(next);
                    break;
                }
                rayUsed++;
            }
            WorldMapTileProgressTracker.instance()
                .markRendering(next.networkId, next.view, next.quality, next.dim, next.chunkX, next.chunkZ, next.layer);
            try {
                byte[] png = WorldMapRenderSupport.renderForView(
                    view,
                    next.layer,
                    next.quality,
                    next.dim,
                    next.chunkX,
                    next.chunkZ,
                    next.ownerUuid,
                    next.networkId);
                if (WorldMapRenderSupport.isValidTilePng(png)) {
                    WorldMapTileCache.write(next.view, next.layer, next.quality, next.dim, next.chunkX, next.chunkZ, png);
                    WorldMapZoomPyramid.enqueueParents(
                        next.view,
                        next.layer,
                        next.quality,
                        next.dim,
                        next.chunkX,
                        next.chunkZ);
                    WorldMapTileProgressTracker.instance()
                        .markDone(next.networkId, next.view, next.quality, next.dim, next.chunkX, next.chunkZ, next.layer);
                } else if (WorldMapTileLayer.isAe(next.layer)) {
                    WorldMapTileProgressTracker.instance()
                        .markEmpty(next.networkId, next.view, next.quality, next.dim, next.chunkX, next.chunkZ, next.layer);
                } else {
                    WorldMapTileProgressTracker.instance()
                        .markFailed(next.networkId, next.view, next.quality, next.dim, next.chunkX, next.chunkZ, next.layer);
                }
            } catch (Throwable t) {
                WorldMapTileProgressTracker.instance()
                    .markFailed(next.networkId, next.view, next.quality, next.dim, next.chunkX, next.chunkZ, next.layer);
                AdvanceDataMonitor.LOG.error(
                    "[WebAE] World map tile render failed view={} layer={} tier={} dim={} cx={} cz={}",
                    next.view,
                    next.layer,
                    next.quality.id,
                    next.dim,
                    next.chunkX,
                    next.chunkZ,
                    t);
            }
        }
        WorldMapZoomPyramid.instance()
            .onServerTick();
        WorldMapPrefetchQueue.instance()
            .onServerTick();
    }

    private static boolean shouldSkipServerUltraTerrain(WorldMapQualityTier tier, String layer, String ownerUuid) {
        return tier != null && tier.isUltra() && !WorldMapTileLayer.isAe(layer)
            && WorldMapHdSupport.isHdAvailable(ownerUuid, null);
    }

    private TileKey pollNext() {
        synchronized (this) {
            TileKey next = queue.pollFirst();
            if (next != null) {
                queuedKeys.remove(next.key);
            }
            return next;
        }
    }

    private void requeueFront(TileKey key) {
        if (key == null) {
            return;
        }
        synchronized (this) {
            queue.offerFirst(key);
            queuedKeys.add(key.key);
        }
    }

    private static String tileKey(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX,
        int chunkZ) {
        return view + ":" + WorldMapTileLayer.normalize(layer) + ":" + tier.id + ":" + dim + ":" + chunkX + ":"
            + chunkZ;
    }

    private static final class TileKey {

        final String view;
        final String layer;
        final WorldMapQualityTier quality;
        final int dim;
        final int chunkX;
        final int chunkZ;
        final String ownerUuid;
        final int networkId;
        final String key;

        TileKey(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
            String ownerUuid, int networkId, String key) {
            this.view = view;
            this.layer = WorldMapTileLayer.normalize(layer);
            this.quality = quality != null ? quality : WorldMapQualityTier.MEDIUM;
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownerUuid = ownerUuid;
            this.networkId = networkId;
            this.key = key;
        }
    }
}
