package com.imgood.textech.webae.api.handler;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.assistant.PlanStore;
import com.imgood.textech.assistant.PlanStore.PlanEntry;
import com.imgood.textech.webae.planner.PlannerFlowExporter;
import com.imgood.textech.webae.planner.PlannerFlowExporter.FlowRoot;

import fi.iki.elonen.NanoHTTPD;

/**
 * Planner REST API (Phase 4.4): read/write plans.json + flow export (Phase 4.3).
 */
public final class PlannerHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private PlannerHandler() {}

    public static NanoHTTPD.Response handleList(String ownerUuid) {
        List<PlanEntry> plans = PlanStore.instance()
            .listForOwner(ownerUuid);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":" + plans.size() + ",\"plans\":" + GSON.toJson(plans) + "}");
    }

    public static NanoHTTPD.Response handleCreate(String body, String ownerUuid) {
        CreateBody req = parseBody(body, CreateBody.class);
        if (req == null) {
            return badRequest("Invalid JSON body");
        }
        String title = req.title != null ? req.title.trim() : "";
        String rawText = req.rawText != null ? req.rawText.trim() : "";
        if (title.isEmpty() && rawText.isEmpty()) {
            return badRequest("title or rawText required");
        }
        PlanEntry created = PlanStore.instance()
            .webAdd(ownerUuid, title, rawText);
        if (created == null) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to create plan\"}");
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"plan\":" + GSON.toJson(created) + "}");
    }

    public static NanoHTTPD.Response handlePatch(String body, String ownerUuid, int planId) {
        if (planId <= 0) {
            return badRequest("Invalid plan id");
        }
        PatchBody req = parseBody(body, PatchBody.class);
        if (req == null) {
            return badRequest("Invalid JSON body");
        }
        PlanEntry updated = PlanStore.instance()
            .webUpdate(ownerUuid, planId, req.title, req.completed);
        if (updated == null) {
            return json(NanoHTTPD.Response.Status.NOT_FOUND, "{\"success\":false,\"message\":\"Plan not found\"}");
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"plan\":" + GSON.toJson(updated) + "}");
    }

    public static NanoHTTPD.Response handleDelete(String ownerUuid, int planId) {
        if (planId <= 0) {
            return badRequest("Invalid plan id");
        }
        boolean removed = PlanStore.instance()
            .webDelete(ownerUuid, planId);
        if (!removed) {
            return json(NanoHTTPD.Response.Status.NOT_FOUND, "{\"success\":false,\"message\":\"Plan not found\"}");
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true}");
    }

    public static NanoHTTPD.Response handleExportFlow(String body, String ownerUuid) {
        ExportBody req = parseBody(body, ExportBody.class);
        if (req == null || req.roots == null || req.roots.isEmpty()) {
            return badRequest("roots array required");
        }
        int networkId = req.networkId;
        String exported = PlannerFlowExporter.export(ownerUuid, networkId, req.roots, req.format);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"format\":\""
                + escapeJson(req.format != null && !req.format.isEmpty() ? req.format : "gtnh-flow-v1")
                + "\",\"export\":"
                + exported
                + "}");
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

    private static NanoHTTPD.Response badRequest(String message) {
        return json(
            NanoHTTPD.Response.Status.BAD_REQUEST,
            "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    public static int parsePlanIdFromUri(String uri) {
        if (uri == null || !uri.startsWith("/api/planner/plans/")) {
            return -1;
        }
        String tail = uri.substring("/api/planner/plans/".length());
        if (tail.isEmpty() || tail.contains("/")) {
            return -1;
        }
        try {
            return Integer.parseInt(tail.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static final class CreateBody {

        public String title = "";
        public String rawText = "";
    }

    private static final class PatchBody {

        public String title;
        public Boolean completed;
    }

    private static final class ExportBody {

        public int networkId;
        public String format = "gtnh-flow-v1";
        public List<FlowRoot> roots;
    }
}
