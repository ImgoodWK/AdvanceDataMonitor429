package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates {@link WebAlertsConfig} before persisting to web-alerts.json.
 */
public final class WebAlertsConfigValidator {

    private WebAlertsConfigValidator() {}

    /**
     * @return error message, or {@code null} when valid
     */
    public static String validate(WebAlertsConfig cfg) {
        if (cfg == null) {
            return "Missing alert rules body";
        }
        if (cfg.pollIntervalSeconds < 1 || cfg.pollIntervalSeconds > 300) {
            return "pollIntervalSeconds must be between 1 and 300";
        }
        if (cfg.cpuStuckMinutes < 1 || cfg.cpuStuckMinutes > 120) {
            return "cpuStuckMinutes must be between 1 and 120";
        }
        if (cfg.channelThresholdPercent < 1 || cfg.channelThresholdPercent > 100) {
            return "channelThresholdPercent must be between 1 and 100";
        }
        if (cfg.channelThresholdAbsolute < 1 || cfg.channelThresholdAbsolute > 128) {
            return "channelThresholdAbsolute must be between 1 and 128";
        }
        String filterError = validateFilter(cfg.browserNotifications, "browserNotifications");
        if (filterError != null) {
            return filterError;
        }
        filterError = validateFilter(cfg.playerChat, "playerChat");
        if (filterError != null) {
            return filterError;
        }
        filterError = validateFilter(cfg.playerHud, "playerHud");
        if (filterError != null) {
            return filterError;
        }
        if (cfg.playerHud != null) {
            if (cfg.playerHud.durationSeconds < 2 || cfg.playerHud.durationSeconds > 120) {
                return "playerHud.durationSeconds must be between 2 and 120";
            }
            if (cfg.playerHud.maxVisible < 1 || cfg.playerHud.maxVisible > 8) {
                return "playerHud.maxVisible must be between 1 and 8";
            }
            if (!isHudPosition(cfg.playerHud.position)) {
                return "playerHud.position must be top_left, top_right, bottom_left, or bottom_right";
            }
        }
        if (cfg.notificationMaxDeliveriesPerAlert < 1 || cfg.notificationMaxDeliveriesPerAlert > 64) {
            return "notificationMaxDeliveriesPerAlert must be between 1 and 64";
        }
        if (cfg.notificationRetryMaxAttempts < 1 || cfg.notificationRetryMaxAttempts > 5) {
            return "notificationRetryMaxAttempts must be between 1 and 5";
        }
        if (cfg.notificationConnectTimeoutMs < 500 || cfg.notificationConnectTimeoutMs > 15000) {
            return "notificationConnectTimeoutMs must be between 500 and 15000";
        }
        if (cfg.notificationReadTimeoutMs < 500 || cfg.notificationReadTimeoutMs > 30000) {
            return "notificationReadTimeoutMs must be between 500 and 30000";
        }
        if (cfg.notificationCircuitBreakFailures < 1 || cfg.notificationCircuitBreakFailures > 20) {
            return "notificationCircuitBreakFailures must be between 1 and 20";
        }
        if (cfg.notificationCircuitBreakSeconds < 10 || cfg.notificationCircuitBreakSeconds > 3600) {
            return "notificationCircuitBreakSeconds must be between 10 and 3600";
        }
        if (cfg.inventoryThresholds != null) {
            for (int i = 0; i < cfg.inventoryThresholds.size(); i++) {
                String err = validateInventoryRule(cfg.inventoryThresholds.get(i), i);
                if (err != null) {
                    return err;
                }
            }
        }
        if (cfg.webhooks != null) {
            for (int i = 0; i < cfg.webhooks.size(); i++) {
                String err = validateWebhookRule(cfg.webhooks.get(i), i);
                if (err != null) {
                    return err;
                }
            }
        }
        if (cfg.notificationTargets != null) {
            if (cfg.notificationTargets.size() > 32) {
                return "notificationTargets supports at most 32 entries";
            }
            Set<String> ids = new HashSet<String>();
            for (int i = 0; i < cfg.notificationTargets.size(); i++) {
                WebAlertsConfig.NotificationTarget target = cfg.notificationTargets.get(i);
                String err = validateNotificationTarget(target, i);
                if (err != null) {
                    return err;
                }
                String id = trimOrEmpty(target.id);
                if (!ids.add(id)) {
                    return "notificationTargets[" + i + "]: duplicate id '" + id + "'";
                }
            }
        }
        if (cfg.automationRules != null) {
            for (int i = 0; i < cfg.automationRules.size(); i++) {
                String err = validateAutomationRule(cfg.automationRules.get(i), i);
                if (err != null) {
                    return err;
                }
            }
        }
        if (cfg.serverTpsBelowEnabled) {
            if (cfg.serverTpsThreshold < 1.0 || cfg.serverTpsThreshold > 20.0) {
                return "serverTpsThreshold must be between 1.0 and 20.0";
            }
            if (cfg.serverTpsDurationSeconds < 10 || cfg.serverTpsDurationSeconds > 600) {
                return "serverTpsDurationSeconds must be between 10 and 600";
            }
        }
        return null;
    }

