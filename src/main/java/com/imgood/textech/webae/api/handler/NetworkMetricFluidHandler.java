package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.Config;
import com.imgood.textech.webae.dto.NetworkMetricFluidHistoryDto;
import com.imgood.textech.webae.metric.NetworkMetricSampler;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/network/metrics/fluids?network=&lt;id&gt;&amp;fluids=water,lava — per-fluid trend.
 */
public final class NetworkMetricFluidHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private NetworkMetricFluidHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String playerUuid) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        int maxPerRequest = Math.max(1, Config.webDashboardMaxTracksPerWidget);
        List<String> fluidNames = parseFluids(params.get("fluids"), maxPerRequest);
        if (fluidNames.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'fluids' parameter (comma-separated, max " + maxPerRequest
                    + ")\"}");
        }
        NetworkMetricSampler sampler = NetworkMetricSampler.getInstance();
        sampler.markActive(playerUuid, networkId);
        String err = sampler.registerTrackedFluids(playerUuid, networkId, fluidNames);
        if (err != null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":" + GSON.toJson(err) + "}");
        }
        NetworkMetricFluidHistoryDto history = sampler.getFluidHistory(playerUuid, networkId);
        if (history == null) {
            history = new NetworkMetricFluidHistoryDto();
            history.networkId = networkId;
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"history\":" + GSON.toJson(history) + "}");
    }

    private static List<String> parseFluids(String raw, int max) {
        List<String> out = new ArrayList<String>();
        if (raw == null || raw.trim()
            .isEmpty()) {
            return out;
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length && out.size() < max; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    private static Integer parseNetwork(Map<String, String> params) {
        String s = params.get("network");
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
