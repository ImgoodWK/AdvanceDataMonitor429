package com.imgood.textech.webae.api.handler;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.Config;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.topology.P2pMapSnapshot;
import com.imgood.textech.webae.topology.P2pTunnelDto;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/network/p2p — P2P frequency map (cache read; scheduler pre-collect).
 */
public final class P2pHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private P2pHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        if (!Config.webTopologyEnabled) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"P2P map requires topology API enabled\"}");
        }
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
                .invalidateType(ownerUuid, networkId, SnapshotScheduler.TYPE_P2P);
            SnapshotScheduler.forceCollectP2p(ownerUuid, networkId);
        }

        @SuppressWarnings("unchecked")
        List<P2pTunnelDto> fresh = SnapshotCache.instance()
            .get(ownerUuid, networkId, SnapshotScheduler.TYPE_P2P);
        long ts = SnapshotCache.instance()
            .timestampOf(ownerUuid, networkId, SnapshotScheduler.TYPE_P2P);
        if (fresh != null) {
            P2pMapSnapshot snap = P2pMapSnapshot.fromTunnels(networkId, fresh);
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(snap) + ",\"cached\":true,\"timestamp\":" + ts + "}");
        }
        @SuppressWarnings("unchecked")
        List<P2pTunnelDto> stale = SnapshotCache.instance()
            .getStale(ownerUuid, networkId, SnapshotScheduler.TYPE_P2P);
        if (stale != null) {
            P2pMapSnapshot snap = P2pMapSnapshot.fromTunnels(networkId, stale);
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"data\":" + GSON.toJson(snap) + ",\"cached\":false,\"timestamp\":" + ts + "}");
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"data\":"
                + GSON.toJson(P2pMapSnapshot.fromTunnels(networkId, java.util.Collections.<P2pTunnelDto>emptyList()))
                + ",\"cached\":false,\"timestamp\":0}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
