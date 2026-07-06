package com.imgood.textech.webae.api.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.health.ServerHealthSampler;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/server/health — read-only TPS / MSPT / uptime snapshot.
 */
public final class ServerHealthHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private ServerHealthHandler() {}

    public static NanoHTTPD.Response handle() {
        ServerHealthSampler.HealthSnapshot snap = ServerHealthSampler.instance()
            .snapshot();
        return NanoHTTPD
            .newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", GSON.toJson(snapToJson(snap)));
    }

    private static HealthJson snapToJson(ServerHealthSampler.HealthSnapshot snap) {
        HealthJson json = new HealthJson();
        json.success = true;
        json.tps = round1(snap.tps);
        json.mspt = round1(snap.mspt);
        json.onlinePlayers = snap.onlinePlayers;
        json.uptimeSeconds = snap.uptimeSeconds;
        json.history = new HistoryJson();
        json.history.tps = snap.tpsHistory;
        json.history.mspt = snap.msptHistory;
        json.history.timestamps = snap.timestamps;
        return json;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static final class HealthJson {

        boolean success;
        double tps;
        double mspt;
        int onlinePlayers;
        long uptimeSeconds;
        HistoryJson history;
    }

    private static final class HistoryJson {

        java.util.List<Double> tps;
        java.util.List<Double> mspt;
        java.util.List<Long> timestamps;
    }
}
