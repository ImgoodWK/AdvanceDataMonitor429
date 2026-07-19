package com.imgood.textech.webae.perf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.imgood.textech.Config;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.debug.WebAeDebugLog;
import com.imgood.textech.webae.health.ServerHealthSampler;

/**
 * Lightweight WebAE performance profiler: tick-phase timings, HTTP route stats,
 * slow-request ring buffer, and snapshot collect durations. JVM 8 friendly.
 */
public final class WebAePerfProfiler {

    public static final String PHASE_SERVER_TASKS = "serverTasks";
    public static final String PHASE_SNAPSHOT_SCHEDULER = "snapshotScheduler";
    public static final String PHASE_POWER_SAMPLER = "powerSampler";
    public static final String PHASE_METRIC_SAMPLER = "metricSampler";
    public static final String PHASE_ICON_QUEUE = "iconMissingQueue";
    public static final String PHASE_WORLD_MAP_TILE = "worldMapTileQueue";
    public static final String PHASE_WORLD_MAP_CAPTURE = "worldMapCapture";
    public static final String PHASE_ALERT_ENGINE = "webAlertEngine";
    public static final String PHASE_QQ_BOT = "qqBot";
    public static final String PHASE_MISC = "misc";

    private static final WebAePerfProfiler INSTANCE = new WebAePerfProfiler();

    private static final long WINDOW_MS = 300_000L;
    private static final long SAMPLE_INTERVAL_MS = 1000L;
    private static final int MAX_HISTORY = 360;
    private static final int MAX_SLOW_HTTP = 50;
    private static final int MAX_ROUTE_STATS = 64;
    private static final long HTTP_SLOW_THRESHOLD_MS = 50L;
    private static final long HTTP_HARD_LOG_MS = 200L;
    private static final long TICK_PHASE_HARD_LOG_MS = 5L;
    private static final long SUMMARY_INTERVAL_MS = 10_000L;
    private static final long HARD_LOG_COOLDOWN_MS = 2_000L;

    private final AtomicInteger queueDepth = new AtomicInteger(0);
    private volatile int lastTasksProcessed;

    private final ConcurrentHashMap<String, PhaseStats> phases = new ConcurrentHashMap<String, PhaseStats>();
    private final ConcurrentHashMap<String, CollectStats> collects = new ConcurrentHashMap<String, CollectStats>();
    private final ConcurrentHashMap<String, RouteStats> routes = new ConcurrentHashMap<String, RouteStats>();

    private final Deque<SlowHttpEntry> slowHttp = new ArrayDeque<SlowHttpEntry>();
    private final Deque<HistoryPoint> history = new ArrayDeque<HistoryPoint>();

    private long lastSampleMs;
    private long lastSummaryLogMs;
    private long lastHardLogMs;

    private WebAePerfProfiler() {
        ensurePhase(PHASE_SERVER_TASKS);
        ensurePhase(PHASE_SNAPSHOT_SCHEDULER);
        ensurePhase(PHASE_POWER_SAMPLER);
        ensurePhase(PHASE_METRIC_SAMPLER);
        ensurePhase(PHASE_ICON_QUEUE);
        ensurePhase(PHASE_WORLD_MAP_TILE);
        ensurePhase(PHASE_WORLD_MAP_CAPTURE);
        ensurePhase(PHASE_ALERT_ENGINE);
        ensurePhase(PHASE_QQ_BOT);
        ensurePhase(PHASE_MISC);
    }

    public static WebAePerfProfiler instance() {
        return INSTANCE;
    }

    public void onTaskEnqueued() {
        queueDepth.incrementAndGet();
    }

    public void onTaskDequeued() {
        queueDepth.decrementAndGet();
    }

    public int getQueueDepth() {
        int d = queueDepth.get();
        return d < 0 ? 0 : d;
    }

    public int getLastTasksProcessed() {
        return lastTasksProcessed;
    }

    public void setLastTasksProcessed(int n) {
        lastTasksProcessed = n;
    }

    public long begin() {
        return System.nanoTime();
    }

