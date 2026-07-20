package com.imgood.textech.webae.api.handler;

import java.util.Collections;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.imgood.textech.webae.assistant.WebAiConfigStore;
import com.imgood.textech.webae.assistant.WebAiConfigStore.RuntimeConfig;
import com.imgood.textech.webae.assistant.WebAiConfigStore.UpdateRequest;
import com.imgood.textech.webae.assistant.WebAiHttpClient;
import com.imgood.textech.webae.assistant.WebAiHttpClient.Message;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;

import fi.iki.elonen.NanoHTTPD;

/** Admin-only API for WebAE AI provider and secret management. */
public final class WebAiAdminHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private WebAiAdminHandler() {}

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.Method method, String body, WebAuthSession auth,
        String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return error(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "admin_required",
                "Admin permission is required to manage AI settings.");
        }
        if (!WebAiConfigStore.isServerKeyEnabled()) {
            return error(
                NanoHTTPD.Response.Status.CONFLICT,
                "server_ai_disabled",
                "Server-side AI key management is disabled in config (aiServerKeyEnabled=false).");
        }
        try {
            if ("/api/admin/ai/settings".equals(uri) && method == NanoHTTPD.Method.GET) {
                return ok(
                    "settings",
                    WebAiConfigStore.instance()
                        .view());
            }
            if ("/api/admin/ai/settings".equals(uri) && method == NanoHTTPD.Method.PUT) {
                UpdateRequest request = GSON.fromJson(body == null ? "{}" : body, UpdateRequest.class);
                return ok(
                    "settings",
                    WebAiConfigStore.instance()
                        .update(request, auth.actorName));
            }
            if ("/api/admin/ai/key".equals(uri) && method == NanoHTTPD.Method.DELETE) {
                TestRequest deleteBody = GSON.fromJson(body == null || body.isEmpty() ? "{}" : body, TestRequest.class);
                if (deleteBody != null && deleteBody.profileId != null
                    && !deleteBody.profileId.trim()
                        .isEmpty()) {
                    return ok(
                        "settings",
                        WebAiConfigStore.instance()
                            .clearProfileApiKey(deleteBody.profileId, auth.actorName));
                }
                return ok(
                    "settings",
                    WebAiConfigStore.instance()
                        .clearApiKey(auth.actorName));
            }
            if ("/api/admin/ai/test".equals(uri) && method == NanoHTTPD.Method.POST) {
                TestRequest request = GSON.fromJson(body == null || body.isEmpty() ? "{}" : body, TestRequest.class);
                RuntimeConfig runtime = null;
                if (request != null && request.profileId != null
                    && !request.profileId.trim()
                        .isEmpty()) {
                    runtime = WebAiConfigStore.instance()
                        .runtimeById(request.profileId.trim());
                } else {
                    runtime = WebAiConfigStore.instance()
                        .runtime();
                }
                if (runtime == null) {
                    return error(
                        NanoHTTPD.Response.Status.BAD_REQUEST,
                        "ai_not_configured",
                        "Enable AI and save an API key first.");
                }
                String reply = new WebAiHttpClient(runtime)
                    .complete(Collections.singletonList(new Message("user", "Reply with exactly: OK")));
                JsonObject value = new JsonObject();
                value.addProperty("profileId", runtime.id);
                value.addProperty("providerId", runtime.providerId);
                value.addProperty("model", runtime.model);
                value.addProperty(
                    "accepted",
                    !reply.trim()
                        .isEmpty());
                return ok("test", value);
            }
            return error(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "method_not_allowed",
                "Unsupported Web AI management endpoint or method.");
        } catch (IllegalArgumentException e) {
            return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_ai_settings", e.getMessage());
        } catch (IllegalStateException e) {
            return error(NanoHTTPD.Response.Status.CONFLICT, "server_ai_disabled", e.getMessage());
        } catch (Exception e) {
            return error(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "ai_provider_failed", safeError(e));
        }
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
        response.addProperty("message", message == null ? "Request failed." : message);
        return json(status, response.toString());
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static String safeError(Exception e) {
        String value = e == null || e.getMessage() == null ? "AI provider request failed." : e.getMessage();
        return value.length() <= 400 ? value : value.substring(0, 400);
    }

    private static final class TestRequest {

        String profileId;
    }
}
