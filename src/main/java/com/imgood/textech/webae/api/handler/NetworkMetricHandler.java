package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.dto.NetworkMetricHistoryDto;
import com.imgood.textech.webae.metric.NetworkMetricSampler;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for the WebAE network metric history endpoint.
 *
 * <p>
 * {@code GET /api/network/metrics?network=<id>} — returns the rolling-window
 * scalar metric history (item/fluid/essentia counts, bytes usage, CPU busy ratio,
 * GT machine active count) sampled by {@link NetworkMetricSampler}.
 * </p>
 *
 * <p>
 * The handler also marks the (player, network) as active so the sampler keeps
 * collecting samples for it; idle records are evicted after 120s of no access.
 * </p>
 */
public class NetworkMetricHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    public static NanoHTTPD.Response handle(String uri, Map<String, String> params, String playerUuid) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        NetworkMetricSampler.getInstance()
            .markActive(playerUuid, networkId);
        NetworkMetricHistoryDto history = NetworkMetricSampler.getInstance()
            .getHistory(playerUuid, networkId);
        if (history == null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"history\":{\"networkId\":" + networkId + "}}");
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"history\":" + GSON.toJson(history) + "}");
    }

    private static Integer parseNetwork(Map<String, String> params) {
        String s = params.get("network");
        if (s == null || s.isEmpty()) return null;
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
