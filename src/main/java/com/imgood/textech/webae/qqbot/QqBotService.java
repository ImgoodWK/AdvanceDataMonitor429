package com.imgood.textech.webae.qqbot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.imgood.textech.Config;
import com.imgood.textech.webae.assistant.WebAiCompletionService;
import com.imgood.textech.webae.assistant.WebAiCompletionService.CompletionResult;
import com.imgood.textech.webae.assistant.WebAiCompletionService.SearchAugmentResult;
import com.imgood.textech.webae.assistant.WebAiHttpClient.Message;
import com.imgood.textech.webae.qqbot.QqBotCommandRouter.RouteResult;
import com.imgood.textech.webae.qqbot.QqBotConfigStore.RuntimeConfig;

/** QQ bot lifecycle, command execution, AI conversations, scheduling, and audit ring. */
public final class QqBotService {

    private static final QqBotService INSTANCE = new QqBotService();
    private static final int EXECUTOR_CAPACITY = 512;
    private static final int MAX_DEDUPE_IDS = 1024;
    private static final long RECONNECT_MIN_MS = 5000L;
    private static final long RECONNECT_MAX_MS = 300000L;

    private final Object lock = new Object();
    private final LinkedHashMap<String, Long> seenMessageIds = new LinkedHashMap<String, Long>();
    private final Map<String, Long> userLastRequest = new LinkedHashMap<String, Long>();
    private final Map<String, Long> userLastAiRequest = new LinkedHashMap<String, Long>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<String, Conversation>();
    private final Deque<AuditEntry> audit = new ArrayDeque<AuditEntry>();

    private ThreadPoolExecutor worker;
    private QqBotGatewayClient client;
    private RuntimeConfig runtime = new RuntimeConfig();
    private volatile QqBotSnapshot snapshot = new QqBotSnapshot();
    private boolean started;
    private boolean connected;
    private String phase = "stopped";
    private String lastError = "";
    private long lastConnectedAtMs;
    private long lastMessageAtMs;
    private long lastReplyAtMs;
    private long nextReconnectAtMs;
    private long reconnectDelayMs = RECONNECT_MIN_MS;
    private long lastSnapshotAtMs;
    private long lastCleanupAtMs;
    private long lastScheduledReportAtMs;
    private long received;
    private long replied;
    private long aiReplies;
    private long failed;
    private long dropped;
    private long rateLimited;
    private long auditSequence;
    private int generation;

    private QqBotService() {}

    public static QqBotService instance() {
        return INSTANCE;
    }

    public void start() {
        synchronized (lock) {
            started = true;
            ensureWorkerLocked();
        }
        reload();
    }

    public void shutdown() {
        synchronized (lock) {
            started = false;
            generation++;
            stopClientLocked();
            connected = false;
            phase = "stopped";
            nextReconnectAtMs = 0L;
            if (worker != null) {
                worker.shutdownNow();
                worker = null;
            }
            conversations.clear();
        }
    }

    /** Reload encrypted configuration and restart/disable the gateway as needed. */
    public void reload() {
        RuntimeConfig next = QqBotConfigStore.instance()
            .runtime();
        synchronized (lock) {
            runtime = next;
            lastScheduledReportAtMs = System.currentTimeMillis();
            generation++;
            stopClientLocked();
            connected = false;
            reconnectDelayMs = RECONNECT_MIN_MS;
            nextReconnectAtMs = 0L;
            if (!started) {
                phase = "stopped";
                return;
            }
            if (!next.settings.enabled) {
                phase = "disabled";
                return;
            }
            if (!next.configured) {
                phase = "unconfigured";
                lastError = safe(next.validationError).isEmpty() ? "AppID or ClientSecret is missing"
                    : next.validationError;
                return;
            }
            connectLocked();
        }
    }

    /** Called on the Minecraft server tick thread. */
    public void onServerTick(long nowMs) {
        synchronized (lock) {
            if (!started || runtime.settings == null || !runtime.settings.enabled || !runtime.configured) return;
        }
        if (nowMs - lastSnapshotAtMs >= 1000L) {
            snapshot = QqBotSnapshot.capture();
            lastSnapshotAtMs = nowMs;
        }
        synchronized (lock) {
            if (!started || runtime.settings == null || !runtime.settings.enabled || !runtime.configured) return;
            if (client == null && runtime.configured && nextReconnectAtMs > 0L && nowMs >= nextReconnectAtMs) {
                connectLocked();
            }
            if (nowMs - lastCleanupAtMs >= 60000L) {
                cleanupLocked(nowMs);
                lastCleanupAtMs = nowMs;
            }
            maybeScheduleReportLocked(nowMs);
        }
    }

