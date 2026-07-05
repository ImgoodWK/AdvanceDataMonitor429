package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.config.ConfigWebOrderTemplatesLoader;
import com.imgood.textech.webae.order.WebOrderTemplate;
import com.imgood.textech.webae.order.WebOrderTemplatesValidator;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/order/templates — list batch order presets for the authenticated owner.
 * PUT /api/order/templates — replace the owner's template list (body {@code { "templates": [...] }}).
 */
public final class OrderTemplatesHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private OrderTemplatesHandler() {}

    public static NanoHTTPD.Response handleGet(String ownerUuid) {
        List<WebOrderTemplate> templates = ConfigWebOrderTemplatesLoader.getForOwner(ownerUuid);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":"
                + templates.size()
                + ",\"templates\":"
                + GSON.toJson(templates)
                + "}");
    }

    public static NanoHTTPD.Response handlePut(String body, String ownerUuid) {
        if (body == null || body.trim()
            .isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing request body\",\"code\":\"missing_body\"}");
        }
        PutBody incoming;
        try {
            incoming = GSON.fromJson(body, PutBody.class);
        } catch (Exception e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid JSON body\",\"code\":\"invalid_json\"}");
        }
        List<WebOrderTemplate> templates = incoming != null && incoming.templates != null
            ? incoming.templates
            : new ArrayList<WebOrderTemplate>();
        String err = WebOrderTemplatesValidator.validateOwnerTemplates(templates);
        if (err != null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\""
                    + escapeJson(err)
                    + "\",\"code\":\"validation_error\"}");
        }
        if (!ConfigWebOrderTemplatesLoader.saveForOwner(ownerUuid, templates)) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to save web-order-templates.json\",\"code\":\"save_failed\"}");
        }
        List<WebOrderTemplate> saved = ConfigWebOrderTemplatesLoader.getForOwner(ownerUuid);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":"
                + saved.size()
                + ",\"templates\":"
                + GSON.toJson(saved)
                + "}");
    }

    private static final class PutBody {

        List<WebOrderTemplate> templates;
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
