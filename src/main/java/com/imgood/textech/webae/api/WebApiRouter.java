package com.imgood.textech.webae.api;

import java.io.DataInputStream;
import java.util.Map;

import com.imgood.textech.webae.api.handler.AlertsHandler;
import com.imgood.textech.webae.api.handler.AssistantHandler;
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
import com.imgood.textech.webae.api.handler.NetworkMetricFluidHandler;
import com.imgood.textech.webae.api.handler.NetworkMetricHandler;
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
import com.imgood.textech.webae.api.handler.RecipeHandler;
import com.imgood.textech.webae.api.handler.ScannerHandler;
import com.imgood.textech.webae.api.handler.SearchHandler;
import com.imgood.textech.webae.api.handler.ServerHealthHandler;
import com.imgood.textech.webae.api.handler.StorageHandler;
import com.imgood.textech.webae.api.handler.StoragePagedHandler;
import com.imgood.textech.webae.api.handler.TopologyHandler;
import com.imgood.textech.webae.api.handler.WebConfigHandler;
import com.imgood.textech.webae.worldmap.WorldMapHandler;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.context.WebAeOwnerContext;

import fi.iki.elonen.NanoHTTPD;

/**
 * 
 * WebAE API router — dispatches API requests to handlers.
 * 
 */

public class WebApiRouter {

    public NanoHTTPD.Response route(NanoHTTPD.IHTTPSession session, WebAuthSession auth) {

        String ownerUuid = auth.ownerUuid;

        String uri = session.getUri();

        Map<String, String> params = session.getParms();

        NanoHTTPD.Method method = session.getMethod();

        if ("/api/auth/login".equals(uri)) {

            return handleLogin(auth);

        }

        if ("/api/config".equals(uri)) {

            return WebConfigHandler.handle(uri, params, ownerUuid);

        }

        if (isStoragePagedUri(uri)) {

            return StoragePagedHandler.handle(uri, params, ownerUuid);

        }

        if (isStorageUri(uri)) {

            return StorageHandler.handle(uri, params, ownerUuid);

        }

        if (uri.startsWith("/api/recipes")) {

            return RecipeHandler.handle(uri, params, ownerUuid);

        }

        if (isPowerUri(uri)) {

            return PowerHandler.handle(uri, params, ownerUuid);

        }

        if (isGtUri(uri)) {

            return GtMachineHandler.handle(uri, params, ownerUuid);

        }

        if ("/api/order/templates".equals(uri)) {

            String body = readBody(session);

            if (method == NanoHTTPD.Method.GET) {

                return OrderTemplatesHandler.handleGet(ownerUuid);

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

            return OrderHandler.handle(uri, params, body, ownerUuid);

        }

        if ("/api/interfaces".equals(uri) || uri.startsWith("/api/pattern/")) {

            return PatternHandler.handle(uri, session, ownerUuid);

        }

        if ("/api/patterns/browse/refresh".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/patterns/browse/refresh\"}");

            }

            return PatternBrowseHandler.handleRefresh(params, ownerUuid);

        }

