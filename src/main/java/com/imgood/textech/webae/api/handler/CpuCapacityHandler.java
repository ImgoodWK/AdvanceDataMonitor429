package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.access.WebAeNetworkAccess;
import com.imgood.textech.webae.access.WebAeNetworkKeys;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.cpu.CpuCapacityPlanDto;
import com.imgood.textech.webae.cpu.CpuHistoryService;
import com.imgood.textech.webae.metric.NetworkMetricSampler;

import fi.iki.elonen.NanoHTTPD;

/** Read-only capacity endpoint calculated from the CPU history memory cache. */
public final class CpuCapacityHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private CpuCapacityHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, WebAuthSession session, String ownerUuid) {
        Integer networkId = CpuHistoryHandler.parseNetwork(params);
        if (networkId == null) {
            return json(NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        NanoHTTPD.Response denied = WebAeNetworkAccess.assertCanAccess(session, ownerUuid, networkId.intValue());
        if (denied != null) {
            return denied;
        }
        String networkKey = WebAeNetworkKeys.fromNetworkId(ownerUuid, networkId.intValue());
        if (networkKey != null && !networkKey.isEmpty()) {
            denied = WebAeNetworkAccess.assertCanAccessKey(session, ownerUuid, networkKey);
            if (denied != null) {
                return denied;
            }
        }

        Window window = parseWindow(params == null ? null : params.get("window"));
        if (window == null) {
            return json(NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'window' parameter\"}");
        }

        NetworkMetricSampler.getInstance()
            .markActive(ownerUuid, networkId.intValue());
        long to = System.currentTimeMillis();
        long from = Math.max(0L, to - window.durationMs);
        CpuCapacityPlanDto response = CpuHistoryService.instance()
            .getCapacity(ownerUuid, networkId.intValue(), networkKey, window.name, from, to);
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(response));
    }

    static Window parseWindow(String raw) {
        String value = raw == null || raw.trim().isEmpty() ? "24h" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("1h".equals(value)) {
            return new Window(value, 60L * 60L * 1000L);
        }
        if ("6h".equals(value)) {
            return new Window(value, 6L * 60L * 60L * 1000L);
        }
        if ("24h".equals(value)) {
            return new Window(value, 24L * 60L * 60L * 1000L);
        }
        if ("7d".equals(value)) {
            return new Window(value, 7L * 24L * 60L * 60L * 1000L);
        }
        if ("14d".equals(value)) {
            return new Window(value, CpuHistoryService.RETENTION_MS);
        }
        return null;
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    static final class Window {

        final String name;
        final long durationMs;

        Window(String name, long durationMs) {
            this.name = name;
            this.durationMs = durationMs;
        }
    }
}
