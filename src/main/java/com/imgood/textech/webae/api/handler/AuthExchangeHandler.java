package com.imgood.textech.webae.api.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.auth.WebAuthToken;
import com.imgood.textech.webae.auth.WebLoginCodeStore;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.player.WebAePlayerStateStore;

import fi.iki.elonen.NanoHTTPD;

/**
 * POST /api/auth/exchange — exchange a 6-digit login code for an owner session token (no prior auth).
 */
public final class AuthExchangeHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private AuthExchangeHandler() {}

    public static NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            return json(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                error("method_not_allowed", "Use POST /api/auth/exchange"));
        }
        String body = readBody(session);
        String code = parseCode(body);
        if (code == null || code.isEmpty()) {
            return json(NanoHTTPD.Response.Status.BAD_REQUEST, error("missing_code", "Missing login code."));
        }
        WebLoginCodeStore.ExchangeResult result = WebLoginCodeStore.exchange(code);
        if (!result.success) {
            NanoHTTPD.Response.Status status = "expired".equals(result.errorCode)
                || "invalid_or_used".equals(result.errorCode)
                || "invalid_code".equals(result.errorCode) ? NanoHTTPD.Response.Status.UNAUTHORIZED
                    : NanoHTTPD.Response.Status.BAD_REQUEST;
            return json(status, error(result.errorCode, describeError(result.errorCode)));
        }
        if (WebAePlayerStateStore.getInstance().isDisabled(result.ownerUuid)) {
            return json(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                error("webae_disabled", "WebAE has been disabled for this player. Contact an administrator."));
        }
        if (WebAeOwnerContext.countMonitors(result.ownerUuid) <= 0) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                error("no_monitor", "Owner has no Advance Data Monitor bound."));
        }
        WebAuthToken token = WebAuthToken.generateOwnerToken(result.ownerUuid, result.ownerName);
        WebAeOwnerContext.invalidateConnectors(result.ownerUuid);
        ExchangeOk ok = new ExchangeOk();
        ok.status = "ok";
        ok.message = "Login code exchanged successfully.";
        ok.token = token.token;
        ok.ownerUuid = result.ownerUuid;
        ok.playerUuid = result.ownerUuid;
        ok.ownerName = result.ownerName;
        ok.actorUuid = result.ownerUuid;
        ok.actorName = result.ownerName;
        ok.tokenType = "owner";
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(ok));
    }

    private static String describeError(String code) {
        if ("expired".equals(code)) {
            return "Login code expired. Run /admweb login in-game again.";
        }
        if ("invalid_or_used".equals(code)) {
            return "Login code invalid or already used.";
        }
        if ("invalid_code".equals(code)) {
            return "Login code must be 6 digits.";
        }
        return "Login code exchange failed.";
    }

    private static String parseCode(String body) {
        if (body == null || body.trim()
            .isEmpty()) {
            return null;
        }
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            if (obj.has("code") && !obj.get("code")
                .isJsonNull()) {
                return obj.get("code")
                    .getAsString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static String readBody(NanoHTTPD.IHTTPSession session) {
        try {
            int contentLength = 0;
            String cl = session.getHeaders()
                .get("content-length");
            if (cl != null) {
                contentLength = Integer.parseInt(cl.trim());
            }
            if (contentLength <= 0) {
                return "";
            }
            byte[] buffer = new byte[contentLength];
            java.io.DataInputStream dis = new java.io.DataInputStream(session.getInputStream());
            dis.readFully(buffer);
            return new String(buffer, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String error(String code, String message) {
        return "{\"status\":\"error\",\"code\":\"" + escapeJson(code)
            + "\",\"error\":\"" + escapeJson(code)
            + "\",\"message\":\""
            + escapeJson(message)
            + "\"}";
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static final class ExchangeOk {

        String status;
        String message;
        String token;
        String ownerUuid;
        String playerUuid;
        String ownerName;
        String actorUuid;
        String actorName;
        String tokenType;
    }
}