    public Status status() {
        synchronized (lock) {
            Status value = new Status();
            value.enabled = runtime.settings != null && runtime.settings.enabled;
            value.configured = runtime.configured;
            value.running = started && value.enabled && runtime.configured;
            value.connected = connected && client != null && client.isOpen();
            value.phase = phase;
            value.lastError = lastError;
            value.lastConnectedAtMs = lastConnectedAtMs;
            value.lastMessageAtMs = lastMessageAtMs;
            value.lastReplyAtMs = lastReplyAtMs;
            value.nextReconnectAtMs = nextReconnectAtMs;
            value.received = received;
            value.replied = replied;
            value.aiReplies = aiReplies;
            value.failed = failed;
            value.dropped = dropped;
            value.rateLimited = rateLimited;
            value.queueDepth = worker == null ? 0
                : worker.getQueue()
                    .size();
            value.queueCapacity = runtime.settings == null ? 0 : runtime.settings.maxQueuedRequests;
            value.conversationCount = conversations.size();
            value.lastScheduledReportAtMs = lastScheduledReportAtMs;
            value.nextScheduledReportAtMs = nextScheduledReportAtLocked();
            value.snapshot = snapshot.copy();
            value.configUpdatedAt = QqBotConfigStore.instance()
                .updatedAt();
            value.configUpdatedBy = QqBotConfigStore.instance()
                .updatedBy();
            return value;
        }
    }

    public List<AuditEntry> audit(int limit) {
        synchronized (lock) {
            int wanted = Math.max(1, Math.min(limit, 500));
            List<AuditEntry> result = new ArrayList<AuditEntry>();
            java.util.Iterator<AuditEntry> iterator = audit.descendingIterator();
            while (iterator.hasNext() && result.size() < wanted) result.add(
                iterator.next()
                    .copy());
            return result;
        }
    }

    public void clearConversations() {
        synchronized (lock) {
            conversations.clear();
            addAuditLocked("system", "", "", "", "clear_conversations", "ok", "All AI conversations cleared", 0L);
        }
    }

    public ManualSendResult restart() {
        synchronized (lock) {
            if (!started) return ManualSendResult.fail("QQ bot service is stopped");
            if (runtime.settings == null || !runtime.settings.enabled)
                return ManualSendResult.fail("QQ bot is disabled");
            if (!runtime.configured) return ManualSendResult.fail("QQ bot credentials are incomplete");
            generation++;
            stopClientLocked();
            connected = false;
            nextReconnectAtMs = 0L;
            reconnectDelayMs = RECONNECT_MIN_MS;
            connectLocked();
            return ManualSendResult.ok();
        }
    }

    public ManualSendResult sendManual(String targetType, String targetId, String content) {
        final String type = safe(targetType).toLowerCase();
        final String id = safe(targetId);
        final String message = safe(content);
        if (!("group".equals(type) || "c2c".equals(type) || "channel".equals(type))) {
            return ManualSendResult.fail("targetType must be group, c2c, or channel");
        }
        if (id.isEmpty()) return ManualSendResult.fail("targetId is required");
        if (message.isEmpty()) return ManualSendResult.fail("message is required");
        QqBotConfig cfg = settings();
        if (message.length() > cfg.maxReplyChars) return ManualSendResult.fail("message is too long");
        synchronized (lock) {
            if (!started || !connected || client == null || !client.isOpen()) {
                return ManualSendResult.fail("QQ bot is not connected");
            }
        }
        if (!submit(new Runnable() {

            @Override
            public void run() {
                send(type, id, "", "", message, "manual", "admin", System.currentTimeMillis());
            }
        }, cfg)) {
            return ManualSendResult.fail("QQ bot queue is full or the service is unavailable");
        }
        return ManualSendResult.ok();
    }

