package com.imgood.textech.webae.health;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Server-wide TPS / MSPT sampler (opencomputer-monitor style).
 * Runs on the server thread via {@link com.imgood.textech.handler.HandlerTick}.
 */
public final class ServerHealthSampler {

    private static final ServerHealthSampler INSTANCE = new ServerHealthSampler();
    private static final long WINDOW_MS = 300_000L;
    private static final long SAMPLE_INTERVAL_MS = 1000L;
    private static final int MAX_POINTS = 360;

    private static final class SamplePoint {

        final long ts;
        final double tps;
        final double mspt;

        SamplePoint(long ts, double tps, double mspt) {
            this.ts = ts;
            this.tps = tps;
            this.mspt = mspt;
        }
    }

    private final Deque<SamplePoint> samples = new ArrayDeque<SamplePoint>();
    private long serverStartMs;
    private long lastTickMs;
    private long lastSampleMs;
    private double latestTps = 20.0;
    private double latestMspt = 50.0;
    private long lowTpsSinceMs;

    private ServerHealthSampler() {}

    public static ServerHealthSampler instance() {
        return INSTANCE;
    }

    /** Called every server tick (END phase). */
    public synchronized void onServerTick() {
        long now = System.currentTimeMillis();
        if (serverStartMs == 0L) {
            serverStartMs = now;
        }
        if (lastTickMs > 0L) {
            long delta = now - lastTickMs;
            if (delta > 0L) {
                latestMspt = (double) delta;
                latestTps = Math.min(20.0, 1000.0 / (double) delta);
            }
        }
        lastTickMs = now;
    }

    /**
     * Called at a reduced cadence (e.g. every 2 ticks) to collect samples
     * without adding a synchronized call every single tick.
     */
    public synchronized void collectSample() {
        long now = System.currentTimeMillis();
        if (now - lastSampleMs < SAMPLE_INTERVAL_MS) {
            return;
        }
        lastSampleMs = now;
        samples.addLast(new SamplePoint(now, latestTps, latestMspt));
        long cutoff = now - WINDOW_MS;
        while (!samples.isEmpty() && samples.peekFirst().ts < cutoff) {
            samples.pollFirst();
        }
        while (samples.size() > MAX_POINTS) {
            samples.pollFirst();
        }
    }

    public synchronized double getLatestTps() {
        return latestTps;
    }

    public synchronized double getLatestMspt() {
        return latestMspt;
    }

    public synchronized long getUptimeSeconds() {
        if (serverStartMs == 0L) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - serverStartMs) / 1000L);
    }

    public synchronized int getOnlinePlayers() {
        return countOnlinePlayers();
    }

    /**
     * @return {@code true} when TPS has been below {@code threshold} for at least {@code durationSeconds}.
     */
    public synchronized boolean isTpsBelowForDuration(double threshold, int durationSeconds) {
        long now = System.currentTimeMillis();
        if (latestTps >= threshold) {
            lowTpsSinceMs = 0L;
            return false;
        }
        if (lowTpsSinceMs == 0L) {
            lowTpsSinceMs = now;
            return false;
        }
        return now - lowTpsSinceMs >= (long) durationSeconds * 1000L;
    }

    public synchronized HealthSnapshot snapshot() {
        HealthSnapshot snap = new HealthSnapshot();
        snap.tps = latestTps;
        snap.mspt = latestMspt;
        snap.onlinePlayers = countOnlinePlayers();
        snap.uptimeSeconds = getUptimeSeconds();
        snap.tpsHistory = new ArrayList<Double>();
        snap.msptHistory = new ArrayList<Double>();
        snap.timestamps = new ArrayList<Long>();
        for (SamplePoint p : samples) {
            snap.timestamps.add(p.ts);
            snap.tpsHistory.add(p.tps);
            snap.msptHistory.add(p.mspt);
        }
        return snap;
    }

    private static int countOnlinePlayers() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return 0;
        }
        int count = 0;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                count++;
            }
        }
        return count;
    }

    public static final class HealthSnapshot {

        public double tps;
        public double mspt;
        public int onlinePlayers;
        public long uptimeSeconds;
        public List<Long> timestamps = new ArrayList<Long>();
        public List<Double> tpsHistory = new ArrayList<Double>();
        public List<Double> msptHistory = new ArrayList<Double>();
    }
}
