package com.imgood.textech.webae.icon;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketIconDirectCaptureRequest;
import com.imgood.textech.webae.network.WebAeBinaryTransfer;

/**
 * Bridges HTTP icon 404 requests to client GL render via packets (integrated SP / MP with online provider).
 */
public final class IconDirectCaptureBridge {

    private static final IconDirectCaptureBridge INSTANCE = new IconDirectCaptureBridge();
    private static final AtomicInteger REQUEST_SEQ = new AtomicInteger();

    private final ConcurrentHashMap<String, PendingCapture> pending = new ConcurrentHashMap<String, PendingCapture>();

    private IconDirectCaptureBridge() {}

    public static IconDirectCaptureBridge instance() {
        return INSTANCE;
    }

    public byte[] requestRender(String pack, String mode, String itemId, long timeoutMs) {
        if (!Config.webIconDirectRenderEnabled) return null;
        if (itemId == null || itemId.isEmpty()) return null;
        if (pack == null || pack.isEmpty()) pack = "default";
        // Active path is nei-only; ignore requested mode.
        mode = IconRenderMode.NEI.getId();

        EntityPlayerMP player = IconMissingQueue.instance()
            .resolveProviderPlayer();
        if (player == null) return null;

        String requestId = "icdc-" + System.currentTimeMillis() + "-" + REQUEST_SEQ.incrementAndGet();
        PendingCapture capture = new PendingCapture();
        capture.latch = new CountDownLatch(1);
        capture.playerUuid = player.getUniqueID()
            .toString();
        capture.lastTouchedMs = System.currentTimeMillis();
        synchronized (pending) {
            pruneExpired();
            if (pending.size() >= MAX_ACTIVE_CAPTURES) {
                return null;
            }
            pending.put(requestId, capture);
        }

        PacketIconDirectCaptureRequest packet = new PacketIconDirectCaptureRequest();
        packet.requestId = requestId;
        packet.packName = pack;
        packet.renderMode = mode;
        packet.itemId = itemId;
        com.imgood.textech.AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);

        long waitMs = timeoutMs > 0 ? timeoutMs : Math.max(500L, Config.webIconDirectRenderTimeoutMs);
        try {
            if (!capture.latch.await(waitMs, TimeUnit.MILLISECONDS)) {
                pending.remove(requestId, capture);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            pending.remove(requestId, capture);
            return null;
        }
        pending.remove(requestId);
        return capture.png;
    }

    public void complete(String requestId, String playerUuid, boolean success, int chunkIndex, int totalChunks,
        byte[] pngChunk) {
        if (requestId == null || playerUuid == null) return;
        PendingCapture capture = pending.get(requestId);
        if (capture == null || capture.playerUuid == null || !capture.playerUuid.equals(playerUuid)) return;
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
            capture.png = full;
            capture.latch.countDown();
        } catch (RuntimeException e) {
            abort(requestId, capture);
        }
    }

    private void abort(String requestId, PendingCapture capture) {
        if (capture != null) {
            pending.remove(requestId, capture);
            capture.png = null;
            capture.latch.countDown();
        }
    }

    private static final int MAX_PNG_BYTES = 256 * 1024;
    private static final int MAX_CHUNK_BYTES = WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES;
    private static final int MAX_TOTAL_CHUNKS = (MAX_PNG_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
    private static final int MAX_ACTIVE_CAPTURES = 32;

    private void pruneExpired() {
        long cutoff = System.currentTimeMillis() - WebAeBinaryTransfer.SESSION_TTL_MS;
        for (java.util.Map.Entry<String, PendingCapture> entry : pending.entrySet()) {
            PendingCapture capture = entry.getValue();
            if (capture == null || capture.lastTouchedMs < cutoff) {
                if (pending.remove(entry.getKey(), capture) && capture != null) {
                    capture.png = null;
                    capture.latch.countDown();
                }
            }
        }
    }

    private static final class PendingCapture {

        CountDownLatch latch;
        String playerUuid;
        byte[] png;
        WebAeBinaryTransfer.SequentialAssembler assembler;
        volatile long lastTouchedMs;
    }
}
