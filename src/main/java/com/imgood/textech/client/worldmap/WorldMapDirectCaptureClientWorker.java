package com.imgood.textech.client.worldmap;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;

import com.imgood.textech.client.worldmap.dynmap.WorldMapDynmapClientFetcher;
import com.imgood.textech.webae.network.PacketWorldMapDirectCaptureRequest;
import com.imgood.textech.webae.network.PacketWorldMapDirectCaptureResponse;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Processes SP direct capture requests on the client main thread.
 */
@SideOnly(Side.CLIENT)
public final class WorldMapDirectCaptureClientWorker {

    private static final WorldMapDirectCaptureClientWorker INSTANCE = new WorldMapDirectCaptureClientWorker();
    private final Deque<PacketWorldMapDirectCaptureRequest> queue = new ArrayDeque<PacketWorldMapDirectCaptureRequest>();

    private WorldMapDirectCaptureClientWorker() {}

    public static WorldMapDirectCaptureClientWorker instance() {
        return INSTANCE;
    }

    public void enqueue(PacketWorldMapDirectCaptureRequest request) {
        if (request == null || request.requestId == null || request.requestId.isEmpty()) {
            return;
        }
        synchronized (queue) {
            queue.offerLast(request);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        WorldMapDynmapClientFetcher.instance()
            .onClientTickEnd();
        PacketWorldMapDirectCaptureRequest request;
        synchronized (queue) {
            request = queue.pollFirst();
        }
        if (request == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            sendResponse(request.requestId, null);
            return;
        }
        byte[] png = null;
        if (WorldMapTileLayer.isAe(request.layer)) {
            if (mc.theWorld != null) {
                png = WorldMapAeVectorOverlayRenderer.render(
                    mc.theWorld,
                    request.ownerUuid,
                    request.networkId,
                    WorldMapView.FLAT,
                    request.dim,
                    request.chunkX,
                    request.chunkZ);
            }
        } else {
            WorldMapQualityTier tier = WorldMapQualityTier.fromTilePx(request.tilePx);
            com.imgood.textech.webae.worldmap.WorldMapTerrainCaptureResult terrain = WorldMapTerrainCaptureChainClient
                .captureTerrain(
                    mc,
                    WorldMapView.FLAT,
                    request.dim,
                    request.chunkX,
                    request.chunkZ,
                    request.tilePx,
                    tier);
            if (terrain != null && terrain.isValid()) {
                png = terrain.png;
            }
        }
        if (!WorldMapRenderSupport.isValidTilePng(png)) {
            png = null;
        }
        sendResponse(request.requestId, png);
    }

    private static void sendResponse(String requestId, byte[] png) {
        PacketWorldMapDirectCaptureResponse.sendToServer(requestId, png);
    }
}