    public void endPhase(String phase, long startNanos) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        if (ms < 0L) {
            ms = 0L;
        }
        PhaseStats stats = ensurePhase(phase);
        synchronized (stats) {
            stats.lastMs = ms;
            stats.totalMs += ms;
            stats.count++;
            if (ms > stats.maxMs) {
                stats.maxMs = ms;
            }
            // EMA ~ alpha 0.2
            if (stats.avgMs <= 0.0) {
                stats.avgMs = (double) ms;
            } else {
                stats.avgMs = stats.avgMs * 0.8 + (double) ms * 0.2;
            }
        }
        maybeHardLogTick(phase, ms);
    }

    public void recordCollect(String dataType, long durationMs) {
        if (dataType == null) {
            return;
        }
        if (durationMs < 0L) {
            durationMs = 0L;
        }
        CollectStats stats = collects.get(dataType);
        if (stats == null) {
            CollectStats created = new CollectStats();
            CollectStats prev = collects.putIfAbsent(dataType, created);
            stats = prev != null ? prev : created;
        }
        synchronized (stats) {
            stats.lastMs = durationMs;
            stats.totalMs += durationMs;
            stats.count++;
            if (durationMs > stats.maxMs) {
                stats.maxMs = durationMs;
            }
            if (stats.avgMs <= 0.0) {
                stats.avgMs = (double) durationMs;
            } else {
                stats.avgMs = stats.avgMs * 0.8 + (double) durationMs * 0.2;
            }
        }
    }

    public void recordHttp(String route, long durationMs) {
        if (route == null) {
            route = "?";
        }
        if (durationMs < 0L) {
            durationMs = 0L;
        }
        RouteStats stats = routes.get(route);
        if (stats == null) {
            if (routes.size() >= MAX_ROUTE_STATS && !routes.containsKey(route)) {
                // Drop least useful: skip recording new routes when full
            } else {
                RouteStats created = new RouteStats();
                created.route = route;
                RouteStats prev = routes.putIfAbsent(route, created);
                stats = prev != null ? prev : created;
            }
        }
        if (stats != null) {
            synchronized (stats) {
                stats.count++;
                stats.totalMs += durationMs;
                if (durationMs > stats.maxMs) {
                    stats.maxMs = durationMs;
                }
            }
        }
        if (durationMs >= HTTP_SLOW_THRESHOLD_MS) {
            synchronized (slowHttp) {
                slowHttp.addLast(new SlowHttpEntry(System.currentTimeMillis(), route, durationMs));
                while (slowHttp.size() > MAX_SLOW_HTTP) {
                    slowHttp.pollFirst();
                }
            }
        }
        maybeHardLogHttp(route, durationMs);
    }

    /** Called once per server tick END after phase timings recorded. */
    public void onTickEnd() {
        long now = System.currentTimeMillis();
        if (now - lastSampleMs >= SAMPLE_INTERVAL_MS) {
            lastSampleMs = now;
            HistoryPoint p = new HistoryPoint();
            p.ts = now;
            p.queueDepth = getQueueDepth();
            p.mspt = ServerHealthSampler.instance()
                .getLatestMspt();
            p.tps = ServerHealthSampler.instance()
                .getLatestTps();
            PhaseStats st = phases.get(PHASE_SERVER_TASKS);
            if (st != null) {
                synchronized (st) {
                    p.serverTasksMs = st.lastMs;
                }
            }
            PhaseStats ss = phases.get(PHASE_SNAPSHOT_SCHEDULER);
            if (ss != null) {
                synchronized (ss) {
                    p.snapshotSchedulerMs = ss.lastMs;
                }
            }
            synchronized (history) {
                history.addLast(p);
                long cutoff = now - WINDOW_MS;
                while (!history.isEmpty() && history.peekFirst().ts < cutoff) {
                    history.pollFirst();
                }
                while (history.size() > MAX_HISTORY) {
                    history.pollFirst();
                }
            }
        }
        if (Config.webDebugPerf && now - lastSummaryLogMs >= SUMMARY_INTERVAL_MS) {
            lastSummaryLogMs = now;
            logSummary();
        }
    }

    public DiagnosticsSnapshot snapshot() {
        DiagnosticsSnapshot out = new DiagnosticsSnapshot();
        ServerHealthSampler.HealthSnapshot health = ServerHealthSampler.instance()
            .snapshot();
        out.tps = health.tps;
        out.mspt = health.mspt;
        out.onlinePlayers = health.onlinePlayers;
        out.uptimeSeconds = health.uptimeSeconds;
        out.queueDepth = getQueueDepth();
        out.tasksProcessedThisTick = lastTasksProcessed;
        out.activeNetworks = SnapshotScheduler.activeNetworkCount();
        out.snapshotCacheSize = SnapshotCache.instance()
            .size();
        out.snapshotWorkerBusy = SnapshotWorkerPool.isBusy();
        out.snapshotTimeouts = SnapshotWorkerPool.getTimeoutCount();
        out.snapshotSkippedBusy = SnapshotWorkerPool.getSkipBusyCount();
        out.snapshotSkippedQueue = SnapshotWorkerPool.getSkipQueueCount();

        out.phases = new LinkedHashMap<String, PhaseView>();
        for (Map.Entry<String, PhaseStats> e : phases.entrySet()) {
            PhaseStats s = e.getValue();
            PhaseView v = new PhaseView();
            synchronized (s) {
                v.lastMs = s.lastMs;
                v.avgMs = round1(s.avgMs);
                v.maxMs = s.maxMs;
                v.count = s.count;
            }
            out.phases.put(e.getKey(), v);
        }

        out.collects = new LinkedHashMap<String, PhaseView>();
        for (Map.Entry<String, CollectStats> e : collects.entrySet()) {
            CollectStats s = e.getValue();
            PhaseView v = new PhaseView();
            synchronized (s) {
                v.lastMs = s.lastMs;
                v.avgMs = round1(s.avgMs);
                v.maxMs = s.maxMs;
                v.count = s.count;
            }
            out.collects.put(e.getKey(), v);
        }

        List<RouteView> routeList = new ArrayList<RouteView>();
        for (RouteStats s : routes.values()) {
            RouteView v = new RouteView();
            synchronized (s) {
                v.route = s.route;
                v.count = s.count;
                v.totalMs = s.totalMs;
                v.maxMs = s.maxMs;
                v.avgMs = s.count > 0 ? round1((double) s.totalMs / (double) s.count) : 0.0;
            }
            routeList.add(v);
        }
        // Sort by totalMs desc (simple insertion for JVM8)
        for (int i = 1; i < routeList.size(); i++) {
            RouteView key = routeList.get(i);
            int j = i - 1;
            while (j >= 0 && routeList.get(j).totalMs < key.totalMs) {
                routeList.set(j + 1, routeList.get(j));
                j--;
            }
            routeList.set(j + 1, key);
        }
        if (routeList.size() > 20) {
            out.topRoutes = new ArrayList<RouteView>(routeList.subList(0, 20));
        } else {
            out.topRoutes = routeList;
        }

        out.slowHttp = new ArrayList<SlowHttpEntry>();
        synchronized (slowHttp) {
            for (SlowHttpEntry e : slowHttp) {
                out.slowHttp.add(e);
            }
        }

        out.historyTimestamps = new ArrayList<Long>();
        out.historyQueueDepth = new ArrayList<Integer>();
        out.historyServerTasksMs = new ArrayList<Long>();
        out.historySnapshotSchedulerMs = new ArrayList<Long>();
        synchronized (history) {
            for (HistoryPoint p : history) {
                out.historyTimestamps.add(p.ts);
                out.historyQueueDepth.add(p.queueDepth);
                out.historyServerTasksMs.add(p.serverTasksMs);
                out.historySnapshotSchedulerMs.add(p.snapshotSchedulerMs);
            }
        }

        out.config = new ConfigSummary();
        out.config.refreshIntervalMs = Config.webRefreshIntervalMs;
        out.config.gtRefreshIntervalMs = Config.webGtRefreshIntervalMs;
        out.config.metricSampleIntervalMs = Config.webMetricSampleIntervalMs;
        out.config.patternCacheTtlMs = Config.webPatternCacheTtlMs;
        out.config.topologyCacheTtlMs = Config.webTopologyCacheTtlMs;
        out.config.worldMapTileBudgetPerTick = Config.webWorldMapTileBudgetPerTick;
        out.config.iconRenderPerTick = Config.webIconRenderPerTick;
        out.config.perfDebugEnabled = Config.webDebugPerf;
        return out;
    }

    private PhaseStats ensurePhase(String name) {
        PhaseStats existing = phases.get(name);
        if (existing != null) {
            return existing;
        }
        PhaseStats created = new PhaseStats();
        PhaseStats prev = phases.putIfAbsent(name, created);
        return prev != null ? prev : created;
    }

    private void maybeHardLogTick(String phase, long ms) {
        if (ms < TICK_PHASE_HARD_LOG_MS) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHardLogMs < HARD_LOG_COOLDOWN_MS) {
            return;
        }
        lastHardLogMs = now;
        WebAeDebugLog.infoAlways(
            WebAeDebugLog.Feature.PERF,
            "slow tick phase {}={}ms queueDepth={}",
            phase,
            Long.valueOf(ms),
            Integer.valueOf(getQueueDepth()));
    }

    private void maybeHardLogHttp(String route, long ms) {
        if (ms < HTTP_HARD_LOG_MS) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHardLogMs < HARD_LOG_COOLDOWN_MS) {
            return;
        }
        lastHardLogMs = now;
        WebAeDebugLog.infoAlways(
            WebAeDebugLog.Feature.PERF,
            "slow http route={} {}ms",
            route,
            Long.valueOf(ms));
    }

    private void logSummary() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("summary tps=")
            .append(round1(ServerHealthSampler.instance().getLatestTps()))
            .append(" mspt=")
            .append(round1(ServerHealthSampler.instance().getLatestMspt()))
            .append(" queue=")
            .append(getQueueDepth())
            .append(" activeNets=")
            .append(SnapshotScheduler.activeNetworkCount());
        for (String name : new String[] { PHASE_SERVER_TASKS, PHASE_SNAPSHOT_SCHEDULER, PHASE_WORLD_MAP_TILE,
            PHASE_QQ_BOT, PHASE_MISC }) {
            PhaseStats s = phases.get(name);
            if (s != null) {
                synchronized (s) {
                    sb.append(' ')
                        .append(name)
                        .append('=')
                        .append(s.lastMs)
                        .append("ms");
                }
            }
        }
        int slowCount;
        synchronized (slowHttp) {
            slowCount = slowHttp.size();
        }
        sb.append(" slowHttpBuffered=")
            .append(slowCount);
        WebAeDebugLog.info(WebAeDebugLog.Feature.PERF, sb.toString());
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static final class PhaseStats {

        long lastMs;
        long maxMs;
        long totalMs;
        long count;
        double avgMs;
    }

    private static final class CollectStats {

        long lastMs;
        long maxMs;
        long totalMs;
        long count;
        double avgMs;
    }

    private static final class RouteStats {

        String route;
        long count;
        long totalMs;
        long maxMs;
    }

    private static final class HistoryPoint {

        long ts;
        int queueDepth;
        double tps;
        double mspt;
        long serverTasksMs;
        long snapshotSchedulerMs;
    }

    public static final class SlowHttpEntry {

        public final long ts;
        public final String route;
        public final long durationMs;

        SlowHttpEntry(long ts, String route, long durationMs) {
            this.ts = ts;
            this.route = route;
            this.durationMs = durationMs;
        }
    }

    public static final class PhaseView {

        public long lastMs;
        public double avgMs;
        public long maxMs;
        public long count;
    }

    public static final class RouteView {

        public String route;
        public long count;
        public long totalMs;
        public long maxMs;
        public double avgMs;
    }

    public static final class ConfigSummary {

        public int refreshIntervalMs;
        public int gtRefreshIntervalMs;
        public int metricSampleIntervalMs;
        public int patternCacheTtlMs;
        public int topologyCacheTtlMs;
        public int worldMapTileBudgetPerTick;
        public int iconRenderPerTick;
        public boolean perfDebugEnabled;
    }

    public static final class DiagnosticsSnapshot {

        public double tps;
        public double mspt;
        public int onlinePlayers;
        public long uptimeSeconds;
        public int queueDepth;
        public int tasksProcessedThisTick;
        public int activeNetworks;
        public int snapshotCacheSize;
        public boolean snapshotWorkerBusy;
        public long snapshotTimeouts;
        public long snapshotSkippedBusy;
        public long snapshotSkippedQueue;
        public Map<String, PhaseView> phases = new HashMap<String, PhaseView>();
        public Map<String, PhaseView> collects = new HashMap<String, PhaseView>();
        public List<RouteView> topRoutes = new ArrayList<RouteView>();
        public List<SlowHttpEntry> slowHttp = new ArrayList<SlowHttpEntry>();
        public List<Long> historyTimestamps = new ArrayList<Long>();
        public List<Integer> historyQueueDepth = new ArrayList<Integer>();
        public List<Long> historyServerTasksMs = new ArrayList<Long>();
        public List<Long> historySnapshotSchedulerMs = new ArrayList<Long>();
        public ConfigSummary config;
    }
}