    /** Queue one manually supplied JPEG for QQ group/C2C delivery. Queue acceptance is not delivery confirmation. */
    public ManualSendResult sendManualImage(String targetType, String targetId, String content, final byte[] jpeg,
        String fileName) {
        final String type = safe(targetType).toLowerCase();
        final String id = safe(targetId);
        final String message = safe(content);
        if (!("group".equals(type) || "c2c".equals(type))) {
            return ManualSendResult.fail("QQ images support group or c2c targets only");
        }
        if (id.isEmpty()) return ManualSendResult.fail("targetId is required");
        if (jpeg == null || jpeg.length == 0) return ManualSendResult.fail("image is required");
        if (jpeg.length > Math.max(64, Config.webScreenshotMaxUploadKB) * 1024) {
            return ManualSendResult.fail("image exceeds the configured screenshot limit");
        }
        QqBotConfig cfg = settings();
        if (message.length() > cfg.maxReplyChars) return ManualSendResult.fail("caption is too long");
        synchronized (lock) {
            if (!started || !connected || client == null || !client.isOpen()) {
                return ManualSendResult.fail("QQ bot is not connected");
            }
        }
        if (!submit(new Runnable() {

            @Override
            public void run() {
                sendImage(type, id, message, jpeg, System.currentTimeMillis());
            }
        }, cfg)) {
            return ManualSendResult.fail("QQ bot queue is full or the service is unavailable");
        }
        return ManualSendResult.ok();
    }

    private void connectLocked() {
        ensureWorkerLocked();
        final int connectionGeneration = generation;
        phase = "starting";
        lastError = "";
        QqBotGatewayClient next = new QqBotGatewayClient(runtime, new QqBotGatewayClient.Listener() {

            @Override
            public void onPhase(String nextPhase) {
                synchronized (lock) {
                    if (connectionGeneration == generation) phase = nextPhase;
                }
            }

            @Override
            public void onReady() {
                synchronized (lock) {
                    if (connectionGeneration != generation) return;
                    connected = true;
                    phase = "ready";
                    lastConnectedAtMs = System.currentTimeMillis();
                    nextReconnectAtMs = 0L;
                    reconnectDelayMs = RECONNECT_MIN_MS;
                    addAuditLocked("system", "", "", "", "connect", "ok", "QQ gateway ready", 0L);
                }
            }

            @Override
            public void onMessage(QqBotMessage message) {
                if (connectionGeneration == generation) handleInbound(message);
            }

            @Override
            public void onError(String message) {
                synchronized (lock) {
                    if (connectionGeneration != generation) return;
                    lastError = truncate(safe(message), 500);
                    addAuditLocked("system", "", "", "", "gateway", "error", lastError, 0L);
                }
            }

            @Override
            public void onClosed(String reason) {
                synchronized (lock) {
                    if (connectionGeneration != generation) return;
                    client = null;
                    connected = false;
                    if (!started || runtime.settings == null || !runtime.settings.enabled) {
                        phase = "stopped";
                        return;
                    }
                    phase = "reconnecting";
                    nextReconnectAtMs = System.currentTimeMillis() + reconnectDelayMs;
                    reconnectDelayMs = Math.min(RECONNECT_MAX_MS, reconnectDelayMs * 2L);
                    addAuditLocked("system", "", "", "", "disconnect", "retry", safe(reason), 0L);
                }
            }
        });
        client = next;
        next.start();
    }

