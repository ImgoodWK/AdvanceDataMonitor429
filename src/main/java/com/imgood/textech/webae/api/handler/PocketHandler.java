package com.imgood.textech.webae.api.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.pocket.PocketOverviewCollector;
import com.imgood.textech.webae.pocket.PocketOverviewDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/pocket/overview — OP-only read-only pocket stats (I6 minimal privacy-safe view).
 */
public final class PocketHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private PocketHandler() {}

    public static NanoHTTPD.Response handle(WebAuthSession auth, String adminHeader) {
        PocketOverviewDto dto = PocketOverviewCollector.collect(auth, adminHeader);
        if (!dto.available && dto.opRequired) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"op_required\",\"message\":" + GSON.toJson(dto.message)
                    + ",\"data\":"
                    + GSON.toJson(dto)
                    + "}");
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"data\":" + GSON.toJson(dto) + "}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
