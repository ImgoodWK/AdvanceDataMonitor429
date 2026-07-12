package com.imgood.textech.webae.api.handler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.scanner.LinkScannerBlockDto;
import com.imgood.textech.webae.scanner.LinkScannerCollector;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/scanner/blocks — read-only Link Scanner mirror (cache read + HTTP-thread filter).
 */
public final class ScannerHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private ScannerHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        SnapshotScheduler.markActive(ownerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID);
        final String typeFilter = params.get("type");
        final String query = params.get("q");

        @SuppressWarnings("unchecked")
        List<LinkScannerBlockDto> fresh = SnapshotCache.instance()
            .get(ownerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_SCANNER);
        long ts = SnapshotCache.instance()
            .timestampOf(ownerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_SCANNER);
        boolean cached = fresh != null;
        List<LinkScannerBlockDto> base = fresh;
        if (base == null) {
            @SuppressWarnings("unchecked")
            List<LinkScannerBlockDto> stale = SnapshotCache.instance()
                .getStale(ownerUuid, SnapshotScheduler.OWNER_SCOPE_NETWORK_ID, SnapshotScheduler.TYPE_SCANNER);
            base = stale;
        }
        if (base == null) {
            base = Collections.emptyList();
            ts = 0L;
        }
        List<LinkScannerBlockDto> blocks = LinkScannerCollector.filterCached(base, typeFilter, query);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":" + blocks.size()
                + ",\"blocks\":"
                + GSON.toJson(blocks)
                + ",\"cached\":"
                + cached
                + ",\"timestamp\":"
                + ts
                + "}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
