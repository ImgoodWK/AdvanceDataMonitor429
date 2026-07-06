package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.List;

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
        if (cfg.version < 1) {
            cfg.version = 1;
        }
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
            if (copy.url.isEmpty() || copy.url.startsWith("***")) {
                continue;
            }
            cleanedHooks.add(copy);
        }
        cfg.webhooks = cleanedHooks;
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
        return cfg;
    }

    /**
     * Merge masked webhook URLs from incoming PUT with persisted secrets.
     */
    public static WebAlertsConfig mergeWebhookSecrets(WebAlertsConfig incoming, WebAlertsConfig existing) {
        if (incoming == null || existing == null || incoming.webhooks == null) {
            return incoming;
        }
        java.util.Map<String, String> urlById = new java.util.HashMap<String, String>();
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
                if (preserved != null) {
                    hook.url = preserved;
                }
            }
        }
        return incoming;
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
