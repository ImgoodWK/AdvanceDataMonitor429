package com.imgood.textech.webae.worldmap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWorldMapDirectCaptureRequest;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Bridges HTTP direct-tile requests to client GL capture via packets (integrated SP).
 */
public final class WorldMapDirectCaptureBridge {

    private static final WorldMapDirectCaptureBridge INSTANCE = new WorldMapDirectCaptureBridge();
    private static final AtomicInteger REQUEST_SEQ = new AtomicInteger();

    private final ConcurrentHashMap<String, PendingCapture> pending = new ConcurrentHashMap<String, PendingCapture>();

    private WorldMapDirectCaptureBridge() {}

    public static WorldMapDirectCaptureBridge instance() {
        return INSTANCE;
    }

    public byte[] requestClientCapture(String layer, String ownerUuid, int networkId, int dim, int chunkX, int chunkZ,
        int tilePx, long timeoutMs) {
        EntityPlayerMP player = firstOnlinePlayer();
        if (player == null) {
            return null;
        }
        String requestId = "wmdc-" + System.currentTimeMillis() + "-" + REQUEST_SEQ.incrementAndGet();
        PendingCapture capture = new PendingCapture();
        capture.latch = new CountDownLatch(1);
        pending.put(requestId, capture);

        PacketWorldMapDirectCaptureRequest packet = new PacketWorldMapDirectCaptureRequest();
        packet.requestId = requestId;
        packet.layer = WorldMapTileLayer.normalize(layer);
        packet.ownerUuid = ownerUuid;
        packet.networkId = networkId;
        packet.dim = dim;
        packet.chunkX = chunkX;
        packet.chunkZ = chunkZ;
        packet.tilePx = tilePx > 0 ? tilePx : 128;
        AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);

        long waitMs = timeoutMs > 0 ? timeoutMs : Math.max(1000L, Config.webWorldMapClientHdTimeoutMs);
        try {
            if (!capture.latch.await(waitMs, TimeUnit.MILLISECONDS)) {
                pending.remove(requestId);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            pending.remove(requestId);
            return null;
        }
        pending.remove(requestId);
        return capture.png;
    }

    public void complete(String requestId, byte[] png) {
        if (requestId == null) {
            return;
        }
        PendingCapture capture = pending.get(requestId);
        if (capture == null) {
            return;
        }
        capture.png = png;
        capture.latch.countDown();
    }

    public static boolean isIntegratedSinglePlayer() {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        return server != null && server.isSinglePlayer();
    }

    private static EntityPlayerMP firstOnlinePlayer() {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        java.util.List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        if (players == null || players.isEmpty()) {
            return null;
        }
        return players.get(0);
    }

    private static final class PendingCapture {

        CountDownLatch latch;
        byte[] png;
    }
}
