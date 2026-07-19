package com.imgood.textech.webae.alerts;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.config.ConfigWebAlertsLoader;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.handler.HandlerWebPlayerTracker;
import com.imgood.textech.webae.alerts.AlertDeliveryTransport.DeliveryException;
import com.imgood.textech.webae.network.PacketWebAlertNotify;

/**
 * Multi-channel WebAE alert dispatcher.
 *
 * <p>Player chat/HUD delivery stays on the server thread and performs no scanning. All DNS, TLS,
 * HTTP, OAuth, SMTP, retry, and backoff work runs on two daemon workers behind a fixed-capacity
 * queue, so an unavailable third-party service cannot stall the Minecraft tick.</p>
 */
public final class WebhookDispatcher {

    private static final WebhookDispatcher INSTANCE = new WebhookDispatcher();
    private static final int QUEUE_CAPACITY = 512;
    private static final int WORKER_COUNT = 2;
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final Gson GSON = new GsonBuilder().create();

    private final ArrayBlockingQueue<DeliveryJob> queue = new ArrayBlockingQueue<DeliveryJob>(QUEUE_CAPACITY);
    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<String, CircuitState>();
    private final AlertDeliveryTransport transport = new AlertDeliveryTransport();
    private final AtomicBoolean workersStarted = new AtomicBoolean(false);
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final List<Thread> workerThreads = new ArrayList<Thread>();

    private WebhookDispatcher() {}

    public static WebhookDispatcher instance() {
        return INSTANCE;
    }

    /** Enqueue all matching routes for a new alert occurrence. */
    public void enqueue(String ownerUuid, WebAlertDto alert) {
        if (ownerUuid == null || alert == null || alert.type == null || !Config.webAlertsEnabled) {
            return;
        }
        WebAlertsConfig cfg = ConfigWebAlertsLoader.get();
        if (cfg == null || !cfg.enabled) {
            return;
        }
        alert.browserNotify = matches(cfg.browserNotifications, alert);
        dispatchLocal(ownerUuid, copyAlert(alert), cfg);

        int budget = Math.max(1, Math.min(64, cfg.notificationMaxDeliveriesPerAlert));
        int scheduled = 0;
        if (cfg.webhooks != null) {
            for (WebAlertsConfig.WebhookRule hook : cfg.webhooks) {
                if (scheduled >= budget) break;
                if (!matches(hook, alert)) continue;
                if (isCircuitOpen("webhook:" + safe(hook.id), System.currentTimeMillis())) continue;
                if (offer(DeliveryJob.legacy(ownerUuid, copyAlert(alert), hook, cfg))) {
                    scheduled++;
                }
            }
        }
        if (cfg.notificationTargets != null) {
            for (WebAlertsConfig.NotificationTarget target : cfg.notificationTargets) {
                if (scheduled >= budget) break;
                if (!matches(target, ownerUuid, alert)) continue;
                if (isCircuitOpen(targetKey(target), System.currentTimeMillis())) continue;
                if (offer(DeliveryJob.target(ownerUuid, copyAlert(alert), target, cfg))) {
                    scheduled++;
                }
            }
        }
        if (scheduled > 0) {
            ensureWorkers();
        }
    }

    public boolean shouldNotifyBrowser(String ownerUuid, WebAlertDto alert) {
        if (!Config.webAlertsEnabled || alert == null) return false;
        WebAlertsConfig cfg = ConfigWebAlertsLoader.get();
        return cfg != null && cfg.enabled && matches(cfg.browserNotifications, alert);
    }

