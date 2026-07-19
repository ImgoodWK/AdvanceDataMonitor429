package com.imgood.textech.webae.qqbot;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Validation and normalization for {@code qq-bot.json}. */
public final class QqBotConfigValidator {

    private QqBotConfigValidator() {}

    public static QqBotConfig normalize(QqBotConfig source) {
        QqBotConfig cfg = source == null ? new QqBotConfig() : source;
        cfg.version = 1;
        cfg.appId = trim(cfg.appId, 128);
        cfg.appSecret = trim(cfg.appSecret, 8192);
        cfg.apiBase = trimTrailingSlash(trim(cfg.apiBase, 512));
        cfg.tokenUrl = trim(cfg.tokenUrl, 512);
        cfg.botName = defaultValue(trim(cfg.botName, 32), "ADM");
        cfg.commandPrefix = trim(cfg.commandPrefix, 8);
        cfg.allowedGroupIds = normalizeIds(cfg.allowedGroupIds, 256, 256);
        cfg.allowedUserIds = normalizeIds(cfg.allowedUserIds, 512, 256);
        cfg.adminUserIds = normalizeIds(cfg.adminUserIds, 128, 256);
        cfg.aiSystemPrompt = trim(cfg.aiSystemPrompt, 4000);
        cfg.maxConversationTurns = clamp(cfg.maxConversationTurns, 1, 20);
        cfg.conversationTtlMinutes = clamp(cfg.conversationTtlMinutes, 5, 1440);
        cfg.userCooldownSeconds = clamp(cfg.userCooldownSeconds, 0, 60);
        cfg.aiCooldownSeconds = clamp(cfg.aiCooldownSeconds, 1, 300);
        cfg.maxInputChars = clamp(cfg.maxInputChars, 64, 4000);
        cfg.maxReplyChars = clamp(cfg.maxReplyChars, 200, 2000);
        cfg.maxQueuedRequests = clamp(cfg.maxQueuedRequests, 16, 512);
        cfg.scheduledReportIntervalMinutes = clamp(cfg.scheduledReportIntervalMinutes, 5, 10080);
        cfg.scheduledReportTargets = normalizeTargets(cfg.scheduledReportTargets);
        cfg.auditMaxEntries = clamp(cfg.auditMaxEntries, 20, 1000);
        return cfg;
    }

    public static String validate(QqBotConfig source, boolean secretConfigured) {
        QqBotConfig cfg = normalize(source);
        if (cfg.enabled && cfg.appId.isEmpty()) return "QQ bot AppID is required when enabled.";
        if (cfg.enabled && !secretConfigured && cfg.appSecret.isEmpty()) {
            return "QQ bot ClientSecret is required when enabled.";
        }
        String endpointError = validateEndpoint(cfg.apiBase, "apiBase");
        if (endpointError != null) return endpointError;
        endpointError = validateEndpoint(cfg.tokenUrl, "tokenUrl");
        if (endpointError != null) return endpointError;
        if (!cfg.allowGroups && !cfg.allowC2c && !cfg.allowChannels) {
            return "At least one QQ message target type must be enabled.";
        }
        if (cfg.scheduledReportEnabled && cfg.scheduledReportTargets.isEmpty()) {
            return "Scheduled reports require at least one target.";
        }
        return null;
    }

    static String normalizeTarget(String value) {
        String target = trim(value, 300);
        if (target.isEmpty()) return "";
        if (target.startsWith("group:") || target.startsWith("c2c:") || target.startsWith("channel:")) {
            int colon = target.indexOf(':');
            return colon + 1 < target.length() ? target : "";
        }
        return "group:" + target;
    }

    private static List<String> normalizeTargets(List<String> values) {
        Set<String> result = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeTarget(value);
                if (!normalized.isEmpty() && result.size() < 128) result.add(normalized);
            }
        }
        return new ArrayList<String>(result);
    }

    private static List<String> normalizeIds(List<String> values, int maxEntries, int maxLength) {
        Set<String> result = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                String id = trim(value, maxLength);
                if (!id.isEmpty() && result.size() < maxEntries) result.add(id);
            }
        }
        return new ArrayList<String>(result);
    }

    private static String validateEndpoint(String value, String field) {
        if (value == null || value.isEmpty()) return null;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!"https".equals(scheme) && !isLoopbackHttp(uri, scheme)) {
                return field + " must use HTTPS (HTTP is allowed only for loopback development endpoints).";
            }
            if (uri.getHost() == null || uri.getHost().isEmpty() || uri.getUserInfo() != null
                || uri.getFragment() != null) {
                return field + " is not a valid service URL.";
            }
        } catch (Exception e) {
            return field + " is not a valid URL.";
        }
        return null;
    }

    private static boolean isLoopbackHttp(URI uri, String scheme) {
        if (!"http".equals(scheme)) return false;
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        return "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host);
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String trim(String value, int max) {
        String result = value == null ? "" : value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