    private void handleInbound(final QqBotMessage message) {
        if (message == null) return;
        final QqBotConfig cfg;
        final RouteResult route;
        final long acceptedAt = System.currentTimeMillis();
        synchronized (lock) {
            cfg = copySettingsLocked();
            if (!allowed(cfg, message)) return;
            if (isDuplicateLocked(message.dedupeKey(), acceptedAt)) return;
            received++;
            lastMessageAtMs = acceptedAt;
            String inboundText = truncate(message.content, cfg.maxInputChars);
            QqBotIntentClassifier.Decision intent = QqBotIntentClassifier.classify(cfg, inboundText);
            if (cfg.astrBotCompatEnabled && intent.owner == QqBotIntentClassifier.Owner.ASTRBOT) {
                addAuditLocked(
                    "in",
                    message.targetType,
                    message.targetId,
                    message.senderId,
                    "intent",
                    "astrbot_handoff",
                    preview(intent.reason + " | " + inboundText),
                    0L);
                return;
            }
            boolean admin = cfg.adminUserIds.contains(message.senderId);
            route = QqBotCommandRouter.route(cfg, snapshot.copy(), intent.textForHandler, admin);
            if ("ignore".equals(route.kind)) return;
            long cooldown = (long) cfg.userCooldownSeconds * 1000L;
            Long last = userLastRequest.get(message.senderId);
            if (cooldown > 0L && last != null && acceptedAt - last.longValue() < cooldown) {
                rateLimited++;
                addAuditLocked(
                    "in",
                    message.targetType,
                    message.targetId,
                    message.senderId,
                    route.command,
                    "rate_limited",
                    preview(message.content),
                    0L);
                return;
            }
            userLastRequest.put(message.senderId, acceptedAt);
            if ("ai".equals(route.kind)) {
                long aiCooldown = (long) cfg.aiCooldownSeconds * 1000L;
                Long lastAi = userLastAiRequest.get(message.senderId);
                if (lastAi != null && acceptedAt - lastAi.longValue() < aiCooldown) {
                    rateLimited++;
                    addAuditLocked(
                        "in",
                        message.targetType,
                        message.targetId,
                        message.senderId,
                        route.command,
                        "ai_rate_limited",
                        preview(message.content),
                        0L);
                    return;
                }
                userLastAiRequest.put(message.senderId, acceptedAt);
            }
            addAuditLocked(
                "in",
                message.targetType,
                message.targetId,
                message.senderId,
                route.command,
                "accepted",
                preview(message.content),
                0L);
        }
        submit(new Runnable() {

            @Override
            public void run() {
                if (route.clearConversation) clearConversation(message.sessionKey());
                if ("ai".equals(route.kind)) processAi(cfg, message, route.aiText, route.command, acceptedAt);
                else send(
                    message.targetType,
                    message.targetId,
                    message.messageId,
                    message.eventId,
                    route.reply,
                    route.command,
                    message.senderId,
                    acceptedAt);
            }
        }, cfg);
    }

    private void processAi(QqBotConfig cfg, QqBotMessage source, String userText, String command, long acceptedAt) {
        String sessionKey = source.sessionKey();
        List<Message> messages = new ArrayList<Message>();
        messages.add(new Message("system", aiSystemPrompt(cfg, snapshot.copy())));
        synchronized (lock) {
            Conversation conversation = conversations.get(sessionKey);
            if (conversation != null && !conversation.expired(cfg, System.currentTimeMillis())) {
                messages.addAll(conversation.copyMessages());
            }
        }
        messages.add(new Message("user", userText));
        try {
            if (cfg.aiWebSearch) {
                SearchAugmentResult augmented = WebAiCompletionService.maybeAugmentWithSearch(messages, userText);
                messages = augmented.messages;
            }
            CompletionResult result = WebAiCompletionService.completeWithFailover(messages);
            String reply = normalizeReply(result.content, cfg.maxReplyChars);
            synchronized (lock) {
                Conversation conversation = conversations.get(sessionKey);
                if (conversation == null) {
                    conversation = new Conversation();
                    conversations.put(sessionKey, conversation);
                }
                conversation.add("user", userText, cfg.maxConversationTurns);
                conversation.add("assistant", reply, cfg.maxConversationTurns);
                conversation.lastUsedAtMs = System.currentTimeMillis();
                aiReplies++;
            }
            send(
                source.targetType,
                source.targetId,
                source.messageId,
                source.eventId,
                reply,
                command,
                source.senderId,
                acceptedAt);
        } catch (Exception e) {
            synchronized (lock) {
                failed++;
                lastError = "AI: " + truncate(safeMessage(e), 400);
                addAuditLocked(
                    "out",
                    source.targetType,
                    source.targetId,
                    source.senderId,
                    command,
                    "ai_error",
                    lastError,
                    System.currentTimeMillis() - acceptedAt);
            }
            send(
                source.targetType,
                source.targetId,
                source.messageId,
                source.eventId,
                "AI 服务暂时不可用，请稍后再试。服务器状态查询命令仍可正常使用。",
                command,
                source.senderId,
                acceptedAt);
        }
    }