    /**
     * Queue one explicit administrator test for a saved external route.
     *
     * <p>The test intentionally bypasses the route's enabled state and event/owner filters so an
     * administrator can verify credentials before enabling real traffic. It still uses the same
     * bounded queue, retry policy, timeouts, and circuit breaker as normal alert delivery.</p>
     */
    public TestEnqueueResult enqueueTest(String ownerUuid, String routeKind, String routeId) {
        if (!Config.webAlertsEnabled) return TestEnqueueResult.FEATURE_DISABLED;
        if (ownerUuid == null || routeId == null || routeId.trim().isEmpty()) {
            return TestEnqueueResult.NOT_FOUND;
        }
        WebAlertsConfig cfg = ConfigWebAlertsLoader.get();
        if (cfg == null) return TestEnqueueResult.NOT_FOUND;

        WebAlertDto alert = new WebAlertDto();
        alert.id = "delivery-test-" + System.currentTimeMillis();
        alert.type = "test";
        alert.severity = "info";
        alert.title = "[WebAE] Test / 测试通知";
        alert.message = "If you can read this, the alert channel is configured correctly. / 收到此消息表示告警渠道配置可用。";
        alert.timestamp = System.currentTimeMillis();
        alert.sourceKey = "delivery-test";

        DeliveryJob job = null;
        if ("webhook".equals(routeKind) && cfg.webhooks != null) {
            for (WebAlertsConfig.WebhookRule hook : cfg.webhooks) {
                if (hook != null && routeId.equals(hook.id) && !safe(hook.url).trim().isEmpty()) {
                    job = DeliveryJob.legacy(ownerUuid, alert, hook, cfg);
                    break;
                }
            }
        } else if ("target".equals(routeKind) && cfg.notificationTargets != null) {
            for (WebAlertsConfig.NotificationTarget target : cfg.notificationTargets) {
                if (target != null && routeId.equals(target.id)) {
                    job = DeliveryJob.target(ownerUuid, alert, target, cfg);
                    break;
                }
            }
        }
        if (job == null) return TestEnqueueResult.NOT_FOUND;
        if (isCircuitOpen(job.targetKey, System.currentTimeMillis())) {
            return TestEnqueueResult.CIRCUIT_OPEN;
        }
        if (!offer(job)) return TestEnqueueResult.QUEUE_FULL;
        ensureWorkers();
        return TestEnqueueResult.QUEUED;
    }

    /** Stop delivery workers and discard queued third-party jobs during server shutdown. */
    public synchronized void shutdown() {
        for (Thread thread : workerThreads) {
            thread.interrupt();
        }
        workerThreads.clear();
        queue.clear();
        workersStarted.set(false);
    }

    public DeliveryStatus getStatus() {
        DeliveryStatus status = new DeliveryStatus();
        status.queueDepth = queue.size();
        status.queueCapacity = QUEUE_CAPACITY;
        status.workerCount = workersStarted.get() ? WORKER_COUNT : 0;
        status.delivered = delivered.get();
        status.failed = failed.get();
        status.dropped = dropped.get();
        long now = System.currentTimeMillis();
        int open = 0;
        for (CircuitState state : circuits.values()) {
            if (state != null && state.openUntilMs > now) open++;
        }
        status.circuitOpenTargets = open;
        return status;
    }

    private boolean offer(DeliveryJob job) {
        if (queue.offer(job)) {
            return true;
        }
        dropped.incrementAndGet();
        AdvanceDataMonitor.LOG.warn("[WebAE] Alert delivery queue full; dropping target {}", job.targetKey);
        return false;
    }

