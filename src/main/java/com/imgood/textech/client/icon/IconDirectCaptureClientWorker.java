package com.imgood.textech.client.icon;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.icon.IconRenderGuard;
import com.imgood.textech.webae.icon.IconRenderer;
import com.imgood.textech.webae.network.PacketIconDirectCaptureRequest;
import com.imgood.textech.webae.network.PacketIconDirectCaptureResponse;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Processes direct icon capture requests on the client main thread.
 */
@SideOnly(Side.CLIENT)
public final class IconDirectCaptureClientWorker {

    private static final IconDirectCaptureClientWorker INSTANCE = new IconDirectCaptureClientWorker();
    private final Deque<PacketIconDirectCaptureRequest> queue = new ArrayDeque<PacketIconDirectCaptureRequest>();

    private IconDirectCaptureClientWorker() {}

    public static IconDirectCaptureClientWorker instance() {
        return INSTANCE;
    }

    public void enqueue(PacketIconDirectCaptureRequest request) {
        if (request == null || request.requestId == null || request.requestId.isEmpty()) {
            return;
        }
        synchronized (queue) {
            queue.offerLast(request);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (IconRenderer.instance().isRunning()) return;

        int budget = Config.webIconDirectRenderPerTick;
        if (budget <= 0) budget = 4;

        for (int i = 0; i < budget; i++) {
            PacketIconDirectCaptureRequest request;
            synchronized (queue) {
                request = queue.pollFirst();
            }
            if (request == null) break;
            processRequest(request);
        }
    }

    private static void processRequest(PacketIconDirectCaptureRequest request) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            sendResponse(request.requestId, null);
            return;
        }
        byte[] png = null;
        try {
            png = IconRenderer.instance().renderPngBytes(request.renderMode, request.itemId);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Direct icon capture failed for {}", request.itemId, t);
        } finally {
            IconRenderGuard.afterRender(mc);
        }
        sendResponse(request.requestId, png);
    }

    private static void sendResponse(String requestId, byte[] png) {
        PacketIconDirectCaptureResponse response = new PacketIconDirectCaptureResponse();
        response.requestId = requestId;
        response.success = png != null && png.length > 0;
        response.png = png != null ? png : new byte[0];
        AdvanceDataMonitor.ADMCHANEL.sendToServer(response);
    }
}
