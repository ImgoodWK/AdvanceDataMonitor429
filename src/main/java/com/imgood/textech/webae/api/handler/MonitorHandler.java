package com.imgood.textech.webae.api.handler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.monitor.MonitorBindingDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/monitor/bindings — read-only monitor Link/GT binding view (cache read).
 */
public final class MonitorHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private MonitorHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        SnapshotScheduler.markActive(ownerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID);

        @SuppressWarnings("unchecked")
        List<MonitorBindingDto> fresh = SnapshotCache.instance()
            .get(ownerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_MONITOR_BINDINGS);
        long ts = SnapshotCache.instance()
            .timestampOf(ownerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_MONITOR_BINDINGS);
        if (fresh != null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"count\":" + fresh.size()
                    + ",\"monitors\":"
                    + GSON.toJson(fresh)
                    + ",\"cached\":true,\"timestamp\":"
                    + ts
                    + "}");
        }
        @SuppressWarnings("unchecked")
        List<MonitorBindingDto> stale = SnapshotCache.instance()
            .getStale(ownerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_MONITOR_BINDINGS);
        if (stale != null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"count\":" + stale.size()
                    + ",\"monitors\":"
                    + GSON.toJson(stale)
                    + ",\"cached\":false,\"timestamp\":"
                    + ts
                    + "}");
        }
        List<MonitorBindingDto> empty = Collections.emptyList();
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":0,\"monitors\":" + GSON.toJson(empty)
                + ",\"cached\":false,\"timestamp\":0}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
