package com.imgood.textech.webae.api.handler;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.monitor.MonitorPreviewCollector;
import com.imgood.textech.webae.monitor.MonitorPreviewDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/monitor/preview — read-only chart data for one monitor slot (Phase 11).
 *
 * <p>
 * Query: {@code dim}, {@code x}, {@code y}, {@code z}, {@code slot} (all required).
 * </p>
 */
public final class MonitorPreviewHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long TIMEOUT_MS = 10_000L;

    private MonitorPreviewHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        int dim = parseInt(params.get("dim"), -1);
        int x = parseInt(params.get("x"), Integer.MIN_VALUE);
        int y = parseInt(params.get("y"), Integer.MIN_VALUE);
        int z = parseInt(params.get("z"), Integer.MIN_VALUE);
        int slot = parseInt(params.get("slot"), -1);
        if (dim < 0 || x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE || slot < 0) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing dim/x/y/z/slot parameters\"}");
        }

        final MonitorPreviewDto[] holder = new MonitorPreviewDto[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = MonitorPreviewCollector.collect(ownerUuid, dim, x, y, z, slot);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return json(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"success\":false,\"message\":\"Monitor preview timed out\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"Interrupted\"}");
        }

        if (holder[0] == null) {
            return json(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Monitor or slot not found\"}");
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"preview\":" + GSON.toJson(holder[0]) + "}");
    }

    private static int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
