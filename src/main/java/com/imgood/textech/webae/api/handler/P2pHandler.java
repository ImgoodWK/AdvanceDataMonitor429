package com.imgood.textech.webae.api.handler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.topology.P2pMapSnapshot;
import com.imgood.textech.webae.topology.P2pTunnelDto;
import com.imgood.textech.webae.topology.P2pTunnelEnumerator;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/network/p2p — P2P frequency map (Phase 10).
 */
public final class P2pHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long TIMEOUT_MS = 15_000L;

    private P2pHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        if (!Config.webTopologyEnabled) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"P2P map requires topology API enabled\"}");
        }
        String networkStr = params.get("network");
        if (networkStr == null || networkStr.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }
        final int networkId;
        try {
            networkId = Integer.parseInt(networkStr.trim());
        } catch (NumberFormatException e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
        }

        final List<P2pTunnelDto>[] holder = new List[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = P2pTunnelEnumerator.enumerate(ownerUuid, networkId);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] P2P map failed", t);
                    holder[0] = null;
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return json(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"success\":false,\"message\":\"P2P enumeration timed out\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"Interrupted\"}");
        }

        List<P2pTunnelDto> tunnels = holder[0];
        if (tunnels == null) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to enumerate P2P tunnels\"}");
        }
        P2pMapSnapshot snap = P2pMapSnapshot.fromTunnels(networkId, tunnels);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"data\":" + GSON.toJson(snap) + "}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