    private void send(String targetType, String targetId, String replyMessageId, String eventId, String content,
        String command, String senderId, long acceptedAt) {
        QqBotGatewayClient current;
        QqBotConfig cfg;
        synchronized (lock) {
            current = client;
            cfg = copySettingsLocked();
        }
        if (current == null || !current.isOpen()) {
            synchronized (lock) {
                failed++;
                addAuditLocked(
                    "out",
                    targetType,
                    targetId,
                    senderId,
                    command,
                    "not_connected",
                    preview(content),
                    System.currentTimeMillis() - acceptedAt);
            }
            return;
        }
        String bounded = normalizeReply(content, cfg.maxReplyChars);
        QqBotGatewayClient.SendResult result = current
            .sendMessage(targetType, targetId, replyMessageId, eventId, bounded);
        synchronized (lock) {
            long latency = Math.max(0L, System.currentTimeMillis() - acceptedAt);
            if (result.success) {
                replied++;
                lastReplyAtMs = System.currentTimeMillis();
                addAuditLocked("out", targetType, targetId, senderId, command, "sent", preview(bounded), latency);
            } else {
                failed++;
                lastError = truncate(result.error, 500);
                addAuditLocked("out", targetType, targetId, senderId, command, "send_error", lastError, latency);
            }
        }
    }

    private void sendImage(String targetType, String targetId, String content, byte[] jpeg, long acceptedAt) {
        QqBotGatewayClient current;
        synchronized (lock) {
            current = client;
        }
        if (current == null || !current.isOpen()) {
            synchronized (lock) {
                failed++;
                addAuditLocked(
                    "out",
                    targetType,
                    targetId,
                    "admin",
                    "manual_image",
                    "not_connected",
                    "image delivery unavailable",
                    System.currentTimeMillis() - acceptedAt);
            }
            return;
        }
        QqBotGatewayClient.SendResult result = current.sendImage(targetType, targetId, content, jpeg);
        synchronized (lock) {
            long latency = Math.max(0L, System.currentTimeMillis() - acceptedAt);
            if (result.success) {
                replied++;
                lastReplyAtMs = System.currentTimeMillis();
                addAuditLocked(
                    "out",
                    targetType,
                    targetId,
                    "admin",
                    "manual_image",
                    "sent",
                    "JPEG " + jpeg.length + " bytes",
                    latency);
            } else {
                failed++;
                lastError = truncate(result.error, 500);
                addAuditLocked("out", targetType, targetId, "admin", "manual_image", "send_error", lastError, latency);
            }
        }
    }

    private boolean submit(Runnable task, QqBotConfig cfg) {
        synchronized (lock) {
            ensureWorkerLocked();
            if (!started || worker == null
                || worker.isShutdown()
                || worker.getQueue()
                    .size() >= Math.min(EXECUTOR_CAPACITY, cfg.maxQueuedRequests)) {
                dropped++;
                return false;
            }
            try {
                worker.execute(task);
                return true;
            } catch (RuntimeException e) {
                dropped++;
                return false;
            }
        }
    }

    private void maybeScheduleReportLocked(long nowMs) {
        QqBotConfig cfg = runtime.settings;
        if (cfg == null || !cfg.scheduledReportEnabled || !connected || cfg.scheduledReportTargets.isEmpty()) return;
        long interval = (long) cfg.scheduledReportIntervalMinutes * 60000L;
        if (lastScheduledReportAtMs > 0L && nowMs - lastScheduledReportAtMs < interval) return;
        lastScheduledReportAtMs = nowMs;
        final String report = QqBotCommandRouter.status(snapshot.copy(), cfg, true);
        for (String rawTarget : cfg.scheduledReportTargets) {
            final String normalized = QqBotConfigValidator.normalizeTarget(rawTarget);
            final int colon = normalized.indexOf(':');
            if (colon <= 0 || colon + 1 >= normalized.length()) continue;
            final String type = normalized.substring(0, colon);
            final String id = normalized.substring(colon + 1);
            submit(new Runnable() {

                @Override
                public void run() {
                    send(type, id, "", "", report, "scheduled_report", "system", System.currentTimeMillis());
                }
            }, cfg);
        }
    }

    private long nextScheduledReportAtLocked() {
        QqBotConfig cfg = runtime.settings;
        if (cfg == null || !cfg.scheduledReportEnabled) return 0L;
        if (lastScheduledReportAtMs == 0L) return System.currentTimeMillis();
        return lastScheduledReportAtMs + (long) cfg.scheduledReportIntervalMinutes * 60000L;
    }

