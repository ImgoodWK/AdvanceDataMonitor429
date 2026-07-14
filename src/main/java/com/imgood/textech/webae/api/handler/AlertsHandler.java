package com.imgood.textech.webae.api.handler;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.config.ConfigWebAlertsLoader;
import com.imgood.textech.webae.alerts.WebAlertDto;
import com.imgood.textech.webae.alerts.WebAlertHistoryEntry;
import com.imgood.textech.webae.alerts.WebAlertHistoryStore;
import com.imgood.textech.webae.alerts.WebAlertStore;
import com.imgood.textech.webae.alerts.WebAlertsConfig;
import com.imgood.textech.webae.alerts.WebAlertsConfigValidator;
import com.imgood.textech.webae.alerts.WebhookDispatcher;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/alerts — active automation alerts for the authenticated owner.
 * GET /api/alerts/history — alert occurrence history (newest first).
 * GET /api/alerts/rules — alert rules mirror (same payload as {@code rules} on GET /api/alerts).
 * PUT /api/alerts/rules — persist {@code TeXTech/WebAE/web-alerts.json} (OP only).
 */
public final class AlertsHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private AlertsHandler() {}

    public static NanoHTTPD.Response handleHistory(Map<String, String> params, String ownerUuid) {
        int offset = parseIntParam(params.get("offset"), 0);
        int limit = parseIntParam(params.get("limit"), 50);
        List<WebAlertHistoryEntry> history = WebAlertHistoryStore.instance()
            .getHistory(ownerUuid, offset, limit);
        int total = WebAlertHistoryStore.instance()
            .count(ownerUuid);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"total\":" + total
                + ",\"offset\":"
                + Math.max(0, offset)
                + ",\"limit\":"
                + Math.min(Math.max(1, limit), 200)
                + ",\"history\":"
                + GSON.toJson(history)
                + "}");
    }

    public static NanoHTTPD.Response handle(Map<String, String> params, WebAuthSession auth, String adminHeader) {
        String ownerUuid = auth.ownerUuid;
        List<WebAlertDto> alerts = WebAlertStore.instance()
            .getAlerts(ownerUuid);
        WebAlertsConfig rules = ConfigWebAlertsLoader.get();
        boolean canEdit = WebAuthAdminCheck.isAdmin(auth, adminHeader);
        WebAlertsConfig clientRules = WebhookDispatcher.sanitizeForClient(rules);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":" + alerts.size()
                + ",\"canEditRules\":"
                + canEdit
                + ",\"alerts\":"
                + GSON.toJson(alerts)
                + ",\"rules\":"
                + GSON.toJson(clientRules)
                + "}");
    }

    public static NanoHTTPD.Response handleGetRules(WebAuthSession auth, String adminHeader) {
        WebAlertsConfig rules = ConfigWebAlertsLoader.get();
        boolean canEdit = WebAuthAdminCheck.isAdmin(auth, adminHeader);
        WebAlertsConfig clientRules = WebhookDispatcher.sanitizeForClient(rules);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"canEditRules\":" + canEdit + ",\"rules\":" + GSON.toJson(clientRules) + "}");
    }

    public static NanoHTTPD.Response handlePutRules(String body, WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Admin permission required to edit alert rules\",\"code\":\"admin_required\"}");
        }
        if (body == null || body.trim()
            .isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing request body\",\"code\":\"missing_body\"}");
        }
        WebAlertsConfig incoming;
        try {
            incoming = GSON.fromJson(body, WebAlertsConfig.class);
        } catch (Exception e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid JSON body\",\"code\":\"invalid_json\"}");
        }
        String err = WebAlertsConfigValidator.validate(incoming);
        if (err != null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"" + escapeJson(err) + "\",\"code\":\"validation_error\"}");
        }
        WebAlertsConfig existing = ConfigWebAlertsLoader.get();
        incoming = WebAlertsConfigValidator.mergeWebhookSecrets(incoming, existing);
        if (!ConfigWebAlertsLoader.save(incoming)) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to save web-alerts.json\",\"code\":\"save_failed\"}");
        }
        WebAlertsConfig saved = ConfigWebAlertsLoader.get();
        WebAlertsConfig clientRules = WebhookDispatcher.sanitizeForClient(saved);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"canEditRules\":true,\"rules\":" + GSON.toJson(clientRules) + "}");
    }

    private static int parseIntParam(String raw, int defaultValue) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c >= 0x20) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
