package com.imgood.textech.webae.api.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.assistant.WebAssistantService;
import com.imgood.textech.webae.assistant.WebAssistantService.ClientAiContext;
import com.imgood.textech.webae.assistant.WebAssistantService.WebAssistantActionRequest;
import com.imgood.textech.webae.assistant.WebAssistantService.WebAssistantRequest;
import com.imgood.textech.webae.assistant.WebAssistantService.WebAssistantResult;
import com.imgood.textech.webae.auth.WebAuthSession;

import fi.iki.elonen.NanoHTTPD;

/** WebAE assistant query and explicit action-confirmation endpoints. */
public final class AssistantHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private AssistantHandler() {}

    public static NanoHTTPD.Response handleClientAiContext(String body) {
        try {
            ClientAiContextRequest request = GSON
                .fromJson(body == null || body.isEmpty() ? "{}" : body, ClientAiContextRequest.class);
            ClientAiContext context = WebAssistantService
                .clientAiContext(request == null ? null : request.locale, request == null ? null : request.text);
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"context\":" + GSON.toJson(context) + "}");
        } catch (IllegalStateException e) {
            return error(NanoHTTPD.Response.Status.CONFLICT, "browser_ai_disabled", e.getMessage());
        } catch (Exception e) {
            return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_json", "Invalid AI context request body.");
        }
    }

    public static NanoHTTPD.Response handleQuery(String body, WebAuthSession auth) {
        try {
            WebAssistantRequest request = GSON
                .fromJson(body == null || body.isEmpty() ? "{}" : body, WebAssistantRequest.class);
            WebAssistantResult result = WebAssistantService.handleQuery(auth, request);
            return json(NanoHTTPD.Response.Status.OK, GSON.toJson(result));
        } catch (Exception e) {
            return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_json", "Invalid assistant request body.");
        }
    }

    public static NanoHTTPD.Response handleAction(String body, WebAuthSession auth) {
        try {
            WebAssistantActionRequest request = GSON
                .fromJson(body == null || body.isEmpty() ? "{}" : body, WebAssistantActionRequest.class);
            WebAssistantResult result = WebAssistantService.confirm(auth, request);
            return json(NanoHTTPD.Response.Status.OK, GSON.toJson(result));
        } catch (Exception e) {
            return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_json", "Invalid assistant action body.");
        }
    }

    private static NanoHTTPD.Response error(NanoHTTPD.Response.Status status, String code, String message) {
        return json(status, "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static final class ClientAiContextRequest {

        String locale;
        String text;
    }
}
