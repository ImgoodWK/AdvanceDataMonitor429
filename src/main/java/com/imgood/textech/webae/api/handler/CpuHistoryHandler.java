package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.access.WebAeNetworkAccess;
import com.imgood.textech.webae.access.WebAeNetworkKeys;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.cpu.CpuHistoryResponseDto;
import com.imgood.textech.webae.cpu.CpuHistoryService;
import com.imgood.textech.webae.metric.NetworkMetricSampler;

import fi.iki.elonen.NanoHTTPD;

/**
 * Read-only CPU history endpoint. The request validates ACL metadata and
 * reads an already collected in-memory CPU history; it never resolves an AE
 * grid or performs a disk load.
 */
public final class CpuHistoryHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long DEFAULT_WINDOW_MS = 24L * 60L * 60L * 1000L;

    private CpuHistoryHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, WebAuthSession session, String ownerUuid) {
        Integer networkId = parseNetwork(params);
        if (networkId == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
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

        TimeRange range = parseRange(params);
        if (range == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid CPU history time range (maximum is 14 days)\"}");
        }
        Limit limit = parseLimit(params);
        if (limit == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'limit' parameter\"}");
        }

        // This only keeps the normal server-tick sampler active. It does not
        // sample immediately and cannot touch World, TileEntity, or AE2 Grid.
        NetworkMetricSampler.getInstance()
            .markActive(ownerUuid, networkId.intValue());
        CpuHistoryResponseDto response = CpuHistoryService.instance()
            .getHistory(ownerUuid, networkId.intValue(), networkKey, range.from, range.to, limit.value);
        if (limit.capped) {
            response.truncated = true;
        }
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(response));
    }

    static Integer parseNetwork(Map<String, String> params) {
        String raw = params == null ? null : params.get("network");
        if (raw == null || raw.trim()
            .isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 0 ? Integer.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static TimeRange parseRange(Map<String, String> params) {
        long now = System.currentTimeMillis();
        String rawFrom = params == null ? null : params.get("from");
        String rawTo = params == null ? null : params.get("to");
        Long to = parseEpoch(rawTo);
        if (rawTo != null && to == null) {
            return null;
        }
        long end = to != null ? to.longValue() : now;
        Long from = parseEpoch(rawFrom);
        if (rawFrom != null && from == null) {
            return null;
        }
        long start = from != null ? from.longValue() : Math.max(0L, end - DEFAULT_WINDOW_MS);
        if (start < 0L || end < 0L || start > end || end - start > CpuHistoryService.RETENTION_MS) {
            return null;
        }
        return new TimeRange(start, end);
    }

    private static Long parseEpoch(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value >= 0L ? Long.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Limit parseLimit(Map<String, String> params) {
        String raw = params == null ? null : params.get("limit");
        if (raw == null || raw.trim()
            .isEmpty()) {
            return new Limit(CpuHistoryService.MAX_HISTORY_JOBS_RESPONSE, false);
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed <= 0L) {
                return null;
            }
            boolean capped = parsed > CpuHistoryService.MAX_HISTORY_JOBS_RESPONSE;
            return new Limit((int) Math.min(CpuHistoryService.MAX_HISTORY_JOBS_RESPONSE, parsed), capped);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    static final class TimeRange {

        final long from;
        final long to;

        TimeRange(long from, long to) {
            this.from = from;
            this.to = to;
        }
    }

    private static final class Limit {

        final int value;
        final boolean capped;

        Limit(int value, boolean capped) {
            this.value = value;
            this.capped = capped;
        }
    }
}
