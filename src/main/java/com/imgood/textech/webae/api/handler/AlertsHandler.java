package com.imgood.textech.webae.api.handler;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.Config;
import com.imgood.textech.config.ConfigWebAlertsLoader;
import com.imgood.textech.webae.alerts.QqIdProbeService;
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
 * PUT /api/alerts/rules — persist {@code TeXTech/WebAE/web-alerts.json} (Admin only).
 * POST /api/alerts/test — enqueue one test for a saved external target (Admin only).
 * POST /api/alerts/qq-id-probe/start — start QQ gateway listen to capture target IDs (Admin only).
 * GET /api/alerts/qq-id-probe — probe session status + discoveries (Admin only).
 * POST /api/alerts/qq-id-probe/stop — stop the active probe session (Admin only).
 */
public final class AlertsHandler {

    /** Include transient *Configured mirrors in API responses; disk persistence still excludes them. */
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC)
        .serializeNulls()
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
        for (WebAlertDto alert : alerts) {
            if (alert != null) {
                alert.browserNotify = WebhookDispatcher.instance()
                    .shouldNotifyBrowser(ownerUuid, alert);
            }
        }
        WebAlertsConfig rules = ConfigWebAlertsLoader.get();
        boolean canEdit = WebAuthAdminCheck.isAdmin(auth, adminHeader);
        WebAlertsConfig clientRules = WebhookDispatcher.sanitizeForClient(rules);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":" + alerts.size()
                + ",\"serverFeatureEnabled\":"
                + Config.webAlertsEnabled
                + ",\"canEditRules\":"
                + canEdit
                + ",\"alerts\":"
                + GSON.toJson(alerts)
                + ",\"rules\":"
                + GSON.toJson(clientRules)
                + ",\"deliveryStatus\":"
                + GSON.toJson(
                    WebhookDispatcher.instance()
                        .getStatus())
                + "}");
    }

    public static NanoHTTPD.Response handleGetRules(WebAuthSession auth, String adminHeader) {
        WebAlertsConfig rules = ConfigWebAlertsLoader.get();
        boolean canEdit = WebAuthAdminCheck.isAdmin(auth, adminHeader);
        WebAlertsConfig clientRules = WebhookDispatcher.sanitizeForClient(rules);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"serverFeatureEnabled\":" + Config.webAlertsEnabled
                + ",\"canEditRules\":"
                + canEdit
                + ",\"rules\":"
                + GSON.toJson(clientRules)
                + ",\"deliveryStatus\":"
                + GSON.toJson(
                    WebhookDispatcher.instance()
                        .getStatus())
                + "}");
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
        WebAlertsConfig existing = ConfigWebAlertsLoader.get();
        incoming = WebAlertsConfigValidator.mergeWebhookSecrets(incoming, existing);
        String err = WebAlertsConfigValidator.validate(incoming);
        if (err != null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"" + escapeJson(err) + "\",\"code\":\"validation_error\"}");
        }
        if (!ConfigWebAlertsLoader.save(incoming)) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to save web-alerts.json\",\"code\":\"save_failed\"}");
        }
        WebAlertsConfig saved = ConfigWebAlertsLoader.get();
        WebAlertsConfig clientRules = WebhookDispatcher.sanitizeForClient(saved);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"serverFeatureEnabled\":" + Config.webAlertsEnabled
                + ",\"canEditRules\":true,\"rules\":"
                + GSON.toJson(clientRules)
                + ",\"deliveryStatus\":"
                + GSON.toJson(
                    WebhookDispatcher.instance()
                        .getStatus())
                + "}");
    }

    public static NanoHTTPD.Response handleTest(String body, WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Admin permission required to test alert delivery\",\"code\":\"admin_required\"}");
        }
        AlertTestRequest request;
        try {
            request = GSON.fromJson(body, AlertTestRequest.class);
        } catch (Exception e) {
            request = null;
        }
        if (request == null || request.id == null
            || request.id.trim()
                .isEmpty()
            || (!"target".equals(request.kind) && !"webhook".equals(request.kind))) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"kind must be target or webhook and id is required\",\"code\":\"invalid_test_target\"}");
        }

        WebhookDispatcher.TestEnqueueResult result = WebhookDispatcher.instance()
            .enqueueTest(auth.ownerUuid, request.kind, request.id.trim());
        if (result == WebhookDispatcher.TestEnqueueResult.FEATURE_DISABLED) {
            return json(
                NanoHTTPD.Response.Status.CONFLICT,
                "{\"success\":false,\"message\":\"[webConsole] alertsEnabled is disabled\",\"code\":\"alerts_feature_disabled\"}");
        }
        if (result == WebhookDispatcher.TestEnqueueResult.NOT_FOUND) {
            return json(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Saved alert target not found\",\"code\":\"alert_target_not_found\"}");
        }
        if (result == WebhookDispatcher.TestEnqueueResult.CIRCUIT_OPEN) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"Target circuit breaker is open; retry later\",\"code\":\"alert_target_circuit_open\"}");
        }
        if (result == WebhookDispatcher.TestEnqueueResult.QUEUE_FULL) {
            return json(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"message\":\"Alert delivery queue is full\",\"code\":\"alert_delivery_queue_full\"}");
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"queued\":true,\"deliveryStatus\":" + GSON.toJson(
                WebhookDispatcher.instance()
                    .getStatus())
                + "}");
    }

    public static NanoHTTPD.Response handleQqIdProbeStart(String body, WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Admin permission required to probe QQ target IDs\",\"code\":\"admin_required\"}");
        }
        QqIdProbeStartRequest request;
        try {
            request = GSON.fromJson(body == null ? "{}" : body, QqIdProbeStartRequest.class);
        } catch (Exception e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid JSON body\",\"code\":\"invalid_json\"}");
        }
        if (request == null) {
            request = new QqIdProbeStartRequest();
        }
        ResolvedQqCredentials credentials = resolveQqProbeCredentials(request);
        if (credentials.error != null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"" + escapeJson(credentials.error)
                    + "\",\"code\":\"invalid_qq_probe\"}");
        }
        QqIdProbeService.StartResult started = QqIdProbeService.instance()
            .start(credentials.appId, credentials.appSecret, credentials.baseUrl, credentials.tokenUrl, 0L);
        if (!started.success) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"" + escapeJson(started.error)
                    + "\",\"code\":\"qq_probe_start_failed\"}");
        }
        return qqProbeStatusResponse();
    }

    public static NanoHTTPD.Response handleQqIdProbeStatus(WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Admin permission required to view QQ ID probe status\",\"code\":\"admin_required\"}");
        }
        return qqProbeStatusResponse();
    }

    public static NanoHTTPD.Response handleQqIdProbeStop(WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Admin permission required to stop QQ ID probe\",\"code\":\"admin_required\"}");
        }
        QqIdProbeService.instance()
            .stop();
        return qqProbeStatusResponse();
    }

    private static NanoHTTPD.Response qqProbeStatusResponse() {
        QqIdProbeService.Status status = QqIdProbeService.instance()
            .snapshot();
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"running\":" + status.running
                + ",\"phase\":"
                + GSON.toJson(status.phase)
                + ",\"error\":"
                + GSON.toJson(status.error)
                + ",\"startedAtMs\":"
                + status.startedAtMs
                + ",\"expiresAtMs\":"
                + status.expiresAtMs
                + ",\"discoveries\":"
                + GSON.toJson(status.discoveries)
                + "}");
    }

    private static ResolvedQqCredentials resolveQqProbeCredentials(QqIdProbeStartRequest request) {
        String appId = trim(request.appId);
        String appSecret = trim(request.appSecret);
        String baseUrl = trim(request.baseUrl);
        String tokenUrl = trim(request.tokenUrl);
        String targetConfigId = trim(request.targetConfigId);

        WebAlertsConfig.NotificationTarget saved = null;
        if (!targetConfigId.isEmpty()) {
            saved = findQqTarget(ConfigWebAlertsLoader.get(), targetConfigId);
            if (saved == null) {
                return ResolvedQqCredentials.fail("Saved QQ notification target not found: " + targetConfigId);
            }
        }

        if (appId.isEmpty() && saved != null) {
            appId = trim(saved.appId);
        }
        if ((appSecret.isEmpty() || appSecret.startsWith("***")) && saved != null) {
            appSecret = trim(saved.appSecret);
        }
        if (baseUrl.isEmpty() && saved != null) {
            baseUrl = trim(saved.baseUrl);
        }
        if (tokenUrl.isEmpty() && saved != null) {
            tokenUrl = trim(saved.tokenUrl);
        }

        if (appId.isEmpty() || appSecret.isEmpty() || appSecret.startsWith("***")) {
            return ResolvedQqCredentials.fail(
                "QQ probe requires appId and a real ClientSecret (save the target first if using a masked secret)");
        }
        return ResolvedQqCredentials.ok(appId, appSecret, baseUrl, tokenUrl);
    }

    private static WebAlertsConfig.NotificationTarget findQqTarget(WebAlertsConfig cfg, String id) {
        if (cfg == null || cfg.notificationTargets == null || id == null) {
            return null;
        }
        for (WebAlertsConfig.NotificationTarget target : cfg.notificationTargets) {
            if (target != null && id.equals(trim(target.id)) && "qq_official".equalsIgnoreCase(trim(target.type))) {
                return target;
            }
        }
        return null;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AlertTestRequest {

        String kind;
        String id;
    }

    private static final class QqIdProbeStartRequest {

        String appId;
        String appSecret;
        String baseUrl;
        String tokenUrl;
        String targetConfigId;
    }

    private static final class ResolvedQqCredentials {

        final String appId;
        final String appSecret;
        final String baseUrl;
        final String tokenUrl;
        final String error;

        private ResolvedQqCredentials(String appId, String appSecret, String baseUrl, String tokenUrl, String error) {
            this.appId = appId;
            this.appSecret = appSecret;
            this.baseUrl = baseUrl;
            this.tokenUrl = tokenUrl;
            this.error = error;
        }

        static ResolvedQqCredentials ok(String appId, String appSecret, String baseUrl, String tokenUrl) {
            return new ResolvedQqCredentials(appId, appSecret, baseUrl, tokenUrl, null);
        }

        static ResolvedQqCredentials fail(String error) {
            return new ResolvedQqCredentials("", "", "", "", error);
        }
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
