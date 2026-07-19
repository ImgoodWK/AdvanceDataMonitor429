package com.imgood.textech.webae.api.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.qqbot.QqBotConfig;
import com.imgood.textech.webae.qqbot.QqBotConfigStore;
import com.imgood.textech.webae.qqbot.QqBotService;
import com.imgood.textech.webae.qqbot.QqBotService.ManualSendResult;

import fi.iki.elonen.NanoHTTPD;

/** Admin-only QQ bot configuration, lifecycle, send, and audit APIs. */
public final class QqBotAdminHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private QqBotAdminHandler() {}

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.Method method, String body, WebAuthSession auth,
        String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return error(NanoHTTPD.Response.Status.FORBIDDEN, "admin_required",
                "Admin permission required to manage the QQ bot.");
        }
        try {
            if ("/api/admin/qq-bot/settings".equals(uri)) {
                if (method == NanoHTTPD.Method.GET) return settingsResponse();
                if (method == NanoHTTPD.Method.PUT) {
                    QqBotConfig request = GSON.fromJson(body == null ? "{}" : body, QqBotConfig.class);
                    QqBotConfigStore.instance().update(request, auth.actorName);
                    QqBotService.instance().reload();
                    return settingsResponse();
                }
            }
            if ("/api/admin/qq-bot/status".equals(uri) && method == NanoHTTPD.Method.GET) {
                return ok("status", QqBotService.instance().status());
            }
            if ("/api/admin/qq-bot/audit".equals(uri) && method == NanoHTTPD.Method.GET) {
                return ok("audit", QqBotService.instance().audit(200));
            }
            if ("/api/admin/qq-bot/restart".equals(uri) && method == NanoHTTPD.Method.POST) {
                return manualResult(QqBotService.instance().restart(), "restart_failed");
            }
            if ("/api/admin/qq-bot/send".equals(uri) && method == NanoHTTPD.Method.POST) {
                SendRequest request = GSON.fromJson(body == null ? "{}" : body, SendRequest.class);
                if (request == null) request = new SendRequest();
                return manualResult(QqBotService.instance().sendManual(request.targetType, request.targetId,
                    request.content), "send_failed");
            }
            if ("/api/admin/qq-bot/conversations/clear".equals(uri) && method == NanoHTTPD.Method.POST) {
                QqBotService.instance().clearConversations();
                return ok("status", QqBotService.instance().status());
            }
            if ("/api/admin/qq-bot/secret".equals(uri) && method == NanoHTTPD.Method.DELETE) {
                QqBotConfigStore.instance().clearSecret(auth.actorName);
                QqBotService.instance().reload();
                return settingsResponse();
            }
            return error(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed",
                "Unsupported QQ bot management endpoint or method.");
        } catch (IllegalArgumentException e) {
            return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_qq_bot_settings", safe(e.getMessage()));
        } catch (IllegalStateException e) {
            return error(NanoHTTPD.Response.Status.INTERNAL_ERROR, "qq_bot_store_failed", safe(e.getMessage()));
        } catch (Exception e) {
            return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_request", safe(e.getMessage()));
        }
    }

    private static NanoHTTPD.Response settingsResponse() {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.add("settings", GSON.toJsonTree(QqBotConfigStore.instance().view()));
        response.add("status", GSON.toJsonTree(QqBotService.instance().status()));
        return json(NanoHTTPD.Response.Status.OK, response.toString());
    }

    private static NanoHTTPD.Response manualResult(ManualSendResult result, String code) {
        if (!result.success) return error(NanoHTTPD.Response.Status.CONFLICT, code, result.error);
        return ok("status", QqBotService.instance().status());
    }

    private static NanoHTTPD.Response ok(String name, Object value) {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.add(name, GSON.toJsonTree(value));
        return json(NanoHTTPD.Response.Status.OK, response.toString());
    }

    private static NanoHTTPD.Response error(NanoHTTPD.Response.Status status, String code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("code", code);
        response.addProperty("message", message.isEmpty() ? "Request failed." : message);
        return json(status, response.toString());
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static String safe(String value) {
        String result = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return result.length() <= 500 ? result : result.substring(0, 500);
    }

    private static final class SendRequest {

        String targetType = "group";
        String targetId = "";
        String content = "";
    }
}
