package com.imgood.textech.webae.api.handler;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.cells.NetworkCellSummaryCollector;
import com.imgood.textech.webae.cells.NetworkCellSummaryDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/network/cells — infinite cell / byte summary for a network.
 */
public final class CellSummaryHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long TIMEOUT_MS = 15_000L;

    private CellSummaryHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
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

        final NetworkCellSummaryDto[] holder = new NetworkCellSummaryDto[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = NetworkCellSummaryCollector.collect(ownerUuid, networkId);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return json(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"success\":false,\"message\":\"Cell summary timed out\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"Interrupted\"}");
        }

        NetworkCellSummaryDto data = holder[0] != null ? holder[0] : new NetworkCellSummaryDto();
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"data\":" + GSON.toJson(data) + "}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
