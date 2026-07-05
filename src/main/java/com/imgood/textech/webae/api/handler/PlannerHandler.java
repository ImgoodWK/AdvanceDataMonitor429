package com.imgood.textech.webae.api.handler;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.assistant.PlanStore;
import com.imgood.textech.assistant.PlanStore.PlanEntry;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/planner/plans — read-only plan list from config/textech/plans.json.
 */
public final class PlannerHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private PlannerHandler() {}

    public static NanoHTTPD.Response handle(String ownerUuid) {
        List<PlanEntry> plans = PlanStore.instance()
            .listForOwner(ownerUuid);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"count\":"
                + plans.size()
                + ",\"plans\":"
                + GSON.toJson(plans)
                + "}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
