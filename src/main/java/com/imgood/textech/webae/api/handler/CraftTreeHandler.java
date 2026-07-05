package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.craft.CraftTreeCalculator;
import com.imgood.textech.webae.craft.CraftTreeNodeDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/craft/tree — material tree calculator (Phase 6).
 *
 * <p>Query: {@code item} (required), {@code amount} (default 1), {@code network} (default 0),
 * {@code maxDepth} (default 8, max 16).</p>
 */
public final class CraftTreeHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private CraftTreeHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        String item = params.get("item");
        if (item == null || item.trim()
            .isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'item' parameter\"}");
        }
        int networkId = parseInt(params.get("network"), 0);
        long amount = parseLong(params.get("amount"), 1L);
        int maxDepth = parseInt(params.get("maxDepth"), 8);

        CraftTreeNodeDto tree = CraftTreeCalculator.build(ownerUuid, networkId, item.trim(), amount, maxDepth);
        if (tree == null) {
            return json(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Could not build craft tree\"}");
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"networkId\":"
                + networkId
                + ",\"amount\":"
                + amount
                + ",\"tree\":"
                + GSON.toJson(tree)
                + "}");
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

    private static long parseLong(String raw, long defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