    private boolean allowed(QqBotConfig cfg, QqBotMessage message) {
        if ("group".equals(message.targetType)) {
            if (!cfg.allowGroups) return false;
            if (!cfg.allowedGroupIds.isEmpty() && !cfg.allowedGroupIds.contains(message.targetId)) return false;
        } else if ("c2c".equals(message.targetType)) {
            if (!cfg.allowC2c) return false;
        } else if ("channel".equals(message.targetType)) {
            if (!cfg.allowChannels) return false;
            if (cfg.requireMention && "MESSAGE_CREATE".equals(message.eventType)) return false;
        } else {
            return false;
        }
        return cfg.allowedUserIds.isEmpty() || cfg.allowedUserIds.contains(message.senderId);
    }

    private boolean isDuplicateLocked(String id, long nowMs) {
        if (id == null || id.isEmpty()) return false;
        if (seenMessageIds.containsKey(id)) return true;
        seenMessageIds.put(id, nowMs);
        while (seenMessageIds.size() > MAX_DEDUPE_IDS) {
            seenMessageIds.remove(
                seenMessageIds.keySet()
                    .iterator()
                    .next());
        }
        return false;
    }

    private void cleanupLocked(long nowMs) {
        int ttlMinutes = runtime.settings == null ? 30 : runtime.settings.conversationTtlMinutes;
        long ttl = (long) ttlMinutes * 60000L;
        java.util.Iterator<Map.Entry<String, Conversation>> conversationsIt = conversations.entrySet()
            .iterator();
        while (conversationsIt.hasNext()) {
            Conversation value = conversationsIt.next()
                .getValue();
            if (nowMs - value.lastUsedAtMs > ttl) conversationsIt.remove();
        }
        cleanupTimeMap(userLastRequest, nowMs - Math.max(60000L, ttl));
        cleanupTimeMap(userLastAiRequest, nowMs - Math.max(60000L, ttl));
        java.util.Iterator<Map.Entry<String, Long>> dedupe = seenMessageIds.entrySet()
            .iterator();
        while (dedupe.hasNext()) if (nowMs - dedupe.next()
            .getValue()
            .longValue() > 3600000L) dedupe.remove();
    }

    private static void cleanupTimeMap(Map<String, Long> values, long cutoff) {
        java.util.Iterator<Map.Entry<String, Long>> iterator = values.entrySet()
            .iterator();
        while (iterator.hasNext()) if (iterator.next()
            .getValue()
            .longValue() < cutoff) iterator.remove();
    }

    private void clearConversation(String sessionKey) {
        synchronized (lock) {
            conversations.remove(sessionKey);
        }
    }