    /** Normalize null lists and clamp version before save. */
    public static WebAlertsConfig normalize(WebAlertsConfig cfg) {
        if (cfg.version < 2) {
            cfg.version = 2;
        }
        cfg.pollIntervalSeconds = clamp(cfg.pollIntervalSeconds, 1, 300, 10);
        cfg.cpuStuckMinutes = clamp(cfg.cpuStuckMinutes, 1, 120, 5);
        cfg.channelThresholdPercent = clamp(cfg.channelThresholdPercent, 1, 100, 90);
        cfg.channelThresholdAbsolute = clamp(cfg.channelThresholdAbsolute, 1, 128, 28);
        cfg.browserNotifications = normalizeFilter(cfg.browserNotifications, true);
        cfg.playerChat = normalizeFilter(cfg.playerChat, true);
        cfg.playerHud = normalizeHudFilter(cfg.playerHud);
        if (cfg.inventoryThresholds == null) {
            cfg.inventoryThresholds = new ArrayList<WebAlertsConfig.InventoryThresholdRule>();
        }
        List<WebAlertsConfig.InventoryThresholdRule> cleaned = new ArrayList<WebAlertsConfig.InventoryThresholdRule>();
        for (WebAlertsConfig.InventoryThresholdRule rule : cfg.inventoryThresholds) {
            if (rule == null) {
                continue;
            }
            WebAlertsConfig.InventoryThresholdRule copy = new WebAlertsConfig.InventoryThresholdRule();
            copy.itemId = trimOrEmpty(rule.itemId);
            copy.fluidName = trimOrEmpty(rule.fluidName);
            copy.minAmount = rule.minAmount < 0L ? 0L : rule.minAmount;
            copy.networkId = rule.networkId;
            copy.label = trimOrEmpty(rule.label);
            if (copy.itemId.isEmpty() && copy.fluidName.isEmpty()) {
                continue;
            }
            cleaned.add(copy);
        }
        cfg.inventoryThresholds = cleaned;
        if (cfg.webhooks == null) {
            cfg.webhooks = new ArrayList<WebAlertsConfig.WebhookRule>();
        }
        List<WebAlertsConfig.WebhookRule> cleanedHooks = new ArrayList<WebAlertsConfig.WebhookRule>();
        for (WebAlertsConfig.WebhookRule hook : cfg.webhooks) {
            if (hook == null) {
                continue;
            }
            WebAlertsConfig.WebhookRule copy = new WebAlertsConfig.WebhookRule();
            copy.id = trimOrEmpty(hook.id);
            if (copy.id.isEmpty()) {
                copy.id = "webhook-" + (cleanedHooks.size() + 1);
            }
            copy.url = trimOrEmpty(hook.url);
            copy.enabled = hook.enabled;
            copy.mention = trimOrEmpty(hook.mention);
            copy.events = normalizeEvents(hook.events);
            if (copy.url.isEmpty() || copy.url.startsWith("***")
                || (!copy.url.startsWith("http://") && !copy.url.startsWith("https://"))) {
                continue;
            }
            cleanedHooks.add(copy);
        }
        cfg.webhooks = cleanedHooks;
        if (cfg.notificationTargets == null) {
            cfg.notificationTargets = new ArrayList<WebAlertsConfig.NotificationTarget>();
        }
        List<WebAlertsConfig.NotificationTarget> cleanedTargets = new ArrayList<WebAlertsConfig.NotificationTarget>();
        for (WebAlertsConfig.NotificationTarget target : cfg.notificationTargets) {
            if (target == null) {
                continue;
            }
            WebAlertsConfig.NotificationTarget copy = new WebAlertsConfig.NotificationTarget();
            copy.id = trimOrEmpty(target.id);
            if (copy.id.isEmpty()) {
                copy.id = "target-" + (cleanedTargets.size() + 1);
            }
            copy.type = trimOrEmpty(target.type).toLowerCase();
            copy.enabled = target.enabled;
            copy.events = normalizeEvents(target.events);
            copy.severities = normalizeSeverities(target.severities);
            copy.ownerUuids = normalizeStrings(target.ownerUuids);
            copy.url = trimOrEmpty(target.url);
            copy.appId = trimOrEmpty(target.appId);
            copy.appSecret = trimOrEmpty(target.appSecret);
            copy.baseUrl = trimOrEmpty(target.baseUrl);
            copy.tokenUrl = trimOrEmpty(target.tokenUrl);
            copy.targetType = trimOrEmpty(target.targetType).toLowerCase();
            copy.targetId = trimOrEmpty(target.targetId);
            copy.mode = trimOrEmpty(target.mode).toLowerCase();
            copy.templateId = trimOrEmpty(target.templateId);
            copy.templateUrl = trimOrEmpty(target.templateUrl);
            copy.corpId = trimOrEmpty(target.corpId);
            copy.corpSecret = trimOrEmpty(target.corpSecret);
            copy.agentId = target.agentId;
            copy.toUser = trimOrEmpty(target.toUser);
            copy.toParty = trimOrEmpty(target.toParty);
            copy.toTag = trimOrEmpty(target.toTag);
            copy.smtpHost = trimOrEmpty(target.smtpHost);
            copy.smtpPort = target.smtpPort;
            copy.smtpSecurity = trimOrEmpty(target.smtpSecurity).toLowerCase();
            copy.smtpUsername = trimOrEmpty(target.smtpUsername);
            copy.smtpPassword = trimOrEmpty(target.smtpPassword);
            copy.mailFrom = trimOrEmpty(target.mailFrom);
            copy.mailTo = normalizeStrings(target.mailTo);
            copy.mailCc = normalizeStrings(target.mailCc);
            copy.subjectPrefix = target.subjectPrefix == null ? "[WebAE]" : target.subjectPrefix.trim();
            cleanedTargets.add(copy);
        }
        cfg.notificationTargets = cleanedTargets;
        if (cfg.automationRules == null) {
            cfg.automationRules = new ArrayList<WebAlertsConfig.AutomationRule>();
        }
        List<WebAlertsConfig.AutomationRule> cleanedAuto = new ArrayList<WebAlertsConfig.AutomationRule>();
        for (WebAlertsConfig.AutomationRule rule : cfg.automationRules) {
            if (rule == null) {
                continue;
            }
            WebAlertsConfig.AutomationRule copy = new WebAlertsConfig.AutomationRule();
            copy.id = trimOrEmpty(rule.id);
            if (copy.id.isEmpty()) {
                copy.id = "auto-" + (cleanedAuto.size() + 1);
            }
            copy.enabled = rule.enabled;
            copy.type = trimOrEmpty(rule.type);
            if (copy.type.isEmpty()) {
                copy.type = "craft_when_below";
            }
            copy.itemId = trimOrEmpty(rule.itemId);
            copy.threshold = rule.threshold < 0L ? 0L : rule.threshold;
            copy.craftAmount = rule.craftAmount < 0L ? 0L : rule.craftAmount;
            copy.patternId = trimOrEmpty(rule.patternId);
            copy.cpuName = trimOrEmpty(rule.cpuName);
            copy.networkId = rule.networkId;
            copy.cooldownSeconds = rule.cooldownSeconds < 1 ? 300 : rule.cooldownSeconds;
            copy.requireCpuIdle = rule.requireCpuIdle;
            copy.maxTriggersPerHour = rule.maxTriggersPerHour < 1 ? 12 : rule.maxTriggersPerHour;
            if (copy.itemId.isEmpty()) {
                continue;
            }
            cleanedAuto.add(copy);
        }
        cfg.automationRules = cleanedAuto;
        if (cfg.automationMaxTriggersPerHour < 1) {
            cfg.automationMaxTriggersPerHour = 12;
        }
        if (cfg.serverTpsThreshold < 1.0) {
            cfg.serverTpsThreshold = 1.0;
        }
        if (cfg.serverTpsThreshold > 20.0) {
            cfg.serverTpsThreshold = 20.0;
        }
        if (cfg.serverTpsDurationSeconds < 10) {
            cfg.serverTpsDurationSeconds = 10;
        }
        if (cfg.serverTpsDurationSeconds > 600) {
            cfg.serverTpsDurationSeconds = 600;
        }
        cfg.notificationMaxDeliveriesPerAlert = clamp(cfg.notificationMaxDeliveriesPerAlert, 1, 64, 16);
        cfg.notificationRetryMaxAttempts = clamp(cfg.notificationRetryMaxAttempts, 1, 5, 3);
        cfg.notificationConnectTimeoutMs = clamp(cfg.notificationConnectTimeoutMs, 500, 15000, 3000);
        cfg.notificationReadTimeoutMs = clamp(cfg.notificationReadTimeoutMs, 500, 30000, 5000);
        cfg.notificationCircuitBreakFailures = clamp(cfg.notificationCircuitBreakFailures, 1, 20, 5);
        cfg.notificationCircuitBreakSeconds = clamp(cfg.notificationCircuitBreakSeconds, 10, 3600, 60);
        return cfg;
    }