    private void ensureWorkers() {
        if (workersStarted.get()) return;
        synchronized (this) {
            if (workersStarted.get()) return;
            workersStarted.set(true);
            for (int i = 0; i < WORKER_COUNT; i++) {
                Thread thread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        runWorker();
                    }
                }, "WebAE-AlertDelivery-" + (i + 1));
                thread.setDaemon(true);
                workerThreads.add(thread);
                thread.start();
            }
        }
    }

    private void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                deliver(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Alert delivery worker error", t);
            }
        }
    }

    private void deliver(DeliveryJob job) {
        if (job == null || isCircuitOpen(job.targetKey, System.currentTimeMillis())) {
            return;
        }
        int attempts = Math.max(1, Math.min(5, job.maxAttempts));
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                if (job.legacyWebhook != null) {
                    transport.postLegacyWebhook(job.legacyWebhook.url, buildLegacyPayload(job), job.runtime);
                } else {
                    transport.sendTarget(job.target, job.ownerUuid, job.alert, job.runtime);
                }
                delivered.incrementAndGet();
                recordSuccess(job.targetKey);
                return;
            } catch (DeliveryException e) {
                if (!e.retryable || attempt >= attempts) {
                    recordFailure(job, e.getMessage());
                    return;
                }
                long exponential = Math.min(MAX_BACKOFF_MS, 1000L << Math.min(5, attempt - 1));
                long waitMs = Math.min(MAX_BACKOFF_MS, Math.max(exponential, e.retryAfterMs));
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (Throwable t) {
                recordFailure(job, t.getClass().getSimpleName());
                return;
            }
        }
    }

    private void recordSuccess(String targetKey) {
        CircuitState state = circuits.get(targetKey);
        if (state != null) {
            state.consecutiveFailures = 0;
            state.openUntilMs = 0L;
        }
    }

    private void recordFailure(DeliveryJob job, String reason) {
        failed.incrementAndGet();
        CircuitState state = circuits.get(job.targetKey);
        if (state == null) {
            CircuitState created = new CircuitState();
            CircuitState old = circuits.putIfAbsent(job.targetKey, created);
            state = old != null ? old : created;
        }
        int failures = ++state.consecutiveFailures;
        if (failures >= job.circuitFailures) {
            state.openUntilMs = System.currentTimeMillis() + (long) job.circuitSeconds * 1000L;
            state.consecutiveFailures = 0;
        }
        AdvanceDataMonitor.LOG.warn(
            "[WebAE] Alert delivery failed target={} circuitOpen={} reason={}",
            job.targetKey,
            state.openUntilMs > System.currentTimeMillis(),
            reason == null ? "unknown" : reason);
    }

    private boolean isCircuitOpen(String targetKey, long now) {
        CircuitState state = circuits.get(targetKey);
        return state != null && state.openUntilMs > now;
    }

    private void dispatchLocal(final String ownerUuid, final WebAlertDto alert, final WebAlertsConfig cfg) {
        Runnable delivery = new Runnable() {

            @Override
            public void run() {
                EntityPlayerMP player = HandlerWebPlayerTracker.findOnlinePlayer(ownerUuid);
                if (player == null) return;
                if (matches(cfg.playerChat, alert)) {
                    sendPlayerChat(player, alert);
                }
                if (matches(cfg.playerHud, alert)) {
                    AdvanceDataMonitor.ADMCHANEL.sendTo(
                        new PacketWebAlertNotify(
                            alert.severity,
                            alert.title,
                            alert.message,
                            cfg.playerHud.durationSeconds,
                            cfg.playerHud.maxVisible,
                            cfg.playerHud.position,
                            cfg.playerHud.soundEnabled),
                        player);
                }
            }
        };
        if (HandlerTick.isServerThread()) {
            delivery.run();
        } else {
            HandlerTick.enqueueServerTask(delivery);
        }
    }

    private static void sendPlayerChat(EntityPlayerMP player, WebAlertDto alert) {
        ChatComponentTranslation component = new ChatComponentTranslation(
            "adm.webae.alert.chat.message",
            safe(alert.title),
            safe(alert.message));
        ChatStyle style = new ChatStyle();
        style.setColor(severityColor(alert.severity));
        component.setChatStyle(style);
        player.addChatMessage(component);
    }

    private static EnumChatFormatting severityColor(String severity) {
        if ("error".equals(severity)) return EnumChatFormatting.RED;
        if ("warning".equals(severity)) return EnumChatFormatting.GOLD;
        return EnumChatFormatting.AQUA;
    }

    private static boolean matches(WebAlertsConfig.NotificationFilter filter, WebAlertDto alert) {
        if (filter == null || !filter.enabled || alert == null) return false;
        if (filter.events != null && !filter.events.isEmpty() && !filter.events.contains(alert.type)) return false;
        return filter.severities == null || filter.severities.isEmpty() || filter.severities.contains(alert.severity);
    }

    private static boolean matches(WebAlertsConfig.NotificationTarget target, String ownerUuid, WebAlertDto alert) {
        if (target == null || !matches((WebAlertsConfig.NotificationFilter) target, alert)) return false;
        return target.ownerUuids == null || target.ownerUuids.isEmpty() || target.ownerUuids.contains(ownerUuid);
    }

    private static boolean matches(WebAlertsConfig.WebhookRule hook, WebAlertDto alert) {
        if (hook == null || !hook.enabled || safe(hook.url).trim().isEmpty()) return false;
        return hook.events == null || hook.events.isEmpty() || hook.events.contains(alert.type);
    }

    private static String targetKey(WebAlertsConfig.NotificationTarget target) {
        return safe(target.type) + ":" + safe(target.id);
    }

    private static String buildLegacyPayload(DeliveryJob job) {
        String url = job.legacyWebhook.url.toLowerCase();
        if (url.contains("discord.com/api/webhooks") || url.contains("discordapp.com/api/webhooks")) {
            JsonObject root = new JsonObject();
            if (!safe(job.legacyWebhook.mention).trim().isEmpty()) {
                root.addProperty("content", job.legacyWebhook.mention.trim());
            }
            JsonObject embed = new JsonObject();
            embed.addProperty("title", firstNonEmpty(job.alert.title, "WebAE Alert"));
            embed.addProperty("description", safe(job.alert.message));
            embed.addProperty("color", severityRgb(job.alert.severity));
            JsonArray fields = new JsonArray();
            addField(fields, "Type", job.alert.type, true);
            addField(fields, "Severity", job.alert.severity, true);
            if (job.alert.networkId >= 0) addField(fields, "Network", String.valueOf(job.alert.networkId), true);
            embed.add("fields", fields);
            embed.addProperty("timestamp", iso8601(job.alert.timestamp));
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            root.add("embeds", embeds);
            return GSON.toJson(root);
        }
        JsonObject root = new JsonObject();
        root.addProperty("source", "textech-webae");
        root.addProperty("ownerUuid", job.ownerUuid);
        root.add("alert", GSON.toJsonTree(job.alert));
        return GSON.toJson(root);
    }

    private static void addField(JsonArray fields, String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", safe(value));
        field.addProperty("inline", inline);
        fields.add(field);
    }

    private static int severityRgb(String severity) {
        if ("error".equals(severity)) return 0xED4245;
        if ("warning".equals(severity)) return 0xFEE75C;
        return 0x5865F2;
    }

    private static String iso8601(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timestamp));
    }

    private static WebAlertDto copyAlert(WebAlertDto source) {
        WebAlertDto copy = new WebAlertDto();
        copy.id = source.id;
        copy.type = source.type;
        copy.severity = source.severity;
        copy.title = source.title;
        copy.message = source.message;
        copy.timestamp = source.timestamp;
        copy.networkId = source.networkId;
        copy.acknowledged = source.acknowledged;
        copy.sourceKey = source.sourceKey;
        copy.browserNotify = source.browserNotify;
        return copy;
    }

    /** Mask URL and credential fields before returning rules to a browser. */
    public static WebAlertsConfig sanitizeForClient(WebAlertsConfig cfg) {
        if (cfg == null) return null;
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
        copy.automationMaxTriggersPerHour = cfg.automationMaxTriggersPerHour;
        copy.notificationMaxDeliveriesPerAlert = cfg.notificationMaxDeliveriesPerAlert;
        copy.notificationRetryMaxAttempts = cfg.notificationRetryMaxAttempts;
        copy.notificationConnectTimeoutMs = cfg.notificationConnectTimeoutMs;
        copy.notificationReadTimeoutMs = cfg.notificationReadTimeoutMs;
        copy.notificationCircuitBreakFailures = cfg.notificationCircuitBreakFailures;
        copy.notificationCircuitBreakSeconds = cfg.notificationCircuitBreakSeconds;
        copy.inventoryThresholds = cfg.inventoryThresholds;
        copy.automationRules = cfg.automationRules;
        copy.browserNotifications = copyFilter(cfg.browserNotifications);
        copy.playerChat = copyFilter(cfg.playerChat);
        copy.playerHud = copyHudFilter(cfg.playerHud);
        copy.webhooks = new ArrayList<WebAlertsConfig.WebhookRule>();
        if (cfg.webhooks != null) {
            for (WebAlertsConfig.WebhookRule hook : cfg.webhooks) {
                if (hook == null) continue;
                WebAlertsConfig.WebhookRule masked = new WebAlertsConfig.WebhookRule();
                masked.id = hook.id;
                masked.url = maskSecret(hook.url);
                masked.urlConfigured = !safe(hook.url).trim().isEmpty();
                masked.enabled = hook.enabled;
                masked.events = hook.events;
                masked.mention = hook.mention;
                copy.webhooks.add(masked);
            }
        }
        copy.notificationTargets = new ArrayList<WebAlertsConfig.NotificationTarget>();
        if (cfg.notificationTargets != null) {
            for (WebAlertsConfig.NotificationTarget target : cfg.notificationTargets) {
                if (target == null) continue;
                WebAlertsConfig.NotificationTarget masked = copyTarget(target);
                masked.urlConfigured = !safe(target.url).isEmpty();
                masked.url = maskSecret(target.url);
                masked.appSecretConfigured = !safe(target.appSecret).isEmpty();
                masked.appSecret = maskSecret(target.appSecret);
                masked.corpSecretConfigured = !safe(target.corpSecret).isEmpty();
                masked.corpSecret = maskSecret(target.corpSecret);
                masked.smtpPasswordConfigured = !safe(target.smtpPassword).isEmpty();
                masked.smtpPassword = maskSecret(target.smtpPassword);
                copy.notificationTargets.add(masked);
            }
        }
        return copy;
    }

    private static WebAlertsConfig.NotificationFilter copyFilter(WebAlertsConfig.NotificationFilter source) {
        WebAlertsConfig.NotificationFilter copy = new WebAlertsConfig.NotificationFilter();
        if (source == null) return copy;
        copy.enabled = source.enabled;
        copy.events = source.events == null ? new ArrayList<String>() : new ArrayList<String>(source.events);
        copy.severities = source.severities == null ? new ArrayList<String>()
            : new ArrayList<String>(source.severities);
        return copy;
    }

    private static WebAlertsConfig.HudNotificationFilter copyHudFilter(WebAlertsConfig.HudNotificationFilter source) {
        WebAlertsConfig.HudNotificationFilter copy = new WebAlertsConfig.HudNotificationFilter();
        if (source == null) return copy;
        copy.enabled = source.enabled;
        copy.events = source.events == null ? new ArrayList<String>() : new ArrayList<String>(source.events);
        copy.severities = source.severities == null ? new ArrayList<String>()
            : new ArrayList<String>(source.severities);
        copy.durationSeconds = source.durationSeconds;
        copy.maxVisible = source.maxVisible;
        copy.position = source.position;
        copy.soundEnabled = source.soundEnabled;
        return copy;
    }

    private static WebAlertsConfig.NotificationTarget copyTarget(WebAlertsConfig.NotificationTarget source) {
        WebAlertsConfig.NotificationTarget copy = new WebAlertsConfig.NotificationTarget();
        copy.id = source.id;
        copy.type = source.type;
        copy.enabled = source.enabled;
        copy.events = source.events == null ? new ArrayList<String>() : new ArrayList<String>(source.events);
        copy.severities = source.severities == null ? new ArrayList<String>()
            : new ArrayList<String>(source.severities);
        copy.ownerUuids = source.ownerUuids == null ? new ArrayList<String>()
            : new ArrayList<String>(source.ownerUuids);
        copy.url = source.url;
        copy.appId = source.appId;
        copy.appSecret = source.appSecret;
        copy.baseUrl = source.baseUrl;
        copy.tokenUrl = source.tokenUrl;
        copy.targetType = source.targetType;
        copy.targetId = source.targetId;
        copy.mode = source.mode;
        copy.templateId = source.templateId;
        copy.templateUrl = source.templateUrl;
        copy.corpId = source.corpId;
        copy.corpSecret = source.corpSecret;
        copy.agentId = source.agentId;
        copy.toUser = source.toUser;
        copy.toParty = source.toParty;
        copy.toTag = source.toTag;
        copy.smtpHost = source.smtpHost;
        copy.smtpPort = source.smtpPort;
        copy.smtpSecurity = source.smtpSecurity;
        copy.smtpUsername = source.smtpUsername;
        copy.smtpPassword = source.smtpPassword;
        copy.mailFrom = source.mailFrom;
        copy.mailTo = source.mailTo == null ? new ArrayList<String>() : new ArrayList<String>(source.mailTo);
        copy.mailCc = source.mailCc == null ? new ArrayList<String>() : new ArrayList<String>(source.mailCc);
        copy.subjectPrefix = source.subjectPrefix;
        return copy;
    }

    public static String maskUrl(String value) {
        return maskSecret(value);
    }

    private static String maskSecret(String value) {
        String text = safe(value).trim();
        if (text.isEmpty()) return "";
        if (text.length() <= 4) return "***";
        return "***" + text.substring(text.length() - 4);
    }

    private static String firstNonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class DeliveryStatus {

        public int queueDepth;
        public int queueCapacity;
        public int workerCount;
        public long delivered;
        public long failed;
        public long dropped;
        public int circuitOpenTargets;
    }

    public enum TestEnqueueResult {
        QUEUED,
        FEATURE_DISABLED,
        NOT_FOUND,
        CIRCUIT_OPEN,
        QUEUE_FULL
    }

    private static final class CircuitState {

        volatile int consecutiveFailures;
        volatile long openUntilMs;
    }

    private static final class DeliveryJob {

        final String ownerUuid;
        final WebAlertDto alert;
        final WebAlertsConfig.WebhookRule legacyWebhook;
        final WebAlertsConfig.NotificationTarget target;
        final WebAlertsConfig runtime;
        final String targetKey;
        final int maxAttempts;
        final int circuitFailures;
        final int circuitSeconds;

        private DeliveryJob(String ownerUuid, WebAlertDto alert, WebAlertsConfig.WebhookRule legacyWebhook,
            WebAlertsConfig.NotificationTarget target, WebAlertsConfig runtime, String targetKey) {
            this.ownerUuid = ownerUuid;
            this.alert = alert;
            this.legacyWebhook = legacyWebhook;
            this.target = target;
            this.runtime = runtime;
            this.targetKey = targetKey;
            this.maxAttempts = runtime.notificationRetryMaxAttempts;
            this.circuitFailures = runtime.notificationCircuitBreakFailures;
            this.circuitSeconds = runtime.notificationCircuitBreakSeconds;
        }

        static DeliveryJob legacy(String ownerUuid, WebAlertDto alert, WebAlertsConfig.WebhookRule webhook,
            WebAlertsConfig runtime) {
            return new DeliveryJob(ownerUuid, alert, webhook, null, runtime, "webhook:" + safe(webhook.id));
        }

        static DeliveryJob target(String ownerUuid, WebAlertDto alert, WebAlertsConfig.NotificationTarget target,
            WebAlertsConfig runtime) {
            return new DeliveryJob(ownerUuid, alert, null, target, runtime, WebhookDispatcher.targetKey(target));
        }
    }
}
