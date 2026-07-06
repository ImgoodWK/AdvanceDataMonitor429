package com.imgood.textech.webae.api.handler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.monitor.MonitorBindingCollector;
import com.imgood.textech.webae.monitor.MonitorBindingDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/monitor/bindings — read-only monitor Link/GT binding view.
 */
public final class MonitorHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long TIMEOUT_MS = 10_000L;

    private MonitorHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        final List<MonitorBindingDto>[] holder = new List[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = MonitorBindingCollector.collect(ownerUuid);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return json(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"success\":false,\"message\":\"Monitor binding scan timed out\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"Interrupted\"}");
        }

        List<MonitorBindingDto> monitors = holder[0] != null ? holder[0]
            : java.util.Collections.<MonitorBindingDto>emptyList();
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":" + monitors.size() + ",\"monitors\":" + GSON.toJson(monitors) + "}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
