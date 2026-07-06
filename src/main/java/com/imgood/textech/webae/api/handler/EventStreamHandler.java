package com.imgood.textech.webae.api.handler;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.events.EventStreamHub;
import com.imgood.textech.webae.events.EventStreamHub.Subscriber;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/events/stream — Server-Sent Events for alerts and heartbeats (Phase 9).
 */
public final class EventStreamHandler {

    private EventStreamHandler() {}

    public static NanoHTTPD.Response handle(String ownerUuid) {
        try {
            final Subscriber[] subHolder = new Subscriber[1];
            PipedInputStream in = new PipedInputStream(8192) {

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        if (subHolder[0] != null) {
                            EventStreamHub.instance()
                                .unregister(subHolder[0]);
                        }
                    }
                }
            };
            PipedOutputStream out = new PipedOutputStream(in);
            subHolder[0] = EventStreamHub.instance()
                .register(ownerUuid, out);
            NanoHTTPD.Response response = NanoHTTPD
                .newChunkedResponse(NanoHTTPD.Response.Status.OK, "text/event-stream; charset=utf-8", in);
            response.addHeader("Cache-Control", "no-cache");
            response.addHeader("Connection", "keep-alive");
            response.addHeader("X-Accel-Buffering", "no");
            return response;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] SSE stream open failed", e);
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"success\":false,\"message\":\"Failed to open event stream\"}");
        }
    }
}
