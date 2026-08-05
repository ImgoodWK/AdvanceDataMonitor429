package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.Config;
import com.imgood.textech.webae.dto.NetworkMetricEntityHistoryDto;
import com.imgood.textech.webae.metric.NetworkMetricSampler;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET
 * /api/network/metrics/entities?network=&lt;id&gt;&amp;entities=cpu:Name,gt:0:1:2:3&amp;fields=craftingProgress,progressPercent
 * <p>
 * Entity keys: {@code cpu:&lt;name&gt;} or {@code gt:&lt;dim&gt;:&lt;x&gt;:&lt;y&gt;:&lt;z&gt;}.
 * Optional {@code fields} is parallel to {@code entities} (same length); omitted fields use defaults.
 * </p>
 */
public final class NetworkMetricEntityHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private NetworkMetricEntityHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String playerUuid) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        int maxPerRequest = Math.max(1, Config.webDashboardMaxTracksPerWidget);
        List<String> entityKeys = parseCsv(params.get("entities"), maxPerRequest);
        if (entityKeys.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'entities' parameter (comma-separated cpu:/gt: keys, max "
                    + maxPerRequest
                    + ")\"}");
        }
        List<String> fields = parseCsv(params.get("fields"), maxPerRequest);
        Map<String, String> fieldByEntity = new HashMap<String, String>();
        for (int i = 0; i < entityKeys.size(); i++) {
            String key = entityKeys.get(i);
            String field = i < fields.size() ? fields.get(i) : null;
            fieldByEntity.put(key, field);
        }
        NetworkMetricSampler sampler = NetworkMetricSampler.getInstance();
        sampler.markActive(playerUuid, networkId);
        String err = sampler.registerTrackedEntities(playerUuid, networkId, fieldByEntity);
        if (err != null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":" + GSON.toJson(err) + "}");
        }
        NetworkMetricEntityHistoryDto history = sampler.getEntityHistory(playerUuid, networkId);
        if (history == null) {
            history = new NetworkMetricEntityHistoryDto();
            history.networkId = networkId;
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"history\":" + GSON.toJson(history) + "}");
    }

    private static List<String> parseCsv(String raw, int max) {
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
