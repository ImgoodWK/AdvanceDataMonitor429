package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.perf.WebAePerfProfiler;
import com.imgood.textech.webae.perf.WebAePerfProfiler.ConfigSummary;
import com.imgood.textech.webae.perf.WebAePerfProfiler.DiagnosticsSnapshot;
import com.imgood.textech.webae.perf.WebAePerfProfiler.PhaseView;
import com.imgood.textech.webae.perf.WebAePerfProfiler.RouteView;
import com.imgood.textech.webae.perf.WebAePerfProfiler.SlowHttpEntry;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/server/diagnostics — WebAE tick/HTTP/snapshot performance snapshot.
 */
public final class ServerDiagnosticsHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private ServerDiagnosticsHandler() {}

    public static NanoHTTPD.Response handle() {
        DiagnosticsSnapshot snap = WebAePerfProfiler.instance()
            .snapshot();
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            GSON.toJson(toJson(snap)));
    }

    private static DiagnosticsJson toJson(DiagnosticsSnapshot snap) {
        DiagnosticsJson json = new DiagnosticsJson();
        json.success = true;
        json.tps = round1(snap.tps);
        json.mspt = round1(snap.mspt);
        json.onlinePlayers = snap.onlinePlayers;
        json.uptimeSeconds = snap.uptimeSeconds;
        json.queueDepth = snap.queueDepth;
        json.tasksProcessedThisTick = snap.tasksProcessedThisTick;
        json.activeNetworks = snap.activeNetworks;
        json.snapshotCacheSize = snap.snapshotCacheSize;
        json.snapshotWorkerBusy = snap.snapshotWorkerBusy;
        json.snapshotTimeouts = snap.snapshotTimeouts;
        json.snapshotSkippedBusy = snap.snapshotSkippedBusy;
        json.snapshotSkippedQueue = snap.snapshotSkippedQueue;
        json.phases = snap.phases;
        json.collects = snap.collects;
        json.topRoutes = snap.topRoutes;
        json.slowHttp = new ArrayList<SlowHttpJson>();
        if (snap.slowHttp != null) {
            for (SlowHttpEntry e : snap.slowHttp) {
                SlowHttpJson s = new SlowHttpJson();
                s.ts = e.ts;
                s.route = e.route;
                s.durationMs = e.durationMs;
                json.slowHttp.add(s);
            }
        }
        json.history = new HistoryJson();
        json.history.timestamps = snap.historyTimestamps;
        json.history.queueDepth = snap.historyQueueDepth;
        json.history.serverTasksMs = snap.historyServerTasksMs;
        json.history.snapshotSchedulerMs = snap.historySnapshotSchedulerMs;
        json.config = snap.config;
        return json;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static final class DiagnosticsJson {

        boolean success;
        double tps;
        double mspt;
        int onlinePlayers;
        long uptimeSeconds;
        int queueDepth;
        int tasksProcessedThisTick;
        int activeNetworks;
        int snapshotCacheSize;
        boolean snapshotWorkerBusy;
        long snapshotTimeouts;
        long snapshotSkippedBusy;
        long snapshotSkippedQueue;
        Map<String, PhaseView> phases;
        Map<String, PhaseView> collects;
        List<RouteView> topRoutes;
        List<SlowHttpJson> slowHttp;
        HistoryJson history;
        ConfigSummary config;
    }

    private static final class SlowHttpJson {

        long ts;
        String route;
        long durationMs;
    }

    private static final class HistoryJson {

        List<Long> timestamps;
        List<Integer> queueDepth;
        List<Long> serverTasksMs;
        List<Long> snapshotSchedulerMs;
    }
}
