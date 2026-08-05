package com.imgood.textech.client.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;

import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWebMapTileJob;
import com.imgood.textech.webae.network.PacketWebMapTileUpload;
import com.imgood.textech.webae.worldmap.WorldMapClientCaptureMode;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side queue for HD world map tile render jobs pushed from the server.
 */
@SideOnly(Side.CLIENT)
public final class WorldMapTileRenderWorker {

    private static final int MAX_QUEUE = 2048;
    private static final int MAX_RETRIES = 40;
    private static final WorldMapTileRenderWorker INSTANCE = new WorldMapTileRenderWorker();

    private final Deque<PendingJob> queue = new ArrayDeque<PendingJob>();
    private final Set<String> queuedKeys = new LinkedHashSet<String>();
    private final WorldMapChunkGlRenderer renderer = new WorldMapChunkGlRenderer();

    private WorldMapTileRenderWorker() {}

    public static WorldMapTileRenderWorker instance() {
        return INSTANCE;
    }

    public void enqueue(PacketWebMapTileJob job) {
        if (job == null || job.view == null || job.view.isEmpty()) {
            return;
        }
        if (!Config.webWorldMapClientHdEnabled) {
            return;
        }
        WorldMapQualityTier tier = WorldMapQualityTier.fromId(job.quality);
        if (!WorldMapClientCaptureMode.shouldUseClientForTier(tier)) {
            return;
        }
        if (WorldMapTileLayer.isAe(job.layer) && !Config.webWorldMapAeOverlayEnabled) {
            return;
        }
        WorldMapView view = WorldMapView.fromId(job.view);
        if (view == null || !WorldMapView.isEnabled(view)) {
            return;
        }
        String key = tileKey(job.view, job.layer, job.quality, job.dim, job.chunkX, job.chunkZ);
        synchronized (this) {
            if (queuedKeys.contains(key)) {
                return;
            }
            if (queue.size() >= MAX_QUEUE) {
                PendingJob dropped = queue.pollFirst();
                if (dropped != null) {
                    queuedKeys.remove(dropped.key);
                }
            }
            PendingJob pending = new PendingJob(job, key);
            queue.offerLast(pending);
            queuedKeys.add(key);
        }
    }

    public int pendingCount() {
        synchronized (this) {
            return queue.size();
        }
    }

    public void clear() {
        synchronized (this) {
            queue.clear();
            queuedKeys.clear();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!Config.webWorldMapClientHdEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return;
        }

        int budget = Config.webWorldMapClientHdBudgetPerTick;
        if (budget <= 0) {
            budget = 1;
        }
        for (int i = 0; i < budget; i++) {
            PendingJob job = poll();
            if (job == null) {
                break;
            }
            WorldMapView view = WorldMapView.fromId(job.job.view);
            if (view == null) {
                continue;
            }
            WorldMapQualityTier tier = WorldMapQualityTier.fromId(job.job.quality);
            if (WorldMapTileLayer.isAe(job.job.layer)) {
                // AE overlay uses server-side category ID tiles; skip client GL upload.
                continue;
            }
            byte[] png = renderer.renderTerrain(mc, view, tier, job.job.dim, job.job.chunkX, job.job.chunkZ);
            String ownerUuid = mc.thePlayer.getUniqueID()
                .toString();
            if (WorldMapRenderSupport.isValidTilePng(png)
                && PacketWebMapTileUpload.sendToServer(
                    view.id,
                    job.job.layer,
                    tier.id,
                    job.job.dim,
                    job.job.chunkX,
                    job.job.chunkZ,
                    job.job.networkId,
                    ownerUuid,
                    png)) {
                continue;
            } else if (job.retries < MAX_RETRIES) {
                job.retries++;
                requeue(job);
            }
        }
    }

    private PendingJob poll() {
        synchronized (this) {
            PendingJob next = queue.pollFirst();
            if (next != null) {
                queuedKeys.remove(next.key);
            }
            return next;
        }
    }

    private void requeue(PendingJob job) {
        synchronized (this) {
            if (queuedKeys.contains(job.key)) {
                return;
            }
            if (queue.size() >= MAX_QUEUE) {
                return;
            }
            queue.offerLast(job);
            queuedKeys.add(job.key);
        }
    }

    private static String tileKey(String view, String layer, String quality, int dim, int chunkX, int chunkZ) {
        return view + ":"
            + WorldMapTileLayer.normalize(layer)
            + ":"
            + quality
            + ":"
            + dim
            + ":"
            + chunkX
            + ":"
            + chunkZ;
    }

    private static final class PendingJob {

        final PacketWebMapTileJob job;
        final String key;
        int retries;

        PendingJob(PacketWebMapTileJob job, String key) {
            this.job = job;
            this.key = key;
        }
    }
}
