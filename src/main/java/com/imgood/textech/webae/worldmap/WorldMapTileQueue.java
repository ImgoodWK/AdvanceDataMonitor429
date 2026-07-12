package com.imgood.textech.webae.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWebMapTileJob;
import com.imgood.textech.webae.worldmap.engine.WorldMapZoomPyramid;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Server-side queue for deferred chunk tile rendering with per-tick budget.
 * Rendering is offloaded to {@link WorldMapRenderExecutor} so it does not block
 * the main server tick. Results are collected and cached on the main thread in
 * {@link #onServerTick()}.
 */
public final class WorldMapTileQueue {

    private static final int MAX_QUEUE = 4096;
    private static final WorldMapTileQueue INSTANCE = new WorldMapTileQueue();

    private final Deque<TileKey> queue = new ArrayDeque<TileKey>();
    private final Set<String> queuedKeys = new LinkedHashSet<String>();
    private final ConcurrentLinkedQueue<PendingResult> results = new ConcurrentLinkedQueue<PendingResult>();

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
        enqueue(view, layer, quality, dim, chunkX, chunkZ, ownerUuid, networkId, null);
    }

    public void enqueue(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId, String actorUuid) {
        if (!Config.webWorldMapEnabled || !Config.webTopologyEnabled) {
            return;
        }
        if (WorldMapSnapshotMode.isClientOnly()) {
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
        if (shouldSkipServerTerrainWithHd(tier, layer, ownerUuid, actorUuid, dim)) {
            dispatchHdJobIfNeeded(parsed.id, layer, tier, dim, chunkX, chunkZ, ownerUuid, networkId, actorUuid);
            WorldMapTileProgressTracker.instance()
                .markQueued(networkId, parsed.id, tier, dim, chunkX, chunkZ, layer);
        }
        String key = tileKey(parsed.id, layer, tier, dim, chunkX, chunkZ);
        boolean hdDispatched = shouldSkipServerTerrainWithHd(tier, layer, ownerUuid, actorUuid, dim);
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
            TileKey entry = new TileKey(parsed.id, layer, tier, dim, chunkX, chunkZ, ownerUuid, networkId, actorUuid,
                key);
            if (hdDispatched) {
                entry.hdDispatchTime = System.currentTimeMillis();
            }
            queue.offerLast(entry);
            queuedKeys.add(key);
        }
        WorldMapTileProgressTracker.instance()
            .markQueued(networkId, parsed.id, tier, dim, chunkX, chunkZ, layer);
        if (!hdDispatched) {
            dispatchHdJobIfNeeded(parsed.id, layer, tier, dim, chunkX, chunkZ, ownerUuid, networkId, actorUuid);
        }
    }

    /**
     * Enqueues terrain + ae tiles for one chunk. AE is skipped (marked empty) when the chunk has no AE devices.
     */
    public void enqueueChunkPair(String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId) {
        enqueueChunkPair(view, quality, dim, chunkX, chunkZ, ownerUuid, networkId, null);
    }

    public void enqueueChunkPair(String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId, String actorUuid) {
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
        enqueue(view, WorldMapTileLayer.TERRAIN, terrainTier, dim, chunkX, chunkZ, ownerUuid, networkId, actorUuid);
        if (!Config.webWorldMapAeOverlayEnabled) {
            return;
        }
        WorldMapQualityTier aeTier = WorldMapQualityTier.fromConfigAeOverlay();
        if (parsed != null && inChunk.isEmpty()) {
            WorldMapTileProgressTracker.instance()
                .markEmpty(networkId, parsed.id, aeTier, dim, chunkX, chunkZ, WorldMapTileLayer.AE);
            return;
        }
        enqueue(view, WorldMapTileLayer.AE, aeTier, dim, chunkX, chunkZ, ownerUuid, networkId, actorUuid);
    }

    private static void dispatchHdJobIfNeeded(String view, String layer, WorldMapQualityTier tier, int dim, int chunkX,
        int chunkZ, String ownerUuid, int networkId, String actorUuid) {
        if (tier == null || !WorldMapClientCaptureMode.shouldUseClientForTier(tier) || !WorldMapHdSupport.isHdEnabled()
            || ownerUuid == null || ownerUuid.isEmpty()) {
            return;
        }
        EntityPlayerMP provider = WorldMapHdSupport.resolveHdProvider(ownerUuid, actorUuid, dim);
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

    /**
     * Main server tick: submits render tasks to the background thread pool, then collects
     * completed results and writes them to the tile cache on the main thread.
     */
    public void onServerTick() {
        if (WorldMapSnapshotMode.isClientOnly()) {
            return;
        }
        if (!Config.webWorldMapEnabled || !Config.webTopologyEnabled) {
            return;
        }

        // --- Collect completed render results from worker threads ---
        PendingResult result;
        while ((result = results.poll()) != null) {
            try {
                if (WorldMapRenderSupport.isValidTilePng(result.png)) {
                    WorldMapTileCache.write(
                        result.view,
                        result.layer,
                        result.quality,
                        result.dim,
                        result.chunkX,
                        result.chunkZ,
                        result.png);
                    WorldMapZoomPyramid.enqueueParents(
                        result.view,
                        result.layer,
                        result.quality,
                        result.dim,
                        result.chunkX,
                        result.chunkZ);
                    WorldMapTileProgressTracker.instance()
                        .markDone(
                            result.networkId,
                            result.view,
                            result.quality,
                            result.dim,
                            result.chunkX,
                            result.chunkZ,
                            result.layer);
                } else if (WorldMapTileLayer.isAe(result.layer)) {
                    WorldMapTileProgressTracker.instance()
                        .markEmpty(
                            result.networkId,
                            result.view,
                            result.quality,
                            result.dim,
                            result.chunkX,
                            result.chunkZ,
                            result.layer);
                } else if (!WorldMapTileLayer.isAe(result.layer)
                    && WorldMapRenderSupport.isLoadedEmptyTerrainChunk(result.dim, result.chunkX, result.chunkZ)) {
                    WorldMapTileCache.writeEmpty(
                        result.view,
                        result.layer,
                        result.quality,
                        result.dim,
                        result.chunkX,
                        result.chunkZ);
                    WorldMapTileProgressTracker.instance()
                        .markEmpty(
                            result.networkId,
                            result.view,
                            result.quality,
                            result.dim,
                            result.chunkX,
                            result.chunkZ,
                            result.layer);
                } else {
                    WorldMapTileProgressTracker.instance()
                        .markFailed(
                            result.networkId,
                            result.view,
                            result.quality,
                            result.dim,
                            result.chunkX,
                            result.chunkZ,
                            result.layer);
                }
            } catch (Throwable t) {
                WorldMapTileProgressTracker.instance()
                    .markFailed(
                        result.networkId,
                        result.view,
                        result.quality,
                        result.dim,
                        result.chunkX,
                        result.chunkZ,
                        result.layer);
                AdvanceDataMonitor.LOG.error(
                    "[WebAE] World map tile result processing failed view={} layer={} tier={} dim={} cx={} cz={}",
                    result.view,
                    result.layer,
                    result.quality.id,
                    result.dim,
                    result.chunkX,
                    result.chunkZ,
                    t);
            }
        }

        // --- Submit pending tasks to the render executor ---
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
            if (shouldSkipServerTerrainWithHd(next.quality, next.layer, next.ownerUuid, next.actorUuid, next.dim)) {
                if (next.hdDispatchTime < 0) {
                    dispatchHdJobIfNeeded(
                        next.view,
                        next.layer,
                        next.quality,
                        next.dim,
                        next.chunkX,
                        next.chunkZ,
                        next.ownerUuid,
                        next.networkId,
                        next.actorUuid);
                    next.hdDispatchTime = System.currentTimeMillis();
                    requeueFront(next);
                    continue;
                }
                long elapsed = System.currentTimeMillis() - next.hdDispatchTime;
                int timeoutMs = Config.webWorldMapClientHdTimeoutMs > 0 ? Config.webWorldMapClientHdTimeoutMs : 5000;
                if (elapsed < timeoutMs) {
                    requeueFront(next);
                    continue;
                }
                // Timeout: fall through to server-side render.
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
                .markRendering(
                    next.networkId,
                    next.view,
                    next.quality,
                    next.dim,
                    next.chunkX,
                    next.chunkZ,
                    next.layer);

            // Pre-load chunk references for the padded region on the main thread
            // without triggering synchronous chunk loads. Worker threads use
            // chunkIfLoaded() which returns null for unloaded chunks — rendering
            // will produce empty tiles that will be retried when the chunk loads naturally.
            WorldMapRenderSupport.preloadChunkRegionIfLoaded(
                next.dim,
                next.chunkX,
                next.chunkZ,
                Config.webWorldMapChunkPadding);

            final TileKey captured = next;
            final WorldMapView capturedView = view;
            WorldMapRenderExecutor.instance()
                .submit(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            byte[] png = WorldMapRenderSupport.renderForView(
                                capturedView,
                                captured.layer,
                                captured.quality,
                                captured.dim,
                                captured.chunkX,
                                captured.chunkZ,
                                captured.ownerUuid,
                                captured.networkId);
                            results.offer(
                                new PendingResult(
                                    captured.view,
                                    captured.layer,
                                    captured.quality,
                                    captured.dim,
                                    captured.chunkX,
                                    captured.chunkZ,
                                    captured.ownerUuid,
                                    captured.networkId,
                                    png));
                        } catch (Throwable t) {
                            AdvanceDataMonitor.LOG.error(
                                "[WebAE] World map tile render failed view={} layer={} tier={} dim={} cx={} cz={}",
                                captured.view,
                                captured.layer,
                                captured.quality.id,
                                captured.dim,
                                captured.chunkX,
                                captured.chunkZ,
                                t);
                            results.offer(
                                new PendingResult(
                                    captured.view,
                                    captured.layer,
                                    captured.quality,
                                    captured.dim,
                                    captured.chunkX,
                                    captured.chunkZ,
                                    captured.ownerUuid,
                                    captured.networkId,
                                    null));
                        }
                    }
                });
        }
        WorldMapZoomPyramid.instance()
            .onServerTick();
        WorldMapPrefetchQueue.instance()
            .onServerTick();
    }

    /**
     * Returns true when server-side render should be skipped in favor of client HD for
     * high/ultra terrain tiles where an online client is available.
     */
    private static boolean shouldSkipServerTerrainWithHd(WorldMapQualityTier tier, String layer, String ownerUuid,
        String actorUuid, int dim) {
        if (WorldMapTileLayer.isAe(layer)) {
            return false;
        }
        if (!WorldMapClientCaptureMode.shouldUseClientForTier(tier)) {
            return false;
        }
        return WorldMapHdSupport.isClientCaptureAvailable(ownerUuid, actorUuid, dim);
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

    /**
     * Immutable result transported from worker thread back to the main server tick.
     */
    private static final class PendingResult {

        final String view;
        final String layer;
        final WorldMapQualityTier quality;
        final int dim;
        final int chunkX;
        final int chunkZ;
        final String ownerUuid;
        final int networkId;
        final byte[] png;

        PendingResult(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
            String ownerUuid, int networkId, byte[] png) {
            this.view = view;
            this.layer = layer;
            this.quality = quality;
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownerUuid = ownerUuid;
            this.networkId = networkId;
            this.png = png;
        }
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
        final String actorUuid;
        final String key;
        long hdDispatchTime = -1;

        TileKey(String view, String layer, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
            String ownerUuid, int networkId, String actorUuid, String key) {
            this.view = view;
            this.layer = WorldMapTileLayer.normalize(layer);
            this.quality = quality != null ? quality : WorldMapQualityTier.MEDIUM;
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownerUuid = ownerUuid;
            this.networkId = networkId;
            this.actorUuid = actorUuid;
            this.key = key;
        }
    }
}
