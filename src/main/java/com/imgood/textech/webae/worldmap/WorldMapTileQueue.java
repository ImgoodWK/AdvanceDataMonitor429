package com.imgood.textech.webae.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWebMapTileJob;

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
        enqueue(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, null, -1);
    }

    public void enqueue(String view, int dim, int chunkX, int chunkZ, String ownerUuid, int networkId) {
        enqueue(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, ownerUuid, networkId);
    }

    public void enqueue(String view, String layer, int dim, int chunkX, int chunkZ, String ownerUuid, int networkId) {
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
        if (WorldMapTileCache.exists(parsed.id, layer, dim, chunkX, chunkZ)) {
            return;
        }
        String key = tileKey(parsed.id, layer, dim, chunkX, chunkZ);
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
            TileKey entry = new TileKey(parsed.id, layer, dim, chunkX, chunkZ, ownerUuid, networkId, key);
            queue.offerLast(entry);
            queuedKeys.add(key);
        }
        dispatchHdJobIfNeeded(parsed.id, layer, dim, chunkX, chunkZ, ownerUuid, networkId);
    }

    private static void dispatchHdJobIfNeeded(String view, String layer, int dim, int chunkX, int chunkZ,
        String ownerUuid, int networkId) {
        if (!WorldMapHdSupport.isHdEnabled() || ownerUuid == null || ownerUuid.isEmpty()) {
            return;
        }
        EntityPlayerMP provider = WorldMapHdSupport.resolveHdProvider(ownerUuid, null);
        if (provider == null) {
            return;
        }
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new PacketWebMapTileJob(view, layer, dim, chunkX, chunkZ, networkId),
            provider);
    }

    /** @deprecated Use {@link #enqueue(String, int, int, int)}. */
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
        for (int i = 0; i < budget; i++) {
            TileKey next = pollNext();
            if (next == null) {
                break;
            }
            if (WorldMapTileCache.exists(next.view, next.layer, next.dim, next.chunkX, next.chunkZ)) {
                continue;
            }
            WorldMapView view = WorldMapView.fromId(next.view);
            if (view == null || !WorldMapView.isEnabled(view)) {
                continue;
            }
            try {
                // Always render server-side tiles as fallback. Client HD upload may overwrite via writeHd().
                byte[] png = WorldMapRenderSupport.renderForView(view, next.layer, next.dim, next.chunkX, next.chunkZ,
                    next.ownerUuid, next.networkId);
                if (WorldMapRenderSupport.isValidTilePng(png)) {
                    WorldMapTileCache.write(next.view, next.layer, next.dim, next.chunkX, next.chunkZ, png);
                }
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.error(
                    "[WebAE] World map tile render failed view={} layer={} dim={} cx={} cz={}",
                    next.view,
                    next.layer,
                    next.dim,
                    next.chunkX,
                    next.chunkZ,
                    t);
            }
        }
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

    private static String tileKey(String view, String layer, int dim, int chunkX, int chunkZ) {
        return view + ":" + WorldMapTileLayer.normalize(layer) + ":" + dim + ":" + chunkX + ":" + chunkZ;
    }

    private static final class TileKey {

        final String view;
        final String layer;
        final int dim;
        final int chunkX;
        final int chunkZ;
        final String ownerUuid;
        final int networkId;
        final String key;

        TileKey(String view, String layer, int dim, int chunkX, int chunkZ, String ownerUuid, int networkId,
            String key) {
            this.view = view;
            this.layer = WorldMapTileLayer.normalize(layer);
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownerUuid = ownerUuid;
            this.networkId = networkId;
            this.key = key;
        }
    }
}
