package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.Config;
import com.imgood.textech.webae.auth.WebAuthOpCheck;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.pattern.PatternBrowseService;
import com.imgood.textech.webae.pattern.PatternBrowseService.BrowseResult;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for pattern browse — cache-only reads with background pre-collection.
 *
 * <ul>
 * <li>GET /api/patterns/browse — paginated Grid + Interface merge (no blocking main-thread collect)</li>
 * <li>POST /api/patterns/browse/refresh — admin-only force cache rebuild</li>
 * </ul>
 */
public final class PatternBrowseHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private PatternBrowseHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        String networkStr = params.get("network");
        if (networkStr == null || networkStr.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }
        final int networkId;
        try {
            networkId = Integer.parseInt(networkStr);
        } catch (NumberFormatException e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
        }

        final String query = params.get("q") != null ? params.get("q") : "";
        final String source = params.get("source") != null ? params.get("source") : "both";
        int offset = 0;
        int limit = Config.webPatternBrowsePageSize;
        try {
            if (params.get("offset") != null) {
                offset = Integer.parseInt(params.get("offset"));
            }
            if (params.get("limit") != null) {
                limit = Integer.parseInt(params.get("limit"));
            }
        } catch (NumberFormatException e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid offset/limit\"}");
        }

        SnapshotScheduler.markActive(ownerUuid, networkId);
        return buildBrowseResponse(ownerUuid, networkId, query, offset, limit, source);
    }

    public static NanoHTTPD.Response handleRefresh(Map<String, String> params, String ownerUuid) {
        if (!WebAuthOpCheck.isOp(ownerUuid)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Pattern browse refresh is admin-only. Use /admweb refresh in-game.\"}");
        }
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid 'network' parameter\"}");
        }
        PatternBrowseService.invalidateCache(ownerUuid, networkId);
        SnapshotScheduler.markActive(ownerUuid, networkId);
        SnapshotScheduler.forceCollectPatternBrowse(ownerUuid, networkId);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"refreshed\":true,\"network\":" + networkId + "}");
    }

    private static NanoHTTPD.Response buildBrowseResponse(String ownerUuid, int networkId, String query, int offset,
        int limit, String source) {
        boolean fresh = PatternBrowseService.isFresh(ownerUuid, networkId);
        BrowseResult r = PatternBrowseService.browse(ownerUuid, networkId, query, offset, limit, source);
        long ts = PatternBrowseService.timestampOf(ownerUuid, networkId);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true");
        sb.append(",\"entries\":")
            .append(GSON.toJson(r.entries));
        sb.append(",\"total\":")
            .append(r.total);
        sb.append(",\"offset\":")
            .append(r.offset);
        sb.append(",\"limit\":")
            .append(r.limit);
        sb.append(",\"truncated\":")
            .append(r.truncated);
        sb.append(",\"sources\":{\"grid\":")
            .append(r.gridCount);
        sb.append(",\"interface\":")
            .append(r.interfaceCount);
        sb.append("},\"cached\":")
            .append(fresh);
        sb.append(",\"timestamp\":")
            .append(ts);
        sb.append("}");
        return json(NanoHTTPD.Response.Status.OK, sb.toString());
    }

    private static Integer parseNetwork(Map<String, String> params) {
        String s = params.get("network");
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
