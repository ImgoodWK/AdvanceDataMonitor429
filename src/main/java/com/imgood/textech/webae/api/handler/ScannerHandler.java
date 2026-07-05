package com.imgood.textech.webae.api.handler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.scanner.LinkScannerBlockDto;
import com.imgood.textech.webae.scanner.LinkScannerCollector;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/scanner/blocks — read-only Link Scanner mirror (loaded chunks only).
 */
public final class ScannerHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long TIMEOUT_MS = 10_000L;

    private ScannerHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        final String typeFilter = params.get("type");
        final String query = params.get("q");
        final List<LinkScannerBlockDto>[] holder = new List[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = LinkScannerCollector.collect(ownerUuid, typeFilter, query);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return json(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"success\":false,\"message\":\"Scanner enumeration timed out\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"Interrupted\"}");
        }

        List<LinkScannerBlockDto> blocks = holder[0] != null ? holder[0] : java.util.Collections.<LinkScannerBlockDto>emptyList();
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":"
                + blocks.size()
                + ",\"blocks\":"
                + GSON.toJson(blocks)
                + "}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
