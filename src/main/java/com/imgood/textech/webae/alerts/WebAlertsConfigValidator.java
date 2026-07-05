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
        List<WebAlertsConfig.InventoryThresholdRule> cleaned =
            new ArrayList<WebAlertsConfig.InventoryThresholdRule>();
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
        return cfg;
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
