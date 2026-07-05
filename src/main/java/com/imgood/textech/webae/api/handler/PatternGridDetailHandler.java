package com.imgood.textech.webae.api.handler;

import java.net.URLDecoder;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.dto.PatternBrowseEntryDto;
import com.imgood.textech.webae.pattern.PatternBrowseService;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for {@code GET /api/patterns/grid/<gridKey>} — full grid pattern detail.
 */
public final class PatternGridDetailHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long MAIN_THREAD_TIMEOUT_MS = 10_000L;

    private PatternGridDetailHandler() {}

    public static NanoHTTPD.Response handle(String gridKeyPart, Map<String, String> params, String ownerUuid) {
        String networkStr = params.get("network");
        if (networkStr == null || networkStr.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }
        final int networkId;
        try {
            networkId = Integer.parseInt(networkStr);
        } catch (NumberFormatException e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
        }

        String gridKey = gridKeyPart;
        try {
            gridKey = URLDecoder.decode(gridKeyPart, "UTF-8");
        } catch (Exception ignored) {
            /* use raw */
        }

        final String fGridKey = gridKey;
        final PatternBrowseEntryDto[] holder = new PatternBrowseEntryDto[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = PatternBrowseService.getGridEntry(ownerUuid, networkId, fGridKey);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Pattern grid detail failed", t);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                if (holder[0] == null) {
                    return json(
                        NanoHTTPD.Response.Status.NOT_FOUND,
                        "{\"success\":false,\"message\":\"Grid pattern not found\"}");
                }
                return json(
                    NanoHTTPD.Response.Status.OK,
                    "{\"success\":true,\"entry\":" + GSON.toJson(holder[0]) + "}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return json(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "{\"success\":false,\"message\":\"Pattern grid detail timed out\"}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
