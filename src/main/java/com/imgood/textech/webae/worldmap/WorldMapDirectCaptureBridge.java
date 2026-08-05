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
import com.imgood.textech.webae.network.WebAeBinaryTransfer;

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

    public byte[] requestClientCapture(String layer, String ownerUuid, String actorUuid, int networkId, int dim,
        int chunkX, int chunkZ, int tilePx, long timeoutMs) {
        if (!isIntegratedSinglePlayer() || !WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)
            || !WorldMapPacketAuthorization.isValidOwnerUuid(actorUuid)
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId)
            || !WorldMapPacketAuthorization.isValidLayer(layer)
            || !WorldMapPacketAuthorization.isValidChunk(dim, chunkX, chunkZ)
            || !WorldMapPacketAuthorization.isValidTilePx(tilePx)) {
            return null;
        }
        EntityPlayerMP player = WorldMapHdSupport.resolveHdProvider(ownerUuid, actorUuid, dim, networkId);
        if (player == null) {
            return null;
        }
        String requestId = "wmdc-" + System.currentTimeMillis() + "-" + REQUEST_SEQ.incrementAndGet();
        PendingCapture capture = new PendingCapture();
        capture.latch = new CountDownLatch(1);
        capture.playerUuid = player.getUniqueID()
            .toString();
        capture.ownerUuid = WorldMapPacketAuthorization.canonicalOwnerUuid(ownerUuid);
        capture.networkId = networkId;
        capture.layer = WorldMapTileLayer.isAe(layer) ? WorldMapTileLayer.AE : WorldMapTileLayer.TERRAIN;
        capture.dim = dim;
        capture.chunkX = chunkX;
        capture.chunkZ = chunkZ;
        capture.tilePx = tilePx;
        capture.lastTouchedMs = System.currentTimeMillis();
        pruneExpired();
        synchronized (pending) {
            if (pending.size() >= MAX_ACTIVE_CAPTURES) {
                return null;
            }
            pending.put(requestId, capture);
        }

        PacketWorldMapDirectCaptureRequest packet = new PacketWorldMapDirectCaptureRequest();
        packet.requestId = requestId;
        packet.layer = capture.layer;
        packet.ownerUuid = capture.ownerUuid;
        packet.networkId = networkId;
        packet.dim = dim;
        packet.chunkX = chunkX;
        packet.chunkZ = chunkZ;
        packet.tilePx = capture.tilePx;
        try {
            AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);
        } catch (RuntimeException e) {
            abort(requestId, capture);
            return null;
        }

        long requestedWaitMs = timeoutMs > 0 ? timeoutMs : Math.max(1000L, Config.webWorldMapClientHdTimeoutMs);
        long waitMs = Math.min(requestedWaitMs, WebAeBinaryTransfer.SESSION_TTL_MS);
        try {
            if (!capture.latch.await(waitMs, TimeUnit.MILLISECONDS)) {
                abort(requestId, capture);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            abort(requestId, capture);
            return null;
        }
        synchronized (capture) {
            if (capture.closed) {
                return null;
            }
            pending.remove(requestId, capture);
            capture.closed = true;
            byte[] result = capture.png;
            capture.png = null;
            capture.assembler = null;
            return result;
        }
    }

    public void complete(String requestId, EntityPlayerMP player, boolean success, int chunkIndex, int totalChunks,
        byte[] pngChunk) {
        if (requestId == null || player == null) {
            return;
        }
        PendingCapture capture = pending.get(requestId);
        String playerUuid = player.getUniqueID()
            .toString();
        if (capture == null || capture.playerUuid == null || !capture.playerUuid.equals(playerUuid)) {
            return;
        }
        synchronized (capture) {
            if (capture.closed || capture.latch.getCount() == 0L) {
                return;
            }
            try {
                if (System.currentTimeMillis() - capture.lastTouchedMs > WebAeBinaryTransfer.SESSION_TTL_MS) {
                    abort(requestId, capture);
                    return;
                }
                if (!success) {
                    if (chunkIndex != 0 || totalChunks != 1 || pngChunk == null || pngChunk.length != 0) {
                        abort(requestId, capture);
                        return;
                    }
                    capture.png = null;
                    capture.latch.countDown();
                    return;
                }
                if (pngChunk == null || pngChunk.length == 0 || pngChunk.length > MAX_CHUNK_BYTES) {
                    abort(requestId, capture);
                    return;
                }
                if (capture.assembler == null && chunkIndex != 0) {
                    abort(requestId, capture);
                    return;
                }
                if (capture.assembler == null) {
                    capture.assembler = new WebAeBinaryTransfer.SequentialAssembler(MAX_PNG_BYTES, MAX_TOTAL_CHUNKS);
                }
                byte[] full = capture.assembler.accept(chunkIndex, totalChunks, pngChunk);
                capture.lastTouchedMs = System.currentTimeMillis();
                if (full == null) {
                    return;
                }
                if (!WorldMapRenderSupport.isValidTilePng(full)) {
                    abort(requestId, capture);
                    return;
                }
                capture.png = full;
                capture.assembler = null;
                capture.latch.countDown();
            } catch (RuntimeException e) {
                abort(requestId, capture);
            }
        }
    }

    private void abort(String requestId, PendingCapture capture) {
        if (capture != null) {
            synchronized (capture) {
                capture.closed = true;
                pending.remove(requestId, capture);
                capture.png = null;
                capture.assembler = null;
                capture.latch.countDown();
            }
        }
    }

    private static final int MAX_PNG_BYTES = 1024 * 1024;
    private static final int MAX_CHUNK_BYTES = WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
    private static final int MAX_TOTAL_CHUNKS = (MAX_PNG_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
    private static final int MAX_ACTIVE_CAPTURES = 16;

    private void pruneExpired() {
        long cutoff = System.currentTimeMillis() - WebAeBinaryTransfer.SESSION_TTL_MS;
        for (java.util.Map.Entry<String, PendingCapture> entry : pending.entrySet()) {
            PendingCapture capture = entry.getValue();
            if (capture == null || capture.lastTouchedMs < cutoff) {
                abort(entry.getKey(), capture);
            }
        }
    }

    public static boolean isIntegratedSinglePlayer() {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        return server != null && server.isSinglePlayer();
    }

    private static final class PendingCapture {

        CountDownLatch latch;
        String playerUuid;
        String ownerUuid;
        String layer;
        int networkId;
        int dim;
        int chunkX;
        int chunkZ;
        int tilePx;
        byte[] png;
        WebAeBinaryTransfer.SequentialAssembler assembler;
        boolean closed;
        volatile long lastTouchedMs;
    }
}