        if ("/api/patterns/browse".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/patterns/browse\"}");

            }

            return PatternBrowseHandler.handle(params, ownerUuid);

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

            return PatternGridDetailHandler.handle(gridKeyPart, params, ownerUuid);

        }

        if ("/api/patterns".equals(uri) || uri.startsWith("/api/patterns/")) {

            String body = readBody(session);

            return PatternListHandler.handle(uri, method, params, body, ownerUuid);

        }

        if ("/api/icon".equals(uri) || uri.startsWith("/api/icon/")) {

            return IconHandler.handle(uri, session, ownerUuid);

        }

        if (uri.startsWith("/api/chat/")) {

            String body = readBody(session);

            return ChatHandler.handle(uri, method, params, body, auth);

        }

        if ("/api/players".equals(uri) || "/api/players/since".equals(uri)

            || "/api/players/online/history".equals(uri)
            || "/api/players/locations".equals(uri)) {

            return PlayerHandler.handle(uri, method, params, ownerUuid);

        }

        if ("/api/auth/guest-invite".equals(uri)) {

            return AuthGuestInviteHandler.handle(session, auth);

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

        if ("/api/oc/summary".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/oc/summary\"}");

            }

            return OcSummaryHandler.handle(ownerUuid);

        }

        if ("/api/network/metrics".equals(uri)) {

            return NetworkMetricHandler.handle(uri, params, ownerUuid);

        }

        if ("/api/network/metrics/fluids".equals(uri)) {

            return NetworkMetricFluidHandler.handle(params, ownerUuid);

        }

        if ("/api/network/topology/snapshot".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/network/topology/snapshot\"}");

            }

            return TopologyHandler.handleSnapshot(params, ownerUuid, auth.actorUuid);

        }

        if ("/api/network/topology".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/network/topology\"}");

            }

            return TopologyHandler.handle(params, ownerUuid, auth.actorUuid);

        }

        if ("/api/worldmap/meta".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/meta\"}");

            }

            return WorldMapHandler.handleMeta(params, ownerUuid, auth.actorUuid);

        }

        if ("/api/worldmap/markers".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/markers\"}");

            }

            return WorldMapHandler.handleMarkers(params, ownerUuid);

        }

        if ("/api/worldmap/invalidate".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/worldmap/invalidate\"}");

            }

            return WorldMapHandler.handleInvalidate(params, ownerUuid);

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

            return WorldMapHandler.handleSnapshotManifest(params, ownerUuid);

        }

        if ("/api/worldmap/snapshot/status".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/snapshot/status\"}");

            }

            return WorldMapHandler.handleSnapshotStatus(params, ownerUuid);

        }

        if ("/api/worldmap/snapshot/request".equals(uri)) {

            if (method != NanoHTTPD.Method.POST) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use POST /api/worldmap/snapshot/request\"}");

            }

            return WorldMapHandler.handleSnapshotRequest(params, ownerUuid, auth.actorUuid, auth.actorName);

        }

        if (uri.startsWith("/api/worldmap/tiles/")) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/worldmap/tiles/<dim>/<chunkX>/<chunkZ>.png\"}");

            }

            return WorldMapHandler.handleTile(uri, params, ownerUuid, auth.actorUuid);

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

            return CellSummaryHandler.handle(params, ownerUuid);

        }

        if ("/api/network/balance".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/network/balance\"}");

            }

            return NetworkBalanceHandler.handle(params, ownerUuid);

        }

        if ("/api/scanner/blocks".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/scanner/blocks\"}");

            }

            return ScannerHandler.handle(params, ownerUuid);

        }

        if ("/api/monitor/bindings".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/monitor/bindings\"}");

            }

            return MonitorHandler.handle(params, ownerUuid);

        }

        if ("/api/monitor/preview".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/monitor/preview\"}");

            }

            return MonitorPreviewHandler.handle(params, ownerUuid);

        }

        if ("/api/favorites".equals(uri)) {
            if (auth.isGuest()) {
                if (method != NanoHTTPD.Method.GET) {
                    return guestWriteDenied();
                }
            }
            if (method == NanoHTTPD.Method.GET) {
                return FavoritesHandler.handleGet(ownerUuid);
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
                    return PlannerHandler.handleList(ownerUuid);
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

            return AssistantHandler.handle(body, ownerUuid);

        }

        if ("/api/pocket/overview".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/pocket/overview\"}");

            }

            return PocketHandler.handle(auth);

        }

        if ("/api/alerts/rules".equals(uri)) {

            if (method == NanoHTTPD.Method.GET) {

                return AlertsHandler.handleGetRules(auth.actorUuid);

            }

            if (method == NanoHTTPD.Method.PUT) {

                String body = readBody(session);

                return AlertsHandler.handlePutRules(body, auth.actorUuid);

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

            return AlertsHandler.handleHistory(params, ownerUuid);

        }

        if ("/api/alerts".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/alerts or PUT /api/alerts/rules\"}");

            }

            return AlertsHandler.handle(params, ownerUuid, auth.actorUuid);

        }

        if ("/api/craft/tree".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/craft/tree\"}");

            }

            return CraftTreeHandler.handle(params, ownerUuid);

        }

        if ("/api/events/stream".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/events/stream\"}");

            }

            return EventStreamHandler.handle(ownerUuid);

        }

        if ("/api/network/p2p".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/network/p2p\"}");

            }

            return P2pHandler.handle(params, ownerUuid);

        }

        if ("/api/search".equals(uri)) {

            if (method != NanoHTTPD.Method.GET) {

                return NanoHTTPD.newFixedLengthResponse(

                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,

                    "application/json",

                    "{\"success\":false,\"message\":\"Use GET /api/search\"}");

            }

            return SearchHandler.handle(params, ownerUuid);

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
