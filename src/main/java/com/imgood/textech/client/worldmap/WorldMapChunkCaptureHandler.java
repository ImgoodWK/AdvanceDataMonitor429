package com.imgood.textech.client.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;

import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWebMapTileUpload;
import com.imgood.textech.webae.worldmap.WorldMapClientCaptureMode;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapSnapshotMode;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Proactively captures flat terrain tiles while the player explores nearby chunks
 * (JourneyMap-style pre-warm) and uploads them to the server cache.
 */
@SideOnly(Side.CLIENT)
public final class WorldMapChunkCaptureHandler {

    private static final int CAPTURE_INTERVAL_TICKS = 10;
    private static final WorldMapChunkCaptureHandler INSTANCE = new WorldMapChunkCaptureHandler();

    private final Deque<CaptureTarget> queue = new ArrayDeque<CaptureTarget>();
    private final Set<String> queuedKeys = new HashSet<String>();
    private final Set<String> uploadedKeys = new HashSet<String>();
    private final WorldMapChunkGlRenderer renderer = new WorldMapChunkGlRenderer();
    private int tickCounter;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;

    private WorldMapChunkCaptureHandler() {}

    public static WorldMapChunkCaptureHandler instance() {
        return INSTANCE;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!Config.webWorldMapClientHdEnabled || !WorldMapClientCaptureMode.isEnabled()) {
            return;
        }
        if (WorldMapSnapshotMode.isClientOnly()) {
            return;
        }
        if (Config.worldMapClientCaptureRadius <= 0 || Config.worldMapClientCaptureBudgetPerTick <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        tickCounter++;
        int playerChunkX = mc.thePlayer.chunkCoordX;
        int playerChunkZ = mc.thePlayer.chunkCoordZ;
        if (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ
            || tickCounter >= CAPTURE_INTERVAL_TICKS) {
            tickCounter = 0;
            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;
            scheduleNearby(playerChunkX, playerChunkZ, mc.theWorld.provider.dimensionId);
        }

        int budget = Config.worldMapClientCaptureBudgetPerTick;
        WorldMapQualityTier tier = WorldMapQualityTier.fromConfigDefault();
        for (int i = 0; i < budget; i++) {
            CaptureTarget target = pollTarget();
            if (target == null) {
                break;
            }
            byte[] png = renderer.renderTerrain(mc, WorldMapView.FLAT, tier, target.dim, target.chunkX, target.chunkZ);
            if (!WorldMapRenderSupport.isValidTilePng(png)) {
                continue;
            }
            String ownerUuid = mc.thePlayer.getUniqueID()
                .toString();
            boolean sent = PacketWebMapTileUpload.sendToServer(
                WorldMapView.FLAT.id,
                WorldMapTileLayer.TERRAIN,
                tier.id,
                target.dim,
                target.chunkX,
                target.chunkZ,
                -1,
                ownerUuid,
                png);
            if (sent) {
                uploadedKeys.add(target.key);
            }
        }
    }

    private void scheduleNearby(int centerX, int centerZ, int dim) {
        int radius = Config.worldMapClientCaptureRadius;
        WorldMapQualityTier tier = WorldMapQualityTier.fromConfigDefault();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                String key = captureKey(dim, cx, cz, tier.id);
                if (uploadedKeys.contains(key) || queuedKeys.contains(key)) {
                    continue;
                }
                synchronized (this) {
                    if (queuedKeys.contains(key)) {
                        continue;
                    }
                    queue.offerLast(new CaptureTarget(dim, cx, cz, key));
                    queuedKeys.add(key);
                }
            }
        }
    }

    private CaptureTarget pollTarget() {
        synchronized (this) {
            CaptureTarget next = queue.pollFirst();
            if (next != null) {
                queuedKeys.remove(next.key);
            }
            return next;
        }
    }

    private static String captureKey(int dim, int chunkX, int chunkZ, String quality) {
        return dim + ":" + chunkX + ":" + chunkZ + ":" + quality;
    }

    private static final class CaptureTarget {

        final int dim;
        final int chunkX;
        final int chunkZ;
        final String key;

        CaptureTarget(int dim, int chunkX, int chunkZ, String key) {
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.key = key;
        }
    }
}