    /**
     * Merge masked webhook URLs from incoming PUT with persisted secrets.
     */
    public static WebAlertsConfig mergeWebhookSecrets(WebAlertsConfig incoming, WebAlertsConfig existing) {
        if (incoming == null || existing == null) {
            return incoming;
        }
        if (incoming.webhooks != null) {
            Map<String, String> urlById = new HashMap<String, String>();
            if (existing.webhooks != null) {
                for (WebAlertsConfig.WebhookRule old : existing.webhooks) {
                    if (old != null && old.id != null && old.url != null && !old.url.isEmpty()) {
                        urlById.put(old.id, old.url);
                    }
                }
            }
            for (WebAlertsConfig.WebhookRule hook : incoming.webhooks) {
                if (hook == null) {
                    continue;
                }
                String url = trimOrEmpty(hook.url);
                if (url.startsWith("***") || url.isEmpty()) {
                    String preserved = urlById.get(hook.id);
                    hook.url = preserved != null ? preserved : "";
                }
            }
        }
        mergeNotificationTargetSecrets(incoming, existing);
        return incoming;
    }

    private static void mergeNotificationTargetSecrets(WebAlertsConfig incoming, WebAlertsConfig existing) {
        if (incoming.notificationTargets == null) {
            return;
        }
        Map<String, WebAlertsConfig.NotificationTarget> oldById = new HashMap<String, WebAlertsConfig.NotificationTarget>();
        if (existing.notificationTargets != null) {
            for (WebAlertsConfig.NotificationTarget target : existing.notificationTargets) {
                if (target != null && target.id != null) {
                    oldById.put(target.id, target);
                }
            }
        }
        for (WebAlertsConfig.NotificationTarget target : incoming.notificationTargets) {
            if (target == null) {
                continue;
            }
            WebAlertsConfig.NotificationTarget old = oldById.get(target.id);
            target.url = preserveSecret(target.url, old == null ? null : old.url);
            target.appSecret = preserveSecret(target.appSecret, old == null ? null : old.appSecret);
            target.corpSecret = preserveSecret(target.corpSecret, old == null ? null : old.corpSecret);
            target.smtpPassword = preserveSecret(target.smtpPassword, old == null ? null : old.smtpPassword);
        }
    }

