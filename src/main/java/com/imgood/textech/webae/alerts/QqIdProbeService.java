package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Admin-triggered QQ gateway listen session that captures openids / channel ids for alert targets.
 * At most one session runs at a time; credentials stay in memory only.
 */
public final class QqIdProbeService {

    public static final long DEFAULT_TIMEOUT_MS = 10L * 60L * 1000L;
    private static final int MAX_DISCOVERIES = 64;

    private static final QqIdProbeService INSTANCE = new QqIdProbeService();

    private final Object lock = new Object();
    private QqGatewayClient client;
    private boolean running;
    private String phase = "idle";
    private String error = "";
    private long startedAtMs;
    private long expiresAtMs;
    private final LinkedHashMap<String, QqIdDiscovery> discoveries = new LinkedHashMap<String, QqIdDiscovery>();

    private QqIdProbeService() {}

    public static QqIdProbeService instance() {
        return INSTANCE;
    }

    public StartResult start(String appId, String appSecret, String apiBase, String tokenUrl, long timeoutMs) {
        String id = trim(appId);
        String secret = trim(appSecret);
        if (id.isEmpty() || secret.isEmpty()) {
            return StartResult.fail("appId and appSecret are required");
        }
        long timeout = timeoutMs > 0L ? timeoutMs : DEFAULT_TIMEOUT_MS;
        timeout = Math.max(60_000L, Math.min(timeout, 30L * 60L * 1000L));

        synchronized (lock) {
            stopLocked("replaced");
            running = true;
            phase = "starting";
            error = "";
            startedAtMs = System.currentTimeMillis();
            expiresAtMs = startedAtMs + timeout;
            discoveries.clear();
            final QqGatewayClient next = new QqGatewayClient(
                id,
                secret,
                apiBase,
                tokenUrl,
                new QqGatewayClient.Listener() {

                    @Override
                    public void onPhase(String nextPhase) {
                        synchronized (lock) {
                            if (!running) {
                                return;
                            }
                            phase = nextPhase == null ? "" : nextPhase;
                            maybeExpireLocked();
                        }
                    }

                    @Override
                    public void onDiscovery(QqIdDiscovery discovery) {
                        synchronized (lock) {
                            if (!running || discovery == null || trim(discovery.targetId).isEmpty()) {
                                return;
                            }
                            maybeExpireLocked();
                            if (!running) {
                                return;
                            }
                            String key = discovery.dedupeKey();
                            discoveries.remove(key);
                            discoveries.put(key, discovery);
                            while (discoveries.size() > MAX_DISCOVERIES) {
                                String oldest = discoveries.keySet()
                                    .iterator()
                                    .next();
                                discoveries.remove(oldest);
                            }
                        }
                    }

                    @Override
                    public void onError(String message) {
                        synchronized (lock) {
                            if (!running) {
                                return;
                            }
                            error = message == null ? "" : message;
                        }
                    }

                    @Override
                    public void onClosed(String reason) {
                        synchronized (lock) {
                            if (!running) {
                                return;
                            }
                            running = false;
                            phase = "stopped";
                            if (error.isEmpty() && reason != null && !"stopped".equals(reason)) {
                                error = reason;
                            }
                            client = null;
                        }
                    }
                });
            client = next;
            next.start();
            scheduleExpiryWatch(timeout);
            return StartResult.ok();
        }
    }

    public void stop() {
        synchronized (lock) {
            stopLocked("stopped");
        }
    }

    public Status snapshot() {
        synchronized (lock) {
            maybeExpireLocked();
            Status status = new Status();
            status.running = running;
            status.phase = phase;
            status.error = error;
            status.startedAtMs = startedAtMs;
            status.expiresAtMs = expiresAtMs;
            status.discoveries = new ArrayList<QqIdDiscovery>(discoveries.values());
            return status;
        }
    }

    /** Package-visible helper for unit tests that inject discoveries without a live gateway. */
    void offerDiscoveryForTest(QqIdDiscovery discovery) {
        synchronized (lock) {
            if (discovery == null) {
                return;
            }
            running = true;
            phase = "ready";
            String key = discovery.dedupeKey();
            discoveries.remove(key);
            discoveries.put(key, discovery);
        }
    }

    void resetForTest() {
        synchronized (lock) {
            stopLocked("test_reset");
            discoveries.clear();
            phase = "idle";
            error = "";
            startedAtMs = 0L;
            expiresAtMs = 0L;
        }
    }

    private void scheduleExpiryWatch(final long timeout) {
        Thread watch = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(timeout + 250L);
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }
                synchronized (lock) {
                    maybeExpireLocked();
                }
            }
        }, "WebAE-QQ-IdProbe-Expire");
        watch.setDaemon(true);
        watch.start();
    }

    private void maybeExpireLocked() {
        if (!running) {
            return;
        }
        if (expiresAtMs > 0L && System.currentTimeMillis() >= expiresAtMs) {
            error = "probe session timed out";
            stopLocked("timeout");
        }
    }

    private void stopLocked(String reason) {
        QqGatewayClient current = client;
        client = null;
        running = false;
        phase = "stopped";
        if (current != null) {
            try {
                current.stop();
            } catch (Exception ignored) {}
        }
        if ("timeout".equals(reason) && (error == null || error.isEmpty())) {
            error = "probe session timed out";
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class StartResult {

        public final boolean success;
        public final String error;

        private StartResult(boolean success, String error) {
            this.success = success;
            this.error = error == null ? "" : error;
        }

        static StartResult ok() {
            return new StartResult(true, "");
        }

        static StartResult fail(String error) {
            return new StartResult(false, error);
        }
    }

    public static final class Status {

        public boolean running;
        public String phase = "idle";
        public String error = "";
        public long startedAtMs;
        public long expiresAtMs;
        public List<QqIdDiscovery> discoveries = new ArrayList<QqIdDiscovery>();
    }
}
