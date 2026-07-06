package com.imgood.textech.webae.alerts;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.config.ConfigWebAlertsLoader;

/**
 * Async outbound webhook queue for WebAE alerts (Discord embed + generic JSON).
 * Single worker thread; does not block {@link WebAlertEngine} or server tick.
 */
public final class WebhookDispatcher {

    private static final WebhookDispatcher INSTANCE = new WebhookDispatcher();
    private static final int MAX_QUEUE = 1000;
    private static final int HTTP_TIMEOUT_MS = 5000;
    private static final String USER_AGENT = "TeXTech-WebAE/1.0";
    private static final Gson GSON = new GsonBuilder().create();

    private final BlockingQueue<WebhookJob> queue = new LinkedBlockingQueue<WebhookJob>();
    private final ArrayDeque<WebhookJob> overflowBuffer = new ArrayDeque<WebhookJob>();
    private final AtomicBoolean workerStarted = new AtomicBoolean(false);
    private volatile Thread workerThread;

    private WebhookDispatcher() {}

    public static WebhookDispatcher instance() {
        return INSTANCE;
    }

    /**
     * Enqueue a webhook delivery for a new alert occurrence.
     */
    public void enqueue(String ownerUuid, WebAlertDto alert) {
        if (ownerUuid == null || alert == null || alert.type == null) {
            return;
        }
        WebAlertsConfig cfg = ConfigWebAlertsLoader.get();
        if (cfg == null || cfg.webhooks == null || cfg.webhooks.isEmpty()) {
            return;
        }
        ensureWorker();
        for (WebAlertsConfig.WebhookRule hook : cfg.webhooks) {
            if (hook == null || !hook.enabled) {
                continue;
            }
            if (hook.url == null || hook.url.trim()
                .isEmpty()) {
                continue;
            }
            if (!matchesEvent(hook, alert.type)) {
                continue;
            }
            offer(new WebhookJob(ownerUuid, alert, hook));
        }
    }

    /** Stop worker thread (server shutdown). */
    public synchronized void shutdown() {
        Thread t = workerThread;
        workerThread = null;
        if (t != null) {
            t.interrupt();
        }
        queue.clear();
        overflowBuffer.clear();
        workerStarted.set(false);
    }

    private static boolean matchesEvent(WebAlertsConfig.WebhookRule hook, String alertType) {
        if (hook.events == null || hook.events.isEmpty()) {
            return true;
        }
        for (String ev : hook.events) {
            if (ev != null && ev.equals(alertType)) {
                return true;
            }
        }
        return false;
    }

    private void ensureWorker() {
        if (workerStarted.get()) {
            return;
        }
        synchronized (this) {
            if (workerStarted.get()) {
                return;
            }
            Thread t = new Thread(new Runnable() {

                @Override
                public void run() {
                    runWorker();
                }
            }, "WebAE-WebhookWorker");
            t.setDaemon(true);
            workerThread = t;
            workerStarted.set(true);
            t.start();
        }
    }

    private void offer(WebhookJob job) {
        if (!queue.offer(job)) {
            synchronized (overflowBuffer) {
                if (overflowBuffer.size() >= MAX_QUEUE) {
                    overflowBuffer.pollFirst();
                    AdvanceDataMonitor.LOG.warn("[WebAE] Webhook queue overflow; dropping oldest job");
                }
                overflowBuffer.addLast(job);
            }
        }
        drainOverflow();
    }

    private void drainOverflow() {
        synchronized (overflowBuffer) {
            while (!overflowBuffer.isEmpty()) {
                WebhookJob job = overflowBuffer.peekFirst();
                if (job == null || !queue.offer(job)) {
                    break;
                }
                overflowBuffer.pollFirst();
            }
        }
    }

    private void runWorker() {
        while (!Thread.currentThread()
            .isInterrupted()) {
            try {
                WebhookJob job = queue.take();
                deliver(job);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                break;
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Webhook worker error", t);
            }
        }
    }

