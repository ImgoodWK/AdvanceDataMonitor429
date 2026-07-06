package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed config/textech/web-alerts.json (automation rules).
 */
public final class WebAlertsConfig {

    public int version = 1;
    public boolean enabled = true;
    public int pollIntervalSeconds = 10;
    public List<InventoryThresholdRule> inventoryThresholds = new ArrayList<InventoryThresholdRule>();
    public int cpuStuckMinutes = 5;
    public boolean gtErrorEnabled = true;
    public boolean orderCompleteEnabled = true;
    public int channelThresholdPercent = 90;
    public int channelThresholdAbsolute = 28;
    public List<WebhookRule> webhooks = new ArrayList<WebhookRule>();
    public boolean serverTpsBelowEnabled = false;
    public double serverTpsThreshold = 15.0;
    public int serverTpsDurationSeconds = 60;
    public List<AutomationRule> automationRules = new ArrayList<AutomationRule>();
    /** Default max automation triggers per rule per hour when rule omits the field. */
    public int automationMaxTriggersPerHour = 12;

    public static final class AutomationRule {

        public String id = "";
        public boolean enabled = true;
        /** Currently only {@code craft_when_below}. */
        public String type = "craft_when_below";
        public String itemId = "";
        public long threshold = 0L;
        /** Craft quantity; {@code 0} = auto (gap to threshold, min 64). */
        public long craftAmount = 0L;
        public String patternId = "";
        public String cpuName = "";
        public int networkId = -1;
        public int cooldownSeconds = 300;
        public boolean requireCpuIdle = true;
        public int maxTriggersPerHour = 12;
    }

    public static final class WebhookRule {

        public String id = "";
        /** Full URL; never exposed to browser (see {@link WebhookDispatcher#sanitizeForClient}). */
        public String url = "";
        /** Client-only mirror: {@code true} when a URL is configured on disk. */
        public transient boolean urlConfigured;
        public boolean enabled = true;
        public List<String> events = new ArrayList<String>();
        /** Optional Discord mention prefix (e.g. {@code @here}). */
        public String mention = "";
    }

    public static final class InventoryThresholdRule {

        public String itemId = "";
        public String fluidName = "";
        public long minAmount = 0L;
        public int networkId = -1;
        public String label = "";
    }
}