    private String aiSystemPrompt(QqBotConfig cfg, QqBotSnapshot current) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are ")
            .append(safe(cfg.botName))
            .append(", the QQ group assistant for a GTNH Minecraft server operated through TeXTech/WebAE. ");
        prompt.append("Answer in concise Chinese unless the user clearly uses another language. ");
        prompt.append(
            "You may explain gameplay and discuss the server, but you cannot execute commands, change permissions, reveal secrets, or claim actions you did not perform. ");
        prompt.append("Current read-only snapshot: online=")
            .append(current.onlinePlayers)
            .append('/')
            .append(current.maxPlayers)
            .append(", TPS=")
            .append(current.tps)
            .append(", MSPT=")
            .append(current.mspt)
            .append(", uptimeSeconds=")
            .append(current.uptimeSeconds)
            .append(", memoryMiB=")
            .append(current.usedMemoryMb)
            .append('/')
            .append(current.maxMemoryMb)
            .append(". ");
        if (!current.playerNames.isEmpty()) prompt.append("Online players: ")
            .append(current.playerNames)
            .append(". ");
        if (!safe(cfg.aiSystemPrompt).isEmpty()) prompt.append("Server administrator instruction: ")
            .append(cfg.aiSystemPrompt);
        return prompt.toString();
    }

    private void addAuditLocked(String direction, String targetType, String targetId, String senderId, String command,
        String outcome, String preview, long latencyMs) {
        QqBotConfig cfg = runtime.settings;
        if (cfg == null || !cfg.auditEnabled) return;
        AuditEntry entry = new AuditEntry();
        entry.id = Long.toHexString(System.currentTimeMillis()) + "-" + Long.toHexString(++auditSequence);
        entry.timestampMs = System.currentTimeMillis();
        entry.direction = direction;
        entry.targetType = targetType;
        entry.targetId = targetId;
        entry.senderId = senderId;
        entry.command = command;
        entry.outcome = outcome;
        entry.preview = truncate(safe(preview), 160);
        entry.latencyMs = latencyMs;
        audit.addLast(entry);
        int max = Math.max(20, cfg.auditMaxEntries);
        while (audit.size() > max) audit.pollFirst();
    }

    private void ensureWorkerLocked() {
        if (worker != null && !worker.isShutdown()) return;
        final AtomicInteger counter = new AtomicInteger();
        worker = new ThreadPoolExecutor(
            1,
            2,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(EXECUTOR_CAPACITY),
            new ThreadFactory() {

                @Override
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "WebAE-QQBot-Worker-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.AbortPolicy());
        worker.allowCoreThreadTimeOut(false);
    }

    private void stopClientLocked() {
        QqBotGatewayClient current = client;
        client = null;
        if (current != null) {
            try {
                current.stop();
            } catch (Exception ignored) {}
        }
    }

    private QqBotConfig settings() {
        synchronized (lock) {
            return copySettingsLocked();
        }
    }

    private QqBotConfig copySettingsLocked() {
        QqBotConfig current = runtime.settings == null ? new QqBotConfig() : runtime.settings;
        return new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(current), QqBotConfig.class);
    }

    private static String normalizeReply(String value, int max) {
        String result = safe(value).replace('\r', ' ')
            .trim();
        return truncate(result.isEmpty() ? "（无回复内容）" : result, Math.max(200, max));
    }

    private static String preview(String value) {
        return truncate(
            safe(value).replace('\r', ' ')
                .replace('\n', ' '),
            160);
    }

    private static String safeMessage(Exception error) {
        String value = error == null ? "" : error.getMessage();
        if (value == null || value.isEmpty()) return error == null ? "unknown error"
            : error.getClass()
                .getSimpleName();
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static final class Conversation {

        final Deque<Message> messages = new ArrayDeque<Message>();
        long lastUsedAtMs = System.currentTimeMillis();

        void add(String role, String content, int turns) {
            messages.addLast(new Message(role, content));
            int maxMessages = Math.max(2, turns * 2);
            while (messages.size() > maxMessages) messages.pollFirst();
        }

        boolean expired(QqBotConfig cfg, long nowMs) {
            return nowMs - lastUsedAtMs > (long) cfg.conversationTtlMinutes * 60000L;
        }

        List<Message> copyMessages() {
            List<Message> result = new ArrayList<Message>();
            for (Message message : messages) result.add(new Message(message.role, message.content));
            return result;
        }
    }

    public static final class Status {

        public boolean enabled;
        public boolean configured;
        public boolean running;
        public boolean connected;
        public String phase = "";
        public String lastError = "";
        public long lastConnectedAtMs;
        public long lastMessageAtMs;
        public long lastReplyAtMs;
        public long nextReconnectAtMs;
        public long received;
        public long replied;
        public long aiReplies;
        public long failed;
        public long dropped;
        public long rateLimited;
        public int queueDepth;
        public int queueCapacity;
        public int conversationCount;
        public long lastScheduledReportAtMs;
        public long nextScheduledReportAtMs;
        public long configUpdatedAt;
        public String configUpdatedBy = "";
        public QqBotSnapshot snapshot;
    }

    public static final class AuditEntry {

        public String id = "";
        public long timestampMs;
        public String direction = "";
        public String targetType = "";
        public String targetId = "";
        public String senderId = "";
        public String command = "";
        public String outcome = "";
        public String preview = "";
        public long latencyMs;

        AuditEntry copy() {
            AuditEntry value = new AuditEntry();
            value.id = id;
            value.timestampMs = timestampMs;
            value.direction = direction;
            value.targetType = targetType;
            value.targetId = targetId;
            value.senderId = senderId;
            value.command = command;
            value.outcome = outcome;
            value.preview = preview;
            value.latencyMs = latencyMs;
            return value;
        }
    }

    public static final class ManualSendResult {

        public final boolean success;
        public final String error;

        private ManualSendResult(boolean success, String error) {
            this.success = success;
            this.error = error == null ? "" : error;
        }

        static ManualSendResult ok() {
            return new ManualSendResult(true, "");
        }

        static ManualSendResult fail(String error) {
            return new ManualSendResult(false, error);
        }
    }
}
