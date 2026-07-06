package com.imgood.textech.webae.api.handler;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.balance.NetworkBalanceCollector;
import com.imgood.textech.webae.balance.NetworkBalanceSuggestion;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/network/balance — read-only cross-network storage balance suggestions (Phase 8).
 */
public final class NetworkBalanceHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private NetworkBalanceHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        int[] networks = parseNetworks(params.get("networks"));
        if (networks == null || networks.length < 2) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Need at least two networks in 'networks' (comma-separated)\"}");
        }
        for (int i = 0; i < networks.length; i++) {
            SnapshotScheduler.markActive(ownerUuid, networks[i]);
        }

        long minSurplus = parseLong(params.get("minSurplus"), NetworkBalanceCollector.DEFAULT_MIN_SURPLUS);
        long minShortage = parseLong(params.get("minShortage"), NetworkBalanceCollector.DEFAULT_MIN_SHORTAGE);
        int limit = (int) parseLong(params.get("limit"), NetworkBalanceCollector.DEFAULT_LIMIT);

        List<NetworkBalanceSuggestion> suggestions = NetworkBalanceCollector
            .collect(ownerUuid, networks, minSurplus, minShortage, limit);

        long ts = 0L;
        for (int i = 0; i < networks.length; i++) {
            long t = SnapshotCache.instance()
                .timestampOf(ownerUuid, networks[i], "storage");
            if (t > ts) {
                ts = t;
            }
        }

        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":" + suggestions
                .size() + ",\"timestamp\":" + ts + ",\"suggestions\":" + GSON.toJson(suggestions) + "}");
    }

    private static int[] parseNetworks(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return null;
        }
        String[] parts = raw.split(",");
        int[] out = new int[parts.length];
        int n = 0;
        for (int i = 0; i < parts.length; i++) {
            try {
                out[n++] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {}
        }
        if (n < 2) {
            return null;
        }
        int[] trimmed = new int[n];
        for (int i = 0; i < n; i++) {
            trimmed[i] = out[i];
        }
        return trimmed;
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
