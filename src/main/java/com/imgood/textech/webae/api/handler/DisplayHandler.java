package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.display.DisplayCaptureService;
import com.imgood.textech.webae.display.DisplayRecord;
import com.imgood.textech.webae.display.DisplayStore;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST API for published dashboard displays (live embed + frame capture).
 */
public final class DisplayHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private DisplayHandler() {}

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.Method method, Map<String, String> params,
        String body, WebAuthSession auth) {
        return handle(uri, method, params, body, auth, null);
    }

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.Method method, Map<String, String> params,
        String body, WebAuthSession auth, Map<String, String> headers) {
        if ("/api/display".equals(uri)) {
            if (method == NanoHTTPD.Method.POST) {
                if (auth == null || auth.isGuest()) {
                    return forbidden("Guest cannot publish displays");
                }
                return handlePublish(body, auth.ownerUuid, headers);
            }
            return methodNotAllowed("Use POST /api/display");
        }

        // /api/display/{id} or /api/display/{id}/layout or /api/display/{id}/frame.jpg
        if (!uri.startsWith("/api/display/")) {
            return notFound();
        }
        String rest = uri.substring("/api/display/".length());
        if (rest.isEmpty()) return notFound();

        String id;
        String suffix = "";
        int slash = rest.indexOf('/');
        if (slash < 0) {
            id = rest;
        } else {
            id = rest.substring(0, slash);
            suffix = rest.substring(slash);
        }

        DisplayRecord record = DisplayStore.getById(id);
        if (record == null) return notFound();

        if (!authorizeRead(record, params, auth)) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                "application/json",
                "{\"success\":false,\"code\":\"invalid_display_token\",\"message\":\"Invalid display view token\"}");
        }

        if (suffix.isEmpty() || "/".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET /api/display/{id}");
            return jsonMeta(record);
        }
        if ("/layout".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET /api/display/{id}/layout");
            return jsonLayout(record);
        }
        if ("/frame.jpg".equals(suffix) || "/frame".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET /api/display/{id}/frame.jpg");
            return handleFrame(record, params);
        }
        if ("/frame-status".equals(suffix) || "/status".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET /api/display/{id}/frame-status");
            return handleFrameStatus(record);
        }
        if ("/render.html".equals(suffix) || "/render".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET /api/display/{id}/render.html");
            return handleRender(record);
        }
        if ("/touch".equals(suffix)) {
            if (method != NanoHTTPD.Method.POST) return methodNotAllowed("Use POST /api/display/{id}/touch");
            int width = parseInt(params.get("width"), record.viewportWidth);
            DisplayCaptureService.instance()
                .touch(record.id, width);
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true}");
        }
        return notFound();
    }

    /**
     * Public (pre-auth) entry for layout/meta/frame when viewToken is present.
     * Returns null if the request should fall through to normal auth.
     */
    public static NanoHTTPD.Response handlePublic(String uri, NanoHTTPD.Method method, Map<String, String> params) {
        if (!uri.startsWith("/api/display/")) return null;
        String token = params != null ? params.get("token") : null;
        if (token == null || token.isEmpty()) {
            if (params != null) token = params.get("viewToken");
        }
        if (token == null || token.isEmpty()) return null;
        if (method != NanoHTTPD.Method.GET && method != NanoHTTPD.Method.POST) return null;

        String rest = uri.substring("/api/display/".length());
        if (rest.isEmpty()) return null;
        String id;
        String suffix = "";
        int slash = rest.indexOf('/');
        if (slash < 0) {
            id = rest;
        } else {
            id = rest.substring(0, slash);
            suffix = rest.substring(slash);
        }
        DisplayRecord record = DisplayStore.getById(id);
        if (record == null) {
            return notFound();
        }
        if (!token.equals(record.viewToken)) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                "application/json",
                "{\"success\":false,\"code\":\"invalid_display_token\",\"message\":\"Invalid display view token\"}");
        }
        if (suffix.isEmpty() || "/".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET");
            return jsonMeta(record);
        }
        if ("/layout".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET");
            return jsonLayout(record);
        }
        if ("/frame.jpg".equals(suffix) || "/frame".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET");
            return handleFrame(record, params);
        }
        if ("/frame-status".equals(suffix) || "/status".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET");
            return handleFrameStatus(record);
        }
        if ("/render.html".equals(suffix) || "/render".equals(suffix)) {
            if (method != NanoHTTPD.Method.GET) return methodNotAllowed("Use GET");
            return handleRender(record);
        }
        if ("/touch".equals(suffix) && method == NanoHTTPD.Method.POST) {
            int width = parseInt(params != null ? params.get("width") : null, record.viewportWidth);
            DisplayCaptureService.instance()
                .touch(record.id, width);
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true}");
        }
        return null;
    }

    public static WebAuthSession sessionFromViewToken(String tokenValue) {
        DisplayRecord record = DisplayStore.getByViewToken(tokenValue);
        if (record == null) return null;
        return new WebAuthSession(
            tokenValue,
            WebAuthSession.TYPE_GUEST,
            record.ownerUuid,
            record.ownerUuid,
            "display:" + record.id);
    }

    private static NanoHTTPD.Response handlePublish(String body, String ownerUuid, Map<String, String> headers) {
        PublishBody req = parseBody(body, PublishBody.class);
        if (req == null || req.layout == null) {
            return badRequest("layout required");
        }
        DisplayRecord record = DisplayStore
            .publish(ownerUuid, req.title, req.layout, req.viewportWidth, req.viewportHeight, req.id);
        if (record == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Failed to publish display (limit or invalid layout)\"}");
        }
        DisplayCaptureService.instance()
            .invalidate(record.id);
        JsonObject binding = new JsonObject();
        binding.addProperty("format", "textech-webae-display-binding");
        binding.addProperty("version", 1);
        binding.addProperty("mode", "dashboard_live");
        binding.addProperty("displayId", record.id);
        binding.addProperty("viewToken", record.viewToken);
        binding.addProperty("title", record.title);
        binding.addProperty("exportedAt", Long.valueOf(record.updatedAt));
        JsonObject viewport = new JsonObject();
        viewport.addProperty("width", Integer.valueOf(record.viewportWidth));
        viewport.addProperty("height", Integer.valueOf(record.viewportHeight));
        binding.add("viewportHint", viewport);
        binding.addProperty("webaeOrigin", resolvePublicOrigin(headers));
        binding.addProperty("embedPath", "/embed/dashboard/" + record.id + "?token=" + record.viewToken);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"display\":" + GSON.toJson(publicMeta(record))
                + ",\"binding\":"
                + GSON.toJson(binding)
                + "}");
    }

    /**
     * Prefer the browser Origin/Host so imported bindings already contain a reachable WebAE address.
     */
    private static String resolvePublicOrigin(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        String origin = header(headers, "origin");
        if (origin != null && (origin.startsWith("http://") || origin.startsWith("https://"))) {
            if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
            return origin;
        }
        String host = header(headers, "host");
        if (host == null || host.isEmpty()) return "";
        String proto = header(headers, "x-forwarded-proto");
        if (proto == null || proto.isEmpty()) proto = "http";
        return proto + "://" + host;
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null || name == null) return null;
        String v = headers.get(name);
        if (v != null) return v.trim();
        // NanoHTTPD lowercases header names.
        v = headers.get(name.toLowerCase(java.util.Locale.ROOT));
        return v != null ? v.trim() : null;
    }

    private static NanoHTTPD.Response handleFrameStatus(DisplayRecord record) {
        DisplayCaptureService svc = DisplayCaptureService.instance();
        boolean hasFrame = svc.hasCachedFrame(record.id);
        boolean inFlight = svc.isCaptureInFlight(record.id);
        String err = svc.getLastError(record.id);
        StringBuilder sb = new StringBuilder(192);
        sb.append("{\"success\":true,\"displayId\":\"")
            .append(escapeJson(record.id))
            .append("\",\"hasFrame\":")
            .append(hasFrame)
            .append(",\"inFlight\":")
            .append(inFlight)
            .append(",\"error\":\"")
            .append(escapeJson(err != null ? err : ""))
            .append("\",\"source\":\"spa-jpeg\"}");
        NanoHTTPD.Response resp = json(NanoHTTPD.Response.Status.OK, sb.toString());
        if (err != null && !err.isEmpty()) {
            resp.addHeader("X-WebAE-Capture-Error", err);
        }
        return resp;
    }

    private static NanoHTTPD.Response handleRender(DisplayRecord record) {
        String html = com.imgood.textech.webae.display.DisplayCapturePage.render(record);
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private static NanoHTTPD.Response handleFrame(DisplayRecord record, Map<String, String> params) {
        int width = parseInt(params != null ? params.get("width") : null, 512);
        String ifNoneMatch = params != null ? params.get("ifNoneMatch") : null;
        DisplayCaptureService.FrameResult frame = DisplayCaptureService.instance()
            .getOrCapture(record, width, ifNoneMatch);
        if (frame.notModified) {
            NanoHTTPD.Response resp = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                "{\"success\":true,\"notModified\":true}");
            resp.addHeader("ETag", frame.etag);
            resp.addHeader("X-WebAE-Frame", "not-modified");
            return resp;
        }
        if (frame.jpeg == null || frame.jpeg.length == 0) {
            String err = frame.error != null ? frame.error : "capture_pending";
            NanoHTTPD.Response resp = json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"code\":\"capture_unavailable\",\"message\":\""
                    + escapeJson(err)
                    + "\"}");
            resp.addHeader("X-WebAE-Capture-Error", err);
            resp.addHeader("Cache-Control", "no-store");
            return resp;
        }
        NanoHTTPD.Response resp = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "image/jpeg",
            new java.io.ByteArrayInputStream(frame.jpeg),
            frame.jpeg.length);
        resp.addHeader("ETag", frame.etag);
        resp.addHeader("Cache-Control", "no-cache");
        resp.addHeader("X-WebAE-Frame", "spa-jpeg");
        return resp;
    }

    private static boolean authorizeRead(DisplayRecord record, Map<String, String> params, WebAuthSession auth) {
        if (auth != null && auth.ownerUuid != null && auth.ownerUuid.equals(record.ownerUuid)) return true;
        String token = params != null ? params.get("token") : null;
        if (token == null || token.isEmpty()) {
            if (params != null) token = params.get("viewToken");
        }
        return token != null && token.equals(record.viewToken);
    }

    private static JsonObject publicMeta(DisplayRecord record) {
        JsonObject o = new JsonObject();
        o.addProperty("id", record.id);
        o.addProperty("title", record.title);
        o.addProperty("createdAt", Long.valueOf(record.createdAt));
        o.addProperty("updatedAt", Long.valueOf(record.updatedAt));
        o.addProperty("viewportWidth", Integer.valueOf(record.viewportWidth));
        o.addProperty("viewportHeight", Integer.valueOf(record.viewportHeight));
        return o;
    }

    private static NanoHTTPD.Response jsonMeta(DisplayRecord record) {
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"display\":" + GSON.toJson(publicMeta(record)) + "}");
    }

    private static NanoHTTPD.Response jsonLayout(DisplayRecord record) {
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"display\":" + GSON.toJson(publicMeta(record))
                + ",\"layout\":"
                + GSON.toJson(record.layout)
                + "}");
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <T> T parseBody(String body, Class<T> type) {
        if (body == null || body.trim()
            .isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(body, type);
        } catch (Exception e) {
            return null;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static NanoHTTPD.Response badRequest(String message) {
        return json(
            NanoHTTPD.Response.Status.BAD_REQUEST,
            "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static NanoHTTPD.Response forbidden(String message) {
        return json(
            NanoHTTPD.Response.Status.FORBIDDEN,
            "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static NanoHTTPD.Response notFound() {
        return json(NanoHTTPD.Response.Status.NOT_FOUND, "{\"success\":false,\"message\":\"Display not found\"}");
    }

    private static NanoHTTPD.Response methodNotAllowed(String message) {
        return json(
            NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
            "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static final class PublishBody {

        String id;
        String title;
        int viewportWidth;
        int viewportHeight;
        JsonObject layout;
    }
}
