package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.cells.NetworkCellSummaryDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/network/cells — infinite cell / byte summary (cache read).
 */
public final class CellSummaryHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private CellSummaryHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        String networkStr = params.get("network");
        if (networkStr == null || networkStr.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }
        final int networkId;
        try {
            networkId = Integer.parseInt(networkStr.trim());
        } catch (NumberFormatException e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
        }

        SnapshotScheduler.markActive(ownerUuid, networkId);
        boolean force = "1".equals(params.get("refresh")) || "true".equalsIgnoreCase(params.get("refresh"));
        if (force) {
            SnapshotCache.instance()
                .invalidateType(ownerUuid, networkId, SnapshotScheduler.TYPE_CELLS);
            SnapshotScheduler.forceCollectCells(ownerUuid, networkId);
        }

        NetworkCellSummaryDto fresh = SnapshotCache.instance()
            .get(ownerUuid, networkId, SnapshotScheduler.TYPE_CELLS);
        long ts = SnapshotCache.instance()
            .timestampOf(ownerUuid, networkId, SnapshotScheduler.TYPE_CELLS);
        if (fresh != null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(fresh) + ",\"cached\":true,\"timestamp\":" + ts + "}");
        }
        NetworkCellSummaryDto stale = SnapshotCache.instance()
            .getStale(ownerUuid, networkId, SnapshotScheduler.TYPE_CELLS);
        if (stale != null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(stale) + ",\"cached\":false,\"timestamp\":" + ts + "}");
        }
        NetworkCellSummaryDto empty = new NetworkCellSummaryDto();
        empty.networkId = networkId;
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"data\":" + GSON.toJson(empty) + ",\"cached\":false,\"timestamp\":0}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
