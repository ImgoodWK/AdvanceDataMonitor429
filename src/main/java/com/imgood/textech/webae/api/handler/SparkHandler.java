package com.imgood.textech.webae.api.handler;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.Config;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.spark.SparkProfile;
import com.imgood.textech.webae.spark.SparkProfileStore;
import com.imgood.textech.webae.spark.SparkAiAnalysisService;
import com.imgood.textech.webae.spark.SparkService;

import fi.iki.elonen.NanoHTTPD;

/** REST endpoints for the optional Spark profiler tab in the WebAE admin console. */
public final class SparkHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private SparkHandler() {}

    public static NanoHTTPD.Response handle(
        String uri,
        NanoHTTPD.Method method,
        String body,
        WebAuthSession auth,
        String adminHeader) {
        if (!SparkService.isEnabled()) {
            return json(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "{\"success\":false,\"code\":\"spark_unavailable\",\"message\":\"Spark is not installed or WebAE Spark is disabled.\"}");
        }
        if (method == NanoHTTPD.Method.GET && "/api/spark".equals(uri)) {
            return status();
        }
        if (method == NanoHTTPD.Method.GET && "/api/spark/history".equals(uri)) {
            return status();
        }
        if (uri.startsWith("/api/spark/history/") && method == NanoHTTPD.Method.GET) {
            String id = uri.substring("/api/spark/history/".length());
            SparkProfile profile = SparkProfileStore.instance().find(id);
            if (profile == null) return error(NanoHTTPD.Response.Status.NOT_FOUND, "spark_not_found", "Spark run not found.");
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"profile\":" + GSON.toJson(profile) + "}");
        }
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return error(NanoHTTPD.Response.Status.FORBIDDEN, "admin_required", "Admin permission is required for Spark profiler control.");
        }
        if (method == NanoHTTPD.Method.POST && "/api/spark/analyze".equals(uri)) {
            try {
                JsonObject request = new JsonParser().parse(body == null ? "{}" : body).getAsJsonObject();
                JsonArray idsJson = request.getAsJsonArray("profileIds");
                List<String> ids = new java.util.ArrayList<String>();
                if (idsJson != null) {
                    for (int i = 0; i < idsJson.size(); i++) ids.add(idsJson.get(i).getAsString());
                }
                String locale = request.has("locale") ? request.get("locale").getAsString() : "zh_CN";
                String aiSource = request.has("aiSource") ? request.get("aiSource").getAsString() : "";
                boolean browser;
                try {
                    browser = com.imgood.textech.webae.assistant.WebAiConfigStore.SOURCE_BROWSER
                        .equals(com.imgood.textech.webae.assistant.WebAiConfigStore.normalizeAiSource(aiSource));
                } catch (IllegalStateException e) {
                    return error(NanoHTTPD.Response.Status.CONFLICT, "ai_source_disabled", e.getMessage());
                }
                if (browser) {
                    return json(NanoHTTPD.Response.Status.OK,
                        "{\"success\":true,\"request\":"
                            + GSON.toJson(SparkAiAnalysisService.prepare(ids, locale)) + "}");
                }
                if (!com.imgood.textech.webae.assistant.WebAiConfigStore.isServerKeyEnabled()) {
                    return error(NanoHTTPD.Response.Status.CONFLICT, "server_ai_disabled",
                        "Server-side AI is disabled in config.");
                }
                return json(NanoHTTPD.Response.Status.OK,
                    "{\"success\":true,\"result\":" + GSON.toJson(SparkAiAnalysisService.analyze(ids, locale)) + "}");
            } catch (IllegalArgumentException e) {
                return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_spark_analysis", e.getMessage());
            } catch (IllegalStateException e) {
                return error(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "ai_not_configured", e.getMessage());
            } catch (Exception e) {
                return error(NanoHTTPD.Response.Status.INTERNAL_ERROR, "spark_ai_failed", safeMessage(e));
            }
        }
        if (method == NanoHTTPD.Method.POST && "/api/spark/profile".equals(uri)) {
            int duration = Config.webSparkDefaultDurationSeconds;
            String mode = "server";
            int intervalMillis = 0;
            int onlyTicksOverMillis = 0;
            try {
                if (body != null && !body.trim().isEmpty()) {
                    JsonObject request = new JsonParser().parse(body).getAsJsonObject();
                    if (request.has("durationSeconds")) duration = request.get("durationSeconds").getAsInt();
                    if (request.has("mode")) mode = request.get("mode").getAsString();
                    if (request.has("intervalMillis")) intervalMillis = request.get("intervalMillis").getAsInt();
                    if (request.has("onlyTicksOverMillis")) {
                        onlyTicksOverMillis = request.get("onlyTicksOverMillis").getAsInt();
                    }
                }
            } catch (Exception e) {
                return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_json", "Invalid Spark request body.");
            }
            try {
                SparkProfile profile = SparkService.start(
                    duration,
                    auth.actorName,
                    mode,
                    intervalMillis,
                    onlyTicksOverMillis);
                return json(NanoHTTPD.Response.Status.OK,
                    "{\"success\":true,\"profile\":" + GSON.toJson(profile) + "}");
            } catch (IllegalStateException e) {
                return error(NanoHTTPD.Response.Status.CONFLICT, "spark_busy", e.getMessage());
            } catch (Exception e) {
                return error(NanoHTTPD.Response.Status.INTERNAL_ERROR, "spark_start_failed", e.getMessage());
            }
        }
        if (method == NanoHTTPD.Method.POST && "/api/spark/stop".equals(uri)) {
            try {
                SparkProfile profile = SparkService.stop();
                return json(NanoHTTPD.Response.Status.OK,
                    "{\"success\":true,\"profile\":" + GSON.toJson(profile) + "}");
            } catch (Exception e) {
                return error(NanoHTTPD.Response.Status.INTERNAL_ERROR, "spark_stop_failed", e.getMessage());
            }
        }
        if (method == NanoHTTPD.Method.POST && "/api/spark/recover".equals(uri)) {
            String id;
            try {
                JsonObject request = new JsonParser().parse(body == null ? "{}" : body).getAsJsonObject();
                id = request.has("id") ? request.get("id").getAsString() : "";
            } catch (Exception e) {
                return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_json", "Invalid Spark recovery body.");
            }
            if (id.isEmpty()) {
                return error(NanoHTTPD.Response.Status.BAD_REQUEST, "spark_id_required", "Spark run id is required.");
            }
            SparkProfile profile = SparkService.recoverResult(id);
            if (profile == null) {
                return error(NanoHTTPD.Response.Status.NOT_FOUND, "spark_not_found", "Spark run not found.");
            }
            boolean recovered = profile.resultUrl != null && !profile.resultUrl.isEmpty();
            return json(NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"recovered\":" + recovered + ",\"profile\":" + GSON.toJson(profile) + "}");
        }
        if (method == NanoHTTPD.Method.DELETE && uri.startsWith("/api/spark/history/")) {
            String id = uri.substring("/api/spark/history/".length());
            SparkProfile profile = SparkProfileStore.instance().find(id);
            if (profile != null && profile.isActive()) {
                return error(NanoHTTPD.Response.Status.CONFLICT, "spark_active", "Stop the active Spark run before deleting it.");
            }
            boolean removed = SparkProfileStore.instance().remove(id);
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"removed\":" + removed + "}");
        }
        return error(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed", "Unsupported Spark endpoint or method.");
    }

    private static NanoHTTPD.Response status() {
        List<SparkProfile> profiles = SparkProfileStore.instance().all();
        SparkProfile current = SparkService.activeProfile();
        JsonArray history = new JsonArray();
        for (SparkProfile profile : profiles) {
            history.add(summary(profile));
        }
        return json(NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"enabled\":true,\"available\":true,\"adminOnly\":true"
                + ",\"defaultDurationSeconds\":" + Config.webSparkDefaultDurationSeconds
                + ",\"maxDurationSeconds\":" + Config.webSparkMaxDurationSeconds
                + ",\"minIntervalMillis\":2,\"maxIntervalMillis\":100"
                + ",\"minTickThresholdMillis\":25,\"maxTickThresholdMillis\":1000"
                + ",\"running\":" + (current != null)
                + ",\"current\":" + (current == null ? "null" : summary(current).toString())
                + ",\"history\":" + history.toString() + "}");
    }

    /** Keep the frequently-polled status route small; full output is detail-only. */
    private static JsonObject summary(SparkProfile profile) {
        JsonObject value = GSON.toJsonTree(profile).getAsJsonObject();
        value.addProperty("messageCount", profile.messages == null ? 0 : profile.messages.size());
        value.addProperty("baselineMessageCount", profile.baselineMessages == null ? 0 : profile.baselineMessages.size());
        value.addProperty("completionMessageCount", profile.completionMessages == null ? 0 : profile.completionMessages.size());
        value.addProperty("hotspotCount", profile.hotspots == null ? 0 : profile.hotspots.size());
        value.addProperty("categoryCount", profile.categories == null ? 0 : profile.categories.size());
        value.addProperty("threadCount", profile.threads == null ? 0 : profile.threads.size());
        value.remove("messages");
        value.remove("baselineMessages");
        value.remove("completionMessages");
        value.remove("hotspots");
        value.remove("categories");
        value.remove("threads");
        return value;
    }

    private static NanoHTTPD.Response error(NanoHTTPD.Response.Status status, String code, String message) {
        return json(status, "{\"success\":false,\"code\":\"" + escape(code)
            + "\",\"message\":\"" + escape(message) + "\"}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String safeMessage(Exception error) {
        String value = error == null || error.getMessage() == null ? "Spark AI analysis failed." : error.getMessage();
        value = value.replace('\r', ' ').replace('\n', ' ');
        return value.length() <= 400 ? value : value.substring(0, 400);
    }
}
