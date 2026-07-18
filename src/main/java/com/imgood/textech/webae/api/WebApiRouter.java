package com.imgood.textech.webae.api;

import java.io.DataInputStream;
import java.util.Map;

import com.imgood.textech.webae.api.handler.AdminPlayerHandler;
import com.imgood.textech.webae.api.handler.AeCableTextureHandler;
import com.imgood.textech.webae.api.handler.AlertsHandler;
import com.imgood.textech.webae.api.handler.AssistantHandler;
import com.imgood.textech.webae.api.handler.AuthAdminElevateHandler;
import com.imgood.textech.webae.api.handler.AuthGuestInviteHandler;
import com.imgood.textech.webae.api.handler.CellSummaryHandler;
import com.imgood.textech.webae.api.handler.ChatHandler;
import com.imgood.textech.webae.api.handler.CraftTreeHandler;
import com.imgood.textech.webae.api.handler.EventStreamHandler;
import com.imgood.textech.webae.api.handler.FavoritesHandler;
import com.imgood.textech.webae.api.handler.GtMachineHandler;
import com.imgood.textech.webae.api.handler.IconHandler;
import com.imgood.textech.webae.api.handler.MonitorHandler;
import com.imgood.textech.webae.api.handler.MonitorPreviewHandler;
import com.imgood.textech.webae.api.handler.NetworkBalanceHandler;
import com.imgood.textech.webae.api.handler.NetworkMetricEntityHandler;
import com.imgood.textech.webae.api.handler.NetworkMetricFluidHandler;
import com.imgood.textech.webae.api.handler.NetworkMetricHandler;
import com.imgood.textech.webae.api.handler.NetworkMetricItemHandler;
import com.imgood.textech.webae.api.handler.OcSummaryHandler;
import com.imgood.textech.webae.api.handler.OrderHandler;
import com.imgood.textech.webae.api.handler.OrderTemplatesHandler;
import com.imgood.textech.webae.api.handler.P2pHandler;
import com.imgood.textech.webae.api.handler.PatternBrowseHandler;
import com.imgood.textech.webae.api.handler.PatternGridDetailHandler;
import com.imgood.textech.webae.api.handler.PatternHandler;
import com.imgood.textech.webae.api.handler.PatternListHandler;
import com.imgood.textech.webae.api.handler.PlannerHandler;
import com.imgood.textech.webae.api.handler.PlayerHandler;
import com.imgood.textech.webae.api.handler.PocketHandler;
import com.imgood.textech.webae.api.handler.PowerHandler;
import com.imgood.textech.webae.api.handler.QuestHandler;
import com.imgood.textech.webae.api.handler.RecipeHandler;
import com.imgood.textech.webae.api.handler.ScannerHandler;
import com.imgood.textech.webae.api.handler.SearchHandler;
import com.imgood.textech.webae.api.handler.ServerDiagnosticsHandler;
import com.imgood.textech.webae.api.handler.ServerHealthHandler;
import com.imgood.textech.webae.api.handler.SparkHandler;
import com.imgood.textech.webae.api.handler.StorageHandler;
import com.imgood.textech.webae.api.handler.StoragePagedHandler;
import com.imgood.textech.webae.api.handler.TopologyHandler;
import com.imgood.textech.webae.api.handler.WebConfigHandler;
import com.imgood.textech.webae.worldmap.WorldMapHandler;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.perf.WebAePerfProfiler;
import com.imgood.textech.webae.player.WebAePlayerStateStore;

import fi.iki.elonen.NanoHTTPD;

/**
 * 
 * WebAE API router — dispatches API requests to handlers.
 * 
 */

public class WebApiRouter {

    public NanoHTTPD.Response route(NanoHTTPD.IHTTPSession session, WebAuthSession auth) {
        String uri = session.getUri();
        long start = WebAePerfProfiler.instance()
            .begin();
        NanoHTTPD.Response response = null;
        try {
            response = routeInner(session, auth);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            WebAePerfProfiler.instance()
                .recordHttp(normalizeRoute(uri), ms);
            // Record per-player request stats
            if (auth != null && auth.ownerUuid != null) {
                WebAePlayerStateStore.getInstance().touchRequest(auth.ownerUuid, ms);
            }
        }
        return response;
    }

