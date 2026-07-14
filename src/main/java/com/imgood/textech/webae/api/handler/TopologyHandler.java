package com.imgood.textech.webae.api.handler;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.topology.TopologyCache;
import com.imgood.textech.webae.topology.TopologyCache.CachedResult;
import com.imgood.textech.webae.topology.TopologyCache.CaptureResult;
import com.imgood.textech.webae.topology.TopologySnapshot;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for {@code GET /api/network/topology} (read last manual snapshot) and
 * {@code POST /api/network/topology/snapshot} (capture on server thread with cooldown).
 */
public final class TopologyHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long MAIN_THREAD_TIMEOUT_MS = 15_000L;

    private TopologyHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, WebAuthSession auth, String adminHeader) {
        String ownerUuid = auth.ownerUuid;
        if (!Config.webTopologyEnabled) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"Network topology API is disabled\",\"code\":\"topology_disabled\"}");
        }

        final int networkId = parseNetworkId(params);
        if (networkId < 0) {
            return parseNetworkError(params);
        }
        final String mode = params.get("mode") != null ? params.get("mode") : "logical";

        CachedResult warm = TopologyCache.instance()
            .getCached(ownerUuid, networkId, mode);
        long cooldownRemainingMs = TopologyCache.instance()
            .remainingCooldownMs(ownerUuid, networkId);
        boolean canForceSnapshot = WebAuthAdminCheck.isAdmin(auth, adminHeader);
        if (warm != null) {
            return buildSuccess(
                warm.snapshot,
                true,
                warm.timestamp,
                cooldownRemainingMs,
                warm.persisted,
                canForceSnapshot);
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"hasSnapshot\":false,\"cached\":false,\"canForceSnapshot\":" + canForceSnapshot
                + ",\"cooldownRemainingMs\":"
                + cooldownRemainingMs
                + ",\"cooldownMs\":"
                + Config.webTopologyCacheTtlMs
                + ",\"message\":\"No topology snapshot yet. Capture one manually.\"}");
    }

    public static NanoHTTPD.Response handleSnapshot(Map<String, String> params, WebAuthSession auth,
        String adminHeader) {
        String ownerUuid = auth.ownerUuid;
        if (!Config.webTopologyEnabled) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"Network topology API is disabled\",\"code\":\"topology_disabled\"}");
        }

        final int networkId = parseNetworkId(params);
        if (networkId < 0) {
            return parseNetworkError(params);
        }
        final String mode = params.get("mode") != null ? params.get("mode") : "logical";
        final boolean force = "1".equals(params.get("force")) || "true".equalsIgnoreCase(params.get("force"));

        if (force && !WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"forbidden\",\"message\":\"Admin permission required for force refresh\"}");
        }

        final CaptureResult[] holder = new CaptureResult[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = TopologyCache.instance()
                        .captureSnapshot(ownerUuid, networkId, mode, force);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG
                        .error("[WebAE] Topology snapshot failed owner={} network={}", ownerUuid, networkId, t);
                    holder[0] = null;
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return json(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "{\"success\":false,\"message\":\"Topology snapshot timed out\"}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return json(NanoHTTPD.Response.Status.INTERNAL_ERROR, "{\"success\":false,\"message\":\"Interrupted\"}");
        }

        CaptureResult result = holder[0];
        if (result == null) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to capture topology snapshot\"}");
        }
        if (result.disabled) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"Network topology API is disabled\",\"code\":\"topology_disabled\"}");
        }
        boolean canForceSnapshot = WebAuthAdminCheck.isAdmin(auth, adminHeader);
        if (result.cooldown) {
            long remaining = TopologyCache.instance()
                .remainingCooldownMs(ownerUuid, networkId);
            if (result.snapshot != null) {
                return buildSuccess(result.snapshot, true, result.timestamp, remaining, true, canForceSnapshot);
            }
            return json(
                NanoHTTPD.Response.Status.TOO_MANY_REQUESTS,
                "{\"success\":false,\"code\":\"cooldown\",\"cooldownRemainingMs\":" + remaining
                    + ",\"cooldownMs\":"
                    + Config.webTopologyCacheTtlMs
                    + ",\"message\":\"Topology snapshot cooldown active\"}");
        }
        return buildSuccess(
            result.snapshot,
            false,
            result.timestamp,
            TopologyCache.instance()
                .remainingCooldownMs(ownerUuid, networkId),
            Config.webTopologySnapshotPersist,
            canForceSnapshot);
    }

    private static int parseNetworkId(Map<String, String> params) {
        String networkStr = params.get("network");
        if (networkStr == null || networkStr.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(networkStr.trim());
        } catch (NumberFormatException e) {
            return -2;
        }
    }

    private static NanoHTTPD.Response parseNetworkError(Map<String, String> params) {
        int id = parseNetworkId(params);
        if (id == -1) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }
        return json(
            NanoHTTPD.Response.Status.BAD_REQUEST,
            "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
    }

    private static NanoHTTPD.Response buildSuccess(TopologySnapshot snapshot, boolean cached, long timestamp,
        long cooldownRemainingMs, boolean persisted, boolean canForceSnapshot) {
        String body = "{\"success\":true,\"hasSnapshot\":true,\"cached\":" + cached
            + ",\"persisted\":"
            + persisted
            + ",\"canForceSnapshot\":"
            + canForceSnapshot
            + ",\"timestamp\":"
            + timestamp
            + ",\"cooldownRemainingMs\":"
            + cooldownRemainingMs
            + ",\"cooldownMs\":"
            + Config.webTopologyCacheTtlMs
            + ",\"data\":"
            + GSON.toJson(snapshot)
            + "}";
        return json(NanoHTTPD.Response.Status.OK, body);
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