    private static String preserveSecret(String incoming, String existing) {
        String value = trimOrEmpty(incoming);
        if (value.isEmpty() || value.startsWith("***")) {
            return existing == null ? "" : existing;
        }
        return value;
    }

    private static List<String> normalizeEvents(List<String> events) {
        List<String> out = new ArrayList<String>();
        if (events == null) {
            return out;
        }
        for (String ev : events) {
            if (ev == null) {
                continue;
            }
            String trimmed = ev.trim();
            if (!trimmed.isEmpty() && !out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static List<String> normalizeSeverities(List<String> severities) {
        List<String> out = new ArrayList<String>();
        if (severities == null) {
            return out;
        }
        for (String severity : severities) {
            String value = trimOrEmpty(severity).toLowerCase();
            if (isKnownSeverity(value) && !out.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }

    private static List<String> normalizeStrings(List<String> values) {
        List<String> out = new ArrayList<String>();
        if (values == null) {
            return out;
        }
        for (String value : values) {
            String trimmed = trimOrEmpty(value);
            if (isKnownEvent(trimmed) && !out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static WebAlertsConfig.NotificationFilter normalizeFilter(WebAlertsConfig.NotificationFilter source,
        boolean defaultEnabled) {
        WebAlertsConfig.NotificationFilter copy = new WebAlertsConfig.NotificationFilter();
        copy.enabled = source == null ? defaultEnabled : source.enabled;
        copy.events = normalizeEvents(source == null ? null : source.events);
        copy.severities = normalizeSeverities(source == null ? null : source.severities);
        return copy;
    }

    private static WebAlertsConfig.HudNotificationFilter normalizeHudFilter(
        WebAlertsConfig.HudNotificationFilter source) {
        WebAlertsConfig.HudNotificationFilter copy = new WebAlertsConfig.HudNotificationFilter();
        copy.enabled = source == null || source.enabled;
        copy.events = normalizeEvents(source == null ? null : source.events);
        copy.severities = normalizeSeverities(source == null ? null : source.severities);
        if (source == null) {
            copy.severities.add("warning");
            copy.severities.add("error");
        }
        copy.durationSeconds = source == null ? 10 : source.durationSeconds;
        copy.maxVisible = source == null ? 3 : source.maxVisible;
        copy.position = source == null ? "top_right" : trimOrEmpty(source.position).toLowerCase();
        copy.soundEnabled = source != null && source.soundEnabled;
        return copy;
    }

    private static String validateFilter(WebAlertsConfig.NotificationFilter filter, String path) {
        if (filter == null) {
            return null;
        }
        if (filter.events != null) {
            for (String event : filter.events) {
                if (!isKnownEvent(trimOrEmpty(event))) {
                    return path + ": unknown event '" + event + "'";
                }
            }
        }
        if (filter.severities != null) {
            for (String severity : filter.severities) {
                if (!isKnownSeverity(trimOrEmpty(severity).toLowerCase())) {
                    return path + ": unknown severity '" + severity + "'";
                }
            }
        }
        return null;
    }

    private static String validateNotificationTarget(WebAlertsConfig.NotificationTarget target, int index) {
        String path = "notificationTargets[" + index + "]";
        if (target == null) {
            return path + " is null";
        }
        if (trimOrEmpty(target.id).isEmpty()) {
            return path + ": id required";
        }
        String filterError = validateFilter(target, path);
        if (filterError != null) {
            return filterError;
        }
        if (target.ownerUuids != null) {
            if (target.ownerUuids.size() > 64) {
                return path + ": ownerUuids supports at most 64 entries";
            }
            for (String owner : target.ownerUuids) {
                try {
                    UUID.fromString(trimOrEmpty(owner));
                } catch (IllegalArgumentException e) {
                    return path + ": invalid owner UUID '" + owner + "'";
                }
            }
        }
        String type = trimOrEmpty(target.type).toLowerCase();
        if (!"qq_official".equals(type) && !"wechat_official".equals(type)
            && !"email".equals(type)
            && !"wecom_bot".equals(type)
            && !"wecom_app".equals(type)) {
            return path + ": unsupported type '" + target.type + "'";
        }
        String urlError = validateOptionalHttpUrl(target.baseUrl, path + ".baseUrl");
        if (urlError != null) return urlError;
        urlError = validateOptionalHttpUrl(target.tokenUrl, path + ".tokenUrl");
        if (urlError != null) return urlError;
        if ("qq_official".equals(type)) {
            if (trimOrEmpty(target.appId).isEmpty() || trimOrEmpty(target.appSecret).isEmpty()
                || trimOrEmpty(target.targetId).isEmpty()) {
                return path + ": QQ official bot requires appId, appSecret, and targetId";
            }
            String targetType = trimOrEmpty(target.targetType).toLowerCase();
            if (!"group".equals(targetType) && !"c2c".equals(targetType) && !"channel".equals(targetType)) {
                return path + ": QQ targetType must be group, c2c, or channel";
            }
        } else if ("wechat_official".equals(type)) {
            if (trimOrEmpty(target.appId).isEmpty() || trimOrEmpty(target.appSecret).isEmpty()
                || trimOrEmpty(target.targetId).isEmpty()) {
                return path + ": WeChat Official Account requires appId, appSecret, and targetId(openid)";
            }
            String mode = trimOrEmpty(target.mode).toLowerCase();
            if (!"customer_service".equals(mode) && !"template".equals(mode)) {
                return path + ": WeChat mode must be customer_service or template";
            }
            if ("template".equals(mode) && trimOrEmpty(target.templateId).isEmpty()) {
                return path + ": templateId required for WeChat template mode";
            }
        } else if ("email".equals(type)) {
            if (trimOrEmpty(target.smtpHost).isEmpty() || target.smtpPort < 1
                || target.smtpPort > 65535
                || trimOrEmpty(target.mailFrom).isEmpty()
                || target.mailTo == null
                || target.mailTo.isEmpty()) {
                return path + ": email requires smtpHost, valid smtpPort, mailFrom, and mailTo";
            }
            String security = trimOrEmpty(target.smtpSecurity).toLowerCase();
            if (!"none".equals(security) && !"starttls".equals(security) && !"ssl".equals(security)) {
                return path + ": smtpSecurity must be none, starttls, or ssl";
            }
            if (!trimOrEmpty(target.smtpUsername).isEmpty() && trimOrEmpty(target.smtpPassword).isEmpty()) {
                return path + ": smtpPassword required when smtpUsername is configured";
            }
            if (target.mailTo.size() > 32 || (target.mailCc != null && target.mailCc.size() > 32)) {
                return path + ": email recipient list supports at most 32 to and 32 cc addresses";
            }
            if (hasLineBreak(target.mailFrom) || hasLineBreak(target.subjectPrefix)) {
                return path + ": email headers cannot contain CR/LF";
            }
            for (String recipient : target.mailTo) {
                if (hasLineBreak(recipient)) return path + ": mailTo cannot contain CR/LF";
            }
            if (target.mailCc != null) {
                for (String recipient : target.mailCc) {
                    if (hasLineBreak(recipient)) return path + ": mailCc cannot contain CR/LF";
                }
            }
        } else if ("wecom_bot".equals(type)) {
            String url = trimOrEmpty(target.url);
            if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                return path + ": WeCom bot requires an http(s) webhook URL";
            }
        } else if ("wecom_app".equals(type)) {
            if (trimOrEmpty(target.corpId).isEmpty() || trimOrEmpty(target.corpSecret).isEmpty()
                || target.agentId <= 0) {
                return path + ": WeCom app requires corpId, corpSecret, and agentId > 0";
            }
            if (trimOrEmpty(target.toUser).isEmpty() && trimOrEmpty(target.toParty).isEmpty()
                && trimOrEmpty(target.toTag).isEmpty()) {
                return path + ": WeCom app requires toUser, toParty, or toTag";
            }
        }
        return null;
    }

    private static String validateOptionalHttpUrl(String value, String path) {
        String url = trimOrEmpty(value);
        if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            return path + " must start with http:// or https://";
        }
        return null;
    }

    private static boolean isHudPosition(String position) {
        String value = trimOrEmpty(position).toLowerCase();
        return "top_left".equals(value) || "top_right".equals(value)
            || "bottom_left".equals(value)
            || "bottom_right".equals(value);
    }

    private static boolean isKnownSeverity(String severity) {
        return "info".equals(severity) || "warning".equals(severity) || "error".equals(severity);
    }

    private static boolean hasLineBreak(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private static String validateWebhookRule(WebAlertsConfig.WebhookRule rule, int index) {
        if (rule == null) {
            return "webhooks[" + index + "] is null";
        }
        String url = trimOrEmpty(rule.url);
        if (!url.isEmpty() && !url.startsWith("***")) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return "webhooks[" + index + "]: url must start with http:// or https://";
            }
        }
        if (rule.events != null) {
            for (String ev : rule.events) {
                if (ev != null && !isKnownEvent(ev.trim())) {
                    return "webhooks[" + index + "]: unknown event '" + ev + "'";
                }
            }
        }
        return null;
    }

    private static boolean isKnownEvent(String ev) {
        return "inventory_threshold".equals(ev) || "cpu_stuck".equals(ev)
            || "gt_error".equals(ev)
            || "order_complete".equals(ev)
            || "channel_overload".equals(ev)
            || "server_tps_below".equals(ev)
            || "automation_craft".equals(ev);
    }

    private static String validateAutomationRule(WebAlertsConfig.AutomationRule rule, int index) {
        if (rule == null) {
            return "automationRules[" + index + "] is null";
        }
        if (!"craft_when_below".equals(trimOrEmpty(rule.type))) {
            return "automationRules[" + index + "]: unsupported type (only craft_when_below)";
        }
        if (trimOrEmpty(rule.itemId).isEmpty()) {
            return "automationRules[" + index + "]: itemId required";
        }
        if (rule.threshold < 1L) {
            return "automationRules[" + index + "]: threshold must be >= 1";
        }
        if (rule.cooldownSeconds < 1 || rule.cooldownSeconds > 86400) {
            return "automationRules[" + index + "]: cooldownSeconds must be 1–86400";
        }
        if (rule.networkId < -1) {
            return "automationRules[" + index + "]: networkId must be >= -1";
        }
        if (rule.maxTriggersPerHour < 1 || rule.maxTriggersPerHour > 60) {
            return "automationRules[" + index + "]: maxTriggersPerHour must be 1–60";
        }
        return null;
    }

    private static String validateInventoryRule(WebAlertsConfig.InventoryThresholdRule rule, int index) {
        if (rule == null) {
            return "inventoryThresholds[" + index + "] is null";
        }
        String itemId = trimOrEmpty(rule.itemId);
        String fluid = trimOrEmpty(rule.fluidName);
        if (itemId.isEmpty() && fluid.isEmpty()) {
            return "inventoryThresholds[" + index + "]: itemId or fluidName required";
        }
        if (rule.minAmount < 0L) {
            return "inventoryThresholds[" + index + "]: minAmount must be >= 0";
        }
        if (rule.networkId < -1) {
            return "inventoryThresholds[" + index + "]: networkId must be >= -1";
        }
        return null;
    }

    private static String trimOrEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