    private static String normalizeRoute(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "?";
        }
        int q = uri.indexOf('?');
        if (q >= 0) {
            uri = uri.substring(0, q);
        }
        return uri;
    }

    private NanoHTTPD.Response routeInner(NanoHTTPD.IHTTPSession session, WebAuthSession auth) {

        String uri = session.getUri();

        Map<String, String> params = session.getParms();

        Map<String, String> headers = session.getHeaders();

        String adminHeader = headers.get("x-webae-admin");
        if (adminHeader == null || adminHeader.isEmpty()) {
            adminHeader = headers.get("X-WebAE-Admin");
        }

        String ownerUuid = auth.ownerUuid;

        NanoHTTPD.Method method = session.getMethod();

        // ---- Cross-player query: admin can override owner via ?owner= param (GET only) ----
        String effectiveOwner = ownerUuid;
        if (adminHeader != null && !adminHeader.isEmpty()
            && WebAuthAdminCheck.isAdmin(auth, adminHeader)
            && method == NanoHTTPD.Method.GET) {
            String overrideOwner = params.get("owner");
            if (overrideOwner != null && !overrideOwner.isEmpty()) {
                effectiveOwner = overrideOwner;
            }
        }

        // ---- Disable interception: reject disabled owner/actor (401 so SPA returns to login) ----
        if (!uri.startsWith("/api/auth/admin/") && !uri.startsWith("/api/admin/")) {
            if (WebAePlayerStateStore.getInstance().isDisabled(ownerUuid)
                || WebAePlayerStateStore.getInstance().isDisabled(auth.actorUuid)) {
                return disabledResponse();
            }
        }

        if ("/api/auth/login".equals(uri)) {
            return handleLogin(auth);
        }

        // ---- Network ACL / suspend gate for ?network= ----
        if (!uri.startsWith("/api/admin/")) {
            String networkParam = params.get("network");
            if (networkParam != null && !networkParam.isEmpty()) {
                try {
                    int nid = Integer.parseInt(networkParam.trim());
                    NanoHTTPD.Response denied = com.imgood.textech.webae.access.WebAeNetworkAccess
                        .assertCanAccess(auth, effectiveOwner, nid);
                    if (denied != null) {
                        return denied;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if ("/api/config".equals(uri)) {

            return WebConfigHandler.handle(uri, params, effectiveOwner);

        }

        if (isStoragePagedUri(uri)) {

            return StoragePagedHandler.handle(uri, params, effectiveOwner);

        }

        if (isStorageUri(uri)) {

            return StorageHandler.handle(uri, params, auth, adminHeader, effectiveOwner);

        }

        if (uri.startsWith("/api/recipes")) {

            return RecipeHandler.handle(uri, params, effectiveOwner);

        }

        if (isPowerUri(uri)) {

            return PowerHandler.handle(uri, params, auth, adminHeader);

        }

        if (isGtUri(uri)) {

            return GtMachineHandler.handle(uri, params, auth, adminHeader);

        }

        if ("/api/order/templates".equals(uri)) {

            String body = readBody(session);

            if (method == NanoHTTPD.Method.GET) {

                return OrderTemplatesHandler.handleGet(effectiveOwner);

            }

            if (method == NanoHTTPD.Method.PUT) {

                return OrderTemplatesHandler.handlePut(body, ownerUuid);

            }

            return NanoHTTPD.newFixedLengthResponse(

                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                "application/json",

                "{\"success\":false,\"message\":\"Use GET or PUT /api/order/templates\"}");

        }

        if (uri.startsWith("/api/order")) {

            String body = readBody(session);

            return OrderHandler.handle(uri, params, body, effectiveOwner);

        }

        if ("/api/interfaces".equals(uri) || uri.startsWith("/api/pattern/")) {

            return PatternHandler.handle(uri, session, effectiveOwner);

        }

        if ("/api/patterns/browse/refresh".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/patterns/browse/refresh\"}");

            }

            return PatternBrowseHandler.handleRefresh(params, auth, adminHeader);

        }

        if ("/api/patterns/browse".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/patterns/browse\"}");

            }

            return PatternBrowseHandler.handle(params, auth, adminHeader);

        }

        if (uri.startsWith("/api/patterns/grid/")) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/patterns/grid/<gridKey>\"}");

            }

            String gridKeyPart = uri.substring("/api/patterns/grid/".length());

            if (gridKeyPart.isEmpty()) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.BAD_REQUEST,

                    "application/json",

                    "{\"success\":false,\"message\":\"Missing grid key\"}");

            }

            return PatternGridDetailHandler.handle(gridKeyPart, params, effectiveOwner);

        }

        if ("/api/patterns".equals(uri) || uri.startsWith("/api/patterns/")) {

            String body = readBody(session);

            return PatternListHandler.handle(uri, method, params, body, auth, adminHeader);

        }

        if ("/api/icon".equals(uri) || uri.startsWith("/api/icon/")) {

            return IconHandler.handle(uri, session, auth, adminHeader);

        }

        if ("/api/ae2/cable-texture".equals(uri)) {

            return AeCableTextureHandler.handle(session);

        }

        if (uri.startsWith("/api/chat/")) {

            String body = readBody(session);

            return ChatHandler.handle(uri, method, params, body, auth);

        }

        if ("/api/players".equals(uri) || "/api/players/since".equals(uri)

            || "/api/players/online/history".equals(uri)
            || "/api/players/locations".equals(uri)) {

            return PlayerHandler.handle(uri, method, params, effectiveOwner);

        }

        if ("/api/auth/guest-invite".equals(uri)) {

            return AuthGuestInviteHandler.handle(session, auth);

        }

        // ---- Admin elevation APIs ----

        if ("/api/auth/admin/elevate".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/auth/admin/elevate\"}");

            }

            return AuthAdminElevateHandler.handle(session, auth);

        }

        if ("/api/auth/admin/me".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/auth/admin/me\"}");

            }

            return AuthAdminElevateHandler.handleMe(session, auth);

        }

        if ("/api/auth/admin/grants".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/auth/admin/grants\"}");

            }

            return AuthAdminElevateHandler.handleListGrants(session, auth);

        }

        if ("/api/auth/admin/revoke-self".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/auth/admin/revoke-self\"}");

            }

            return AuthAdminElevateHandler.handleRevokeSelf(session, auth);

        }

        if ("/api/server/health".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/server/health\"}");

            }

            return ServerHealthHandler.handle();

        }

        if ("/api/server/diagnostics".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/server/diagnostics\"}");

            }

            return ServerDiagnosticsHandler.handle();

        }

        if ("/api/spark".equals(uri) || "/api/spark/profile".equals(uri)
            || "/api/spark/stop".equals(uri) || "/api/spark/history".equals(uri)
            || uri.startsWith("/api/spark/history/")) {
            String body = method == NanoHTTPD.Method.POST ? readBody(session) : null;
            return SparkHandler.handle(uri, method, body, auth, adminHeader);
        }

        // ---- Admin player management ----

        if (uri.startsWith("/api/admin/players")) {
            String adminBody = (method == NanoHTTPD.Method.POST || method == NanoHTTPD.Method.PUT)
                ? readBody(session) : null;
            return AdminPlayerHandler.handle(uri, params, method, auth, adminHeader, adminBody);
        }

        if ("/api/oc/summary".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/oc/summary\"}");

            }

            return OcSummaryHandler.handle(effectiveOwner);

        }

        if ("/api/network/metrics".equals(uri)) {

            return NetworkMetricHandler.handle(uri, params, effectiveOwner);

        }

        if ("/api/network/metrics/fluids".equals(uri)) {

            return NetworkMetricFluidHandler.handle(params, effectiveOwner);

        }

        if ("/api/network/metrics/items".equals(uri)) {

            return NetworkMetricItemHandler.handle(params, effectiveOwner);

        }

        if ("/api/network/metrics/entities".equals(uri)) {

            return NetworkMetricEntityHandler.handle(params, effectiveOwner);

        }

        if ("/api/network/topology/snapshot".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/network/topology/snapshot\"}");

            }

            return TopologyHandler.handleSnapshot(params, auth, adminHeader);

        }

        if ("/api/network/topology".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/network/topology\"}");

            }

            return TopologyHandler.handle(params, auth, adminHeader);

        }

        if ("/api/worldmap/meta".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/meta\"}");

            }

            return WorldMapHandler.handleMeta(params, effectiveOwner, auth.actorUuid);

        }

        if ("/api/worldmap/markers".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/markers\"}");

            }

            return WorldMapHandler.handleMarkers(params, effectiveOwner);

        }

        if ("/api/worldmap/invalidate".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/worldmap/invalidate\"}");

            }

            return WorldMapHandler.handleInvalidate(params, auth, adminHeader);

        }

        if ("/api/worldmap/progress".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/progress\"}");

            }

            return WorldMapHandler.handleProgress(params);

        }

        if ("/api/worldmap/snapshot/manifest".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/snapshot/manifest\"}");

            }

            return WorldMapHandler.handleSnapshotManifest(params, effectiveOwner);

        }

        if ("/api/worldmap/snapshot/status".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/snapshot/status\"}");

            }

            return WorldMapHandler.handleSnapshotStatus(params, effectiveOwner);

        }

        if ("/api/worldmap/snapshot/request".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/worldmap/snapshot/request\"}");

            }

            return WorldMapHandler.handleSnapshotRequest(params, effectiveOwner, auth.actorUuid, auth.actorName);

        }

        if (uri.startsWith("/api/worldmap/tiles/")) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/tiles/<dim>/<chunkX>/<chunkZ>.png\"}");

            }

            return WorldMapHandler.handleTile(uri, params, effectiveOwner, auth.actorUuid);

        }

        if (uri.startsWith("/api/worldmap/dynmap-tiles/")) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/dynmap-tiles/<world>/<zoom>/<x>/<y>.png\"}");

            }

            return WorldMapHandler.handleDynmapTile(uri, params);

        }

        if ("/api/network/cells".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/network/cells\"}");

            }

            return CellSummaryHandler.handle(params, effectiveOwner);

        }

        if ("/api/network/balance".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/network/balance\"}");

            }

            return NetworkBalanceHandler.handle(params, effectiveOwner);

        }

        if ("/api/scanner/blocks".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/scanner/blocks\"}");

            }

            return ScannerHandler.handle(params, effectiveOwner);

        }

        if ("/api/monitor/bindings".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/monitor/bindings\"}");

            }

            return MonitorHandler.handle(params, effectiveOwner);

        }

        if ("/api/monitor/preview".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/monitor/preview\"}");

            }

            return MonitorPreviewHandler.handle(params, effectiveOwner);

        }

        if ("/api/favorites".equals(uri)) {
            if (auth.isGuest()) {
                if (method != NanoHTTPD.Method.GET) {
                    return guestWriteDenied();
                }
            }
            if (method == NanoHTTPD.Method.GET) {
                return FavoritesHandler.handleGet(effectiveOwner);
            }
            if (method == NanoHTTPD.Method.PUT) {
                return FavoritesHandler.handlePut(readBody(session), ownerUuid);
            }
            return methodNotAllowed("Use GET or PUT /api/favorites");
        }

        if ("/api/planner/export-flow".equals(uri)) {
            if (auth.isGuest()) {
                return guestWriteDenied();
            }
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST /api/planner/export-flow");
            }
            return PlannerHandler.handleExportFlow(readBody(session), ownerUuid);
        }

        if (uri.startsWith("/api/planner/plans")) {
            if ("/api/planner/plans".equals(uri)) {
                if (method == NanoHTTPD.Method.GET) {
                    return PlannerHandler.handleList(effectiveOwner);
                }
                if (method == NanoHTTPD.Method.POST) {
                    if (auth.isGuest()) {
                        return guestWriteDenied();
                    }
                    return PlannerHandler.handleCreate(readBody(session), ownerUuid);
                }
                return methodNotAllowed("Use GET or POST /api/planner/plans");
            }
            int planId = PlannerHandler.parsePlanIdFromUri(uri);
            if (planId > 0) {
                if (method == NanoHTTPD.Method.PATCH) {
                    if (auth.isGuest()) {
                        return guestWriteDenied();
                    }
                    return PlannerHandler.handlePatch(readBody(session), ownerUuid, planId);
                }
                if (method == NanoHTTPD.Method.DELETE) {
                    if (auth.isGuest()) {
                        return guestWriteDenied();
                    }
                    return PlannerHandler.handleDelete(ownerUuid, planId);
                }
                return methodNotAllowed("Use PATCH or DELETE /api/planner/plans/<id>");
            }
        }

        if ("/api/assistant/query".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/assistant/query\"}");

            }

            String body = readBody(session);

            return AssistantHandler.handle(body, effectiveOwner);

        }

        if ("/api/pocket/overview".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/pocket/overview\"}");

            }

            return PocketHandler.handle(auth, adminHeader);

        }

        if ("/api/alerts/rules".equals(uri)) {

            if (method == NanoHTTPD.Method.GET) {

                return AlertsHandler.handleGetRules(auth, adminHeader);

            }

            if (method == NanoHTTPD.Method.PUT) {

                String body = readBody(session);

                return AlertsHandler.handlePutRules(body, auth, adminHeader);

            }

            return NanoHTTPD.newFixedLengthResponse(

                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                "application/json",

                "{\"success\":false,\"message\":\"Use GET or PUT /api/alerts/rules\"}");

        }

        if ("/api/alerts/history".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/alerts/history\"}");

            }

            return AlertsHandler.handleHistory(params, effectiveOwner);

        }

        if ("/api/alerts".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/alerts or PUT /api/alerts/rules\"}");

            }

            return AlertsHandler.handle(params, auth, adminHeader);

        }

        if ("/api/craft/tree".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/craft/tree\"}");

            }

            return CraftTreeHandler.handle(params, effectiveOwner);

        }

        if ("/api/events/stream".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/events/stream\"}");

            }

            return EventStreamHandler.handle(effectiveOwner);

        }

        if ("/api/network/p2p".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/network/p2p\"}");

            }

            return P2pHandler.handle(params, effectiveOwner);

        }

        if (uri.startsWith("/api/quests")) {

            String body = method == NanoHTTPD.Method.POST ? readBody(session) : null;

            return QuestHandler.handle(uri, method, params, body, effectiveOwner, auth.isGuest());

        }

        if ("/api/search".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/search\"}");

            }

            return SearchHandler.handle(params, effectiveOwner);

        }

        return NanoHTTPD.newFixedLengthResponse(

            NanoHTTPD.Response.Status.NOT_FOUND,

            "application/json",

            "{\"status\":\"error\",\"message\":\"Unknown API endpoint: " + uri + "\"}");

    }

    private static boolean isStoragePagedUri(String uri) {

        return "/api/storage/items".equals(uri) || "/api/storage/fluids".equals(uri)

            || "/api/storage/essentia".equals(uri);

    }

    private static boolean isStorageUri(String uri) {

        return "/api/storage".equals(uri) || "/api/storage/batch".equals(uri)

            || "/api/refresh".equals(uri)

            || "/api/refresh/batch".equals(uri)

            || "/api/networks".equals(uri);

    }

    private static boolean isPowerUri(String uri) {

        return "/api/power".equals(uri) || "/api/power/batch".equals(uri)

            || "/api/power/refresh".equals(uri)

            || "/api/power/refresh/batch".equals(uri);

    }

    private static boolean isGtUri(String uri) {

        return "/api/gt/machines".equals(uri) || "/api/gt/machines/batch".equals(uri)

            || "/api/gt/machines/refresh".equals(uri)

            || "/api/gt/machines/refresh/batch".equals(uri);

    }

    private NanoHTTPD.Response handleLogin(WebAuthSession auth) {
        if (WebAePlayerStateStore.getInstance().isDisabled(auth.ownerUuid)
            || WebAePlayerStateStore.getInstance().isDisabled(auth.actorUuid)) {
            return disabledResponse();
        }

        String ownerName = WebAeOwnerContext.resolveOwnerName(auth.ownerUuid);

        String response = "{"
            + "\"status\":\"ok\","
            + "\"message\":\"Authenticated successfully.\","
            + "\"playerUuid\":\""
            + escapeJson(auth.ownerUuid)
            + "\","
            + "\"ownerUuid\":\""
            + escapeJson(auth.ownerUuid)
            + "\","
            + "\"ownerName\":\""
            + escapeJson(ownerName)
            + "\","
            + "\"actorUuid\":\""
            + escapeJson(auth.actorUuid)
            + "\","
            + "\"actorName\":\""
            + escapeJson(auth.actorName)
            + "\","
            + "\"tokenType\":\""
            + escapeJson(auth.type)
            + "\""
            + "}";

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", response);
    }

    private static NanoHTTPD.Response disabledResponse() {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.UNAUTHORIZED,
            "application/json",
            "{\"success\":false,\"status\":\"error\",\"code\":\"webae_disabled\",\"error\":\"webae_disabled\","
                + "\"message\":\"WebAE has been disabled for this player. Contact an administrator.\"}");
    }

    private static String escapeJson(String value) {

        if (value == null) {

            return "";

        }

        return value.replace("\\", "\\\\")

            .replace("\"", "\\\"");

    }

    private static String readBody(NanoHTTPD.IHTTPSession session) {

        try {

            int contentLength = 0;

            String cl = session.getHeaders()

                .get("content-length");

            if (cl != null) {

                contentLength = Integer.parseInt(cl.trim());

            }

            if (contentLength <= 0) return "";

            byte[] buffer = new byte[contentLength];

            DataInputStream dis = new DataInputStream(session.getInputStream());

            dis.readFully(buffer);

            return new String(buffer, "UTF-8");

        } catch (Exception e) {

            return "";

        }

    }

    private static NanoHTTPD.Response guestWriteDenied() {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.FORBIDDEN,
            "application/json",
            "{\"success\":false,\"message\":\"Guest token is read-only\",\"code\":\"guest_readonly\"}");
    }

    private static NanoHTTPD.Response methodNotAllowed(String message) {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
            "application/json",
            "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}");
    }

}
