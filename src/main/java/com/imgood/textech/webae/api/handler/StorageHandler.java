package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.access.WebAeNetworkAccess;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for AE storage snapshots.
 *
 * GET /api/storage?network=&lt;id&gt; — read cache only (stale fallback)
 * GET /api/storage/batch?networks=0,1,2 — batched cache read
 * POST /api/refresh?network=&lt;id&gt; — admin-only force re-collect
 * POST /api/refresh/batch?networks=0,1,2 — admin-only batched force re-collect
 * GET /api/networks — list available networks
 */
public class StorageHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long COLLECT_TIMEOUT_MS = 10_000L;

    public static NanoHTTPD.Response handle(String uri, Map<String, String> params, WebAuthSession auth,
        String adminHeader, String effectiveOwner) {
        String ownerUuid = effectiveOwner != null && !effectiveOwner.isEmpty() ? effectiveOwner : auth.ownerUuid;
        if ("/api/storage".equals(uri)) {
            return handleStorage(params, ownerUuid);
        }
        if ("/api/storage/batch".equals(uri)) {
            return handleStorageBatch(params, ownerUuid);
        }
        if ("/api/refresh".equals(uri)) {
            return handleRefresh(params, auth, adminHeader);
        }
        if ("/api/refresh/batch".equals(uri)) {
            return handleRefreshBatch(params, auth, adminHeader);
        }
        if ("/api/networks".equals(uri)) {
            return handleNetworks(ownerUuid, params, auth);
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "application/json",
            "{\"success\":false,\"message\":\"Unknown endpoint\"}");
    }

    private static NanoHTTPD.Response handleStorage(Map<String, String> params, String playerUuid) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        SnapshotScheduler.markActive(playerUuid, networkId);
        return buildStorageResponse(playerUuid, networkId);
    }

    private static NanoHTTPD.Response buildStorageResponse(String playerUuid, int networkId) {
        StorageDto cached = SnapshotCache.instance()
            .get(playerUuid, networkId, "storage");
        if (cached != null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(cached)
                    + ",\"cached\":true,\"timestamp\":"
                    + cached.timestamp
                    + "}");
        }
        // Stale fallback: return last snapshot (if any) with cached:false so the
        // frontend can render something while the scheduler catches up. Never
        // trigger a blocking collect from the HTTP thread.
        StorageDto stale = SnapshotCache.instance()
            .getStale(playerUuid, networkId, "storage");
        long ts = SnapshotCache.instance()
            .timestampOf(playerUuid, networkId, "storage");
        if (stale != null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(stale) + ",\"cached\":false,\"timestamp\":" + ts + "}");
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"data\":null,\"cached\":false,\"timestamp\":0}");
    }

    private static NanoHTTPD.Response handleStorageBatch(Map<String, String> params, String playerUuid) {
        int[] networks = parseNetworks(params);
        if (networks == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'networks' parameter (comma-separated)\"}");
        }
        List<String> results = new ArrayList<String>();
        for (int networkId : networks) {
            SnapshotScheduler.markActive(playerUuid, networkId);
            StorageDto cached = SnapshotCache.instance()
                .get(playerUuid, networkId, "storage");
            if (cached != null) {
                results.add(
                    "{\"networkId\":" + networkId
                        + ",\"data\":"
                        + GSON.toJson(cached)
                        + ",\"cached\":true,\"timestamp\":"
                        + cached.timestamp
                        + "}");
            } else {
                StorageDto stale = SnapshotCache.instance()
                    .getStale(playerUuid, networkId, "storage");
                long ts = SnapshotCache.instance()
                    .timestampOf(playerUuid, networkId, "storage");
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
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Refresh is admin-only. Use /admweb refresh in-game.\"}");
        }
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        String ownerUuid = auth.ownerUuid;
        SnapshotCache.instance()
            .invalidateAll(ownerUuid, networkId);
        AeSnapshotCollector.invalidateConnectors(ownerUuid);
        SnapshotScheduler.markActive(ownerUuid, networkId);
        SnapshotScheduler.forceCollectStorage(ownerUuid, networkId);
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"refreshed\":true,\"network\":" + networkId + "}");
    }

    private static NanoHTTPD.Response handleRefreshBatch(Map<String, String> params, WebAuthSession auth,
        String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return jsonResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Batch refresh is admin-only.\"}");
        }
        int[] networks = parseNetworks(params);
        if (networks == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'networks' parameter\"}");
        }
        String ownerUuid = auth.ownerUuid;
        AeSnapshotCollector.invalidateConnectors(ownerUuid);
        for (int networkId : networks) {
            SnapshotCache.instance()
                .invalidateAll(ownerUuid, networkId);
            SnapshotScheduler.markActive(ownerUuid, networkId);
            SnapshotScheduler.forceCollectStorage(ownerUuid, networkId);
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"refreshed\":true,\"networks\":[" + joinInts(networks) + "]}");
    }

    private static NanoHTTPD.Response handleNetworks(String playerUuid, Map<String, String> params,
        WebAuthSession auth) {
        SnapshotScheduler.markActive(playerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID);
        boolean forceRefresh = params != null
            && ("1".equals(params.get("refresh")) || "true".equalsIgnoreCase(params.get("refresh")));
        if (forceRefresh) {
            AeSnapshotCollector.invalidateConnectors(playerUuid);
            SnapshotCache.instance()
                .invalidateType(playerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_NETWORKS);
            SnapshotScheduler.forceCollectNetworks(playerUuid);
        }

        @SuppressWarnings("unchecked")
        List<NetworkInfo> fresh = SnapshotCache.instance()
            .get(playerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_NETWORKS);
        long ts = SnapshotCache.instance()
            .timestampOf(playerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_NETWORKS);
        if (fresh != null) {
            List<NetworkInfo> visible = WebAeNetworkAccess.filterVisible(auth, playerUuid, fresh);
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"networks\":" + GSON.toJson(visible)
                    + ",\"cached\":true,\"timestamp\":"
                    + ts
                    + "}");
        }
        @SuppressWarnings("unchecked")
        List<NetworkInfo> stale = SnapshotCache.instance()
            .getStale(playerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_NETWORKS);
        if (stale != null) {
            List<NetworkInfo> visible = WebAeNetworkAccess.filterVisible(auth, playerUuid, stale);
            return jsonResponse(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"networks\":" + GSON.toJson(visible)
                    + ",\"cached\":false,\"timestamp\":"
                    + ts
                    + "}");
        }
        // Kick async collect; do not block HTTP on main-thread findNetworks
        SnapshotScheduler.forceCollectNetworks(playerUuid);
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"networks\":[],\"cached\":false,\"timestamp\":0}");
    }

    // ---- helpers ----

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
