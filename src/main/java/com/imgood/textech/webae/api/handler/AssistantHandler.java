package com.imgood.textech.webae.api.handler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.assistant.WebAssistantService;
import com.imgood.textech.webae.assistant.WebAssistantService.WebAssistantResult;

import fi.iki.elonen.NanoHTTPD;

/**
 * POST /api/assistant/query — Web text → server-side assistant rule parsing.
 */
public final class AssistantHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long TIMEOUT_MS = 20_000L;

    private AssistantHandler() {}

    public static NanoHTTPD.Response handle(String body, String ownerUuid) {
        String text = "";
        String locale = "zh_CN";
        if (body != null && !body.isEmpty()) {
            try {
                JsonObject json = new JsonParser().parse(body)
                    .getAsJsonObject();
                if (json.has("text")) {
                    text = json.get("text")
                        .getAsString();
                }
                if (json.has("locale")) {
                    locale = json.get("locale")
                        .getAsString();
                }
            } catch (Exception ignored) {}
        }

        final String finalText = text;
        final String finalLocale = locale;
        final WebAssistantResult[] holder = new WebAssistantResult[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = WebAssistantService.handleQuery(ownerUuid, finalText, finalLocale);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return json(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"success\":false,\"message\":\"Assistant query timed out\",\"code\":\"timeout\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"Interrupted\"}");
        }

        WebAssistantResult result = holder[0];
        if (result == null) {
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"No result\"}");
        }
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(result));
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
