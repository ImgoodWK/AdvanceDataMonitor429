package com.imgood.textech.webae.icon;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketIconDirectCaptureRequest;

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
        pending.put(requestId, capture);

        PacketIconDirectCaptureRequest packet = new PacketIconDirectCaptureRequest();
        packet.requestId = requestId;
        packet.packName = pack;
        packet.renderMode = mode;
        packet.itemId = itemId;
        com.imgood.textech.AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);

        long waitMs = timeoutMs > 0 ? timeoutMs : Math.max(500L, Config.webIconDirectRenderTimeoutMs);
        try {
            if (!capture.latch.await(waitMs, TimeUnit.MILLISECONDS)) {
                pending.remove(requestId);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(requestId);
            return null;
        }
        pending.remove(requestId);
        return capture.png;
    }

    public void complete(String requestId, byte[] png) {
        if (requestId == null) return;
        PendingCapture capture = pending.get(requestId);
        if (capture == null) return;
        capture.png = png;
        capture.latch.countDown();
    }

    private static final class PendingCapture {

        CountDownLatch latch;
        byte[] png;
    }
}
