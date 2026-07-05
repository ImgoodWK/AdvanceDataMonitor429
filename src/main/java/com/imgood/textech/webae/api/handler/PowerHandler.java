package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.auth.WebAuthOpCheck;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.dto.PowerDto;
import com.imgood.textech.webae.power.PowerSampler;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for wireless power / steam snapshots.
 *
 * GET /api/power?network=&lt;id&gt; — cache-only read (stale fallback)
 * GET /api/power/batch?networks=0,1,2 — batched cache read
 * POST /api/power/refresh?network=&lt;id&gt; — admin-only force re-sample
 * POST /api/power/refresh/batch?networks=0,1,2 — admin-only batched force re-sample
 */
public class PowerHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final String DATA_TYPE = "power";

    public static NanoHTTPD.Response handle(String uri, Map<String, String> params, String playerUuid) {
        if ("/api/power".equals(uri)) {
            return handlePower(params, playerUuid);
        }
        if ("/api/power/batch".equals(uri)) {
            return handlePowerBatch(params, playerUuid);
        }
        if ("/api/power/refresh".equals(uri)) {
            return handlePowerRefresh(params, playerUuid);
        }
        if ("/api/power/refresh/batch".equals(uri)) {
            return handlePowerRefreshBatch(params, playerUuid);
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "application/json",
            "{\"success\":false,\"message\":\"Unknown power endpoint\"}");
    }

    private static NanoHTTPD.Response handlePower(Map<String, String> params, String playerUuid) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        SnapshotScheduler.markActive(playerUuid, networkId);
        PowerSampler.getInstance()
            .markActive(playerUuid, networkId);
        return buildPowerResponse(playerUuid, networkId);
    }

    private static NanoHTTPD.Response buildPowerResponse(String playerUuid, int networkId) {
        PowerDto cached = SnapshotCache.instance()
            .get(playerUuid, networkId, DATA_TYPE);
        if (cached != null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(cached)
                    + ",\"cached\":true,\"timestamp\":"
                    + cached.timestamp
                    + "}");
        }
        PowerDto stale = SnapshotCache.instance()
            .getStale(playerUuid, networkId, DATA_TYPE);
        long ts = SnapshotCache.instance()
            .timestampOf(playerUuid, networkId, DATA_TYPE);
        if (stale != null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(stale) + ",\"cached\":false,\"timestamp\":" + ts + "}");
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"data\":null,\"cached\":false,\"timestamp\":0}");
    }

    private static NanoHTTPD.Response handlePowerBatch(Map<String, String> params, String playerUuid) {
        int[] networks = parseNetworks(params);
        if (networks == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'networks' parameter\"}");
        }
        List<String> results = new ArrayList<String>();
        for (int networkId : networks) {
            SnapshotScheduler.markActive(playerUuid, networkId);
            PowerSampler.getInstance()
                .markActive(playerUuid, networkId);
            PowerDto cached = SnapshotCache.instance()
                .get(playerUuid, networkId, DATA_TYPE);
            if (cached != null) {
                results.add(
                    "{\"networkId\":" + networkId
                        + ",\"data\":"
                        + GSON.toJson(cached)
                        + ",\"cached\":true,\"timestamp\":"
                        + cached.timestamp
                        + "}");
            } else {
                PowerDto stale = SnapshotCache.instance()
                    .getStale(playerUuid, networkId, DATA_TYPE);
                long ts = SnapshotCache.instance()
                    .timestampOf(playerUuid, networkId, DATA_TYPE);
                results.add(
                    "{\"networkId\":" + networkId
                        + ",\"data\":"
                        + GSON.toJson(stale)
                        + ",\"cached\":false,\"timestamp\":"
                        + ts
                        + "}");
            }
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"results\":[" + join(results) + "]}");
    }

    private static NanoHTTPD.Response handlePowerRefresh(Map<String, String> params, String playerUuid) {
        if (!WebAuthOpCheck.isOp(playerUuid)) {
            return jsonResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Power refresh is admin-only.\"}");
        }
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        SnapshotCache.instance()
            .invalidateType(playerUuid, networkId, DATA_TYPE);
        SnapshotScheduler.markActive(playerUuid, networkId);
        PowerSampler.getInstance()
            .markActive(playerUuid, networkId);
        // Power sampler runs on its own 5s tick; admin refresh just clears cache
        // and lets the next sampler tick repopulate it.
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"refreshed\":true,\"network\":" + networkId + "}");
    }

    private static NanoHTTPD.Response handlePowerRefreshBatch(Map<String, String> params, String playerUuid) {
        if (!WebAuthOpCheck.isOp(playerUuid)) {
            return jsonResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Batch power refresh is admin-only.\"}");
        }
        int[] networks = parseNetworks(params);
        if (networks == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'networks' parameter\"}");
        }
        for (int networkId : networks) {
            SnapshotCache.instance()
                .invalidateType(playerUuid, networkId, DATA_TYPE);
            SnapshotScheduler.markActive(playerUuid, networkId);
            PowerSampler.getInstance()
                .markActive(playerUuid, networkId);
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"refreshed\":true,\"networks\":[" + joinInts(networks) + "]}");
    }

    private static Integer parseNetwork(Map<String, String> params) {
        String s = params.get("network");
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int[] parseNetworks(Map<String, String> params) {
        String s = params.get("networks");
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String joinInts(int[] ints) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ints.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ints[i]);
        }
        return sb.toString();
    }

    private static NanoHTTPD.Response jsonResponse(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
