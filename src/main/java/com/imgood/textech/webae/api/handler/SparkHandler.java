package com.imgood.textech.webae.api.handler;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.Config;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.spark.SparkProfile;
import com.imgood.textech.webae.spark.SparkProfileStore;
import com.imgood.textech.webae.spark.SparkService;

import fi.iki.elonen.NanoHTTPD;

/** REST endpoints for the optional WebAE Spark profiler page. */
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
        if (method == NanoHTTPD.Method.POST && "/api/spark/profile".equals(uri)) {
            int duration = Config.webSparkDefaultDurationSeconds;
            try {
                if (body != null && !body.trim().isEmpty()) {
                    JsonObject request = new JsonParser().parse(body).getAsJsonObject();
                    if (request.has("durationSeconds")) duration = request.get("durationSeconds").getAsInt();
                }
            } catch (Exception e) {
                return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_json", "Invalid Spark request body.");
            }
            try {
                SparkProfile profile = SparkService.start(duration, auth.actorName);
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
        if (method == NanoHTTPD.Method.DELETE && uri.startsWith("/api/spark/history/")) {
            String id = uri.substring("/api/spark/history/".length());
            boolean removed = SparkProfileStore.instance().remove(id);
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"removed\":" + removed + "}");
        }
        return error(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed", "Unsupported Spark endpoint or method.");
    }

    private static NanoHTTPD.Response status() {
        List<SparkProfile> profiles = SparkProfileStore.instance().all();
        SparkProfile current = SparkService.activeProfile();
        return json(NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"enabled\":true,\"available\":true,\"adminOnly\":true"
                + ",\"defaultDurationSeconds\":" + Config.webSparkDefaultDurationSeconds
                + ",\"maxDurationSeconds\":" + Config.webSparkMaxDurationSeconds
                + ",\"running\":" + (current != null)
                + ",\"current\":" + GSON.toJson(current)
                + ",\"history\":" + GSON.toJson(profiles) + "}");
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
}