    private void deliver(WebhookJob job) {
        String body = buildPayload(job);
        String url = job.rule.url.trim();
        int attempts = 0;
        long backoffMs = 1000L;
        while (attempts < 3) {
            attempts++;
            try {
                postJson(url, body);
                return;
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Webhook delivery failed (attempt {}/3) id={}: {}",
                    attempts,
                    job.rule.id,
                    e.getMessage());
                if (attempts >= 3) {
                    return;
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }
                backoffMs *= 2;
            }
        }
    }

    private static String buildPayload(WebhookJob job) {
        String url = job.rule.url.toLowerCase();
        if (url.contains("discord.com/api/webhooks") || url.contains("discordapp.com/api/webhooks")) {
            return buildDiscordPayload(job);
        }
        return buildGenericPayload(job);
    }

    private static String buildDiscordPayload(WebhookJob job) {
        JsonObject root = new JsonObject();
        if (job.rule.mention != null && !job.rule.mention.trim()
            .isEmpty()) {
            root.addProperty("content", job.rule.mention.trim());
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", safe(job.alert.title, "WebAE Alert"));
        embed.addProperty("description", safe(job.alert.message, ""));
        embed.addProperty("color", severityColor(job.alert.severity));
        JsonArray fields = new JsonArray();
        addField(fields, "Type", job.alert.type, true);
        addField(fields, "Severity", job.alert.severity, true);
        if (job.alert.networkId >= 0) {
            addField(fields, "Network", String.valueOf(job.alert.networkId), true);
        }
        embed.add("fields", fields);
        embed.addProperty("timestamp", iso8601(job.alert.timestamp));
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        root.add("embeds", embeds);
        return GSON.toJson(root);
    }

    private static String buildGenericPayload(WebhookJob job) {
        JsonObject root = new JsonObject();
        root.addProperty("source", "textech-webae");
        root.addProperty("ownerUuid", job.ownerUuid);
        root.add("alert", GSON.toJsonTree(job.alert));
        return GSON.toJson(root);
    }

    private static void addField(JsonArray fields, String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value != null ? value : "");
        field.addProperty("inline", inline);
        fields.add(field);
    }

    private static int severityColor(String severity) {
        if ("error".equals(severity)) {
            return 0xED4245;
        }
        if ("warning".equals(severity)) {
            return 0xFEE75C;
        }
        return 0x5865F2;
    }

    private static String iso8601(long ts) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(ts));
    }

    private static String safe(String s, String fallback) {
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        return s;
    }

    private static void postJson(String endpoint, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(HTTP_TIMEOUT_MS);
        connection.setReadTimeout(HTTP_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        if (stream != null) {
            byte[] buf = new byte[256];
            while (stream.read(buf) >= 0) {
                /* drain */
            }
            stream.close();
        }
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
    }

    /** Mask webhook URL for API responses (*** + last 4 chars). */
    public static String maskUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.length() <= 4) {
            return "***";
        }
        return "***" + trimmed.substring(trimmed.length() - 4);
    }

    /** Strip secrets from config before sending to browser. */
    public static WebAlertsConfig sanitizeForClient(WebAlertsConfig cfg) {
        if (cfg == null) {
            return null;
        }
        WebAlertsConfig copy = new WebAlertsConfig();
        copy.version = cfg.version;
        copy.enabled = cfg.enabled;
        copy.pollIntervalSeconds = cfg.pollIntervalSeconds;
        copy.cpuStuckMinutes = cfg.cpuStuckMinutes;
        copy.gtErrorEnabled = cfg.gtErrorEnabled;
        copy.orderCompleteEnabled = cfg.orderCompleteEnabled;
        copy.channelThresholdPercent = cfg.channelThresholdPercent;
        copy.channelThresholdAbsolute = cfg.channelThresholdAbsolute;
        copy.serverTpsBelowEnabled = cfg.serverTpsBelowEnabled;
        copy.serverTpsThreshold = cfg.serverTpsThreshold;
        copy.serverTpsDurationSeconds = cfg.serverTpsDurationSeconds;
        copy.inventoryThresholds = cfg.inventoryThresholds;
        if (cfg.webhooks != null) {
            copy.webhooks = new java.util.ArrayList<WebAlertsConfig.WebhookRule>();
            for (WebAlertsConfig.WebhookRule hook : cfg.webhooks) {
                if (hook == null) {
                    continue;
                }
                WebAlertsConfig.WebhookRule masked = new WebAlertsConfig.WebhookRule();
                masked.id = hook.id;
                masked.url = maskUrl(hook.url);
                masked.urlConfigured = hook.url != null && !hook.url.trim()
                    .isEmpty();
                masked.enabled = hook.enabled;
                masked.events = hook.events;
                masked.mention = hook.mention;
                copy.webhooks.add(masked);
            }
        }
        return copy;
    }

    private static final class WebhookJob {

        final String ownerUuid;
        final WebAlertDto alert;
        final WebAlertsConfig.WebhookRule rule;

        WebhookJob(String ownerUuid, WebAlertDto alert, WebAlertsConfig.WebhookRule rule) {
            this.ownerUuid = ownerUuid;
            this.alert = alert;
            this.rule = rule;
        }
    }
}
