package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.dto.GtMachineListDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for GT machine status.
 *
 * GET /api/gt/machines?network=&lt;id&gt; — cache-only read (stale fallback)
 * GET /api/gt/machines/batch?networks=0,1,2 — batched cache read
 * POST /api/gt/machines/refresh?network=&lt;id&gt; — admin-only force re-collect
 * POST /api/gt/machines/refresh/batch?networks=0,1,2 — admin-only batched force re-collect
 */
public class GtMachineHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final String DATA_TYPE = "gt_machines";

    public static NanoHTTPD.Response handle(String uri, Map<String, String> params, WebAuthSession auth,
        String adminHeader) {
        String ownerUuid = auth.ownerUuid;
        if ("/api/gt/machines".equals(uri)) {
            return handleMachines(params, ownerUuid);
        }
        if ("/api/gt/machines/batch".equals(uri)) {
            return handleMachinesBatch(params, ownerUuid);
        }
        if ("/api/gt/machines/refresh".equals(uri)) {
            return handleRefresh(params, auth, adminHeader);
        }
        if ("/api/gt/machines/refresh/batch".equals(uri)) {
            return handleRefreshBatch(params, auth, adminHeader);
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "application/json",
            "{\"success\":false,\"message\":\"Unknown GT endpoint\"}");
    }

    private static NanoHTTPD.Response handleMachines(Map<String, String> params, String playerUuid) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        SnapshotScheduler.markActive(playerUuid, networkId);
        return buildGtResponse(playerUuid, networkId);
    }

    private static NanoHTTPD.Response buildGtResponse(String playerUuid, int networkId) {
        GtMachineListDto cached = SnapshotCache.instance()
            .get(playerUuid, networkId, DATA_TYPE);
        if (cached != null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(cached)
                    + ",\"cached\":true,\"timestamp\":"
                    + cached.timestamp
                    + "}");
        }
        GtMachineListDto stale = SnapshotCache.instance()
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

    private static NanoHTTPD.Response handleMachinesBatch(Map<String, String> params, String playerUuid) {
        int[] networks = parseNetworks(params);
        if (networks == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'networks' parameter\"}");
        }
        List<String> results = new ArrayList<String>();
        for (int networkId : networks) {
            SnapshotScheduler.markActive(playerUuid, networkId);
            GtMachineListDto cached = SnapshotCache.instance()
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
                GtMachineListDto stale = SnapshotCache.instance()
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

    private static NanoHTTPD.Response handleRefresh(Map<String, String> params, WebAuthSession auth,
        String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return jsonResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"GT refresh is admin-only.\"}");
        }
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        String ownerUuid = auth.ownerUuid;
        SnapshotCache.instance()
            .invalidateType(ownerUuid, networkId, DATA_TYPE);
        SnapshotScheduler.markActive(ownerUuid, networkId);
        SnapshotScheduler.forceCollectGt(ownerUuid, networkId);
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"refreshed\":true,\"network\":" + networkId + "}");
    }

    private static NanoHTTPD.Response handleRefreshBatch(Map<String, String> params, WebAuthSession auth,
        String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return jsonResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Batch GT refresh is admin-only.\"}");
        }
        int[] networks = parseNetworks(params);
        if (networks == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'networks' parameter\"}");
        }
        String ownerUuid = auth.ownerUuid;
        for (int networkId : networks) {
            SnapshotCache.instance()
                .invalidateType(ownerUuid, networkId, DATA_TYPE);
            SnapshotScheduler.markActive(ownerUuid, networkId);
            SnapshotScheduler.forceCollectGt(ownerUuid, networkId);
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
