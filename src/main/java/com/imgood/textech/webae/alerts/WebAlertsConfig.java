package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed TeXTech/WebAE/web-alerts.json (automation rules).
 */
public final class WebAlertsConfig {

    public int version = 2;
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
    /** Existing WebAE toast/browser notification route. */
    public NotificationFilter browserNotifications = defaultBrowserNotifications();
    /** Direct message to the alert owner's in-game chat. */
    public NotificationFilter playerChat = defaultPlayerChat();
    /** Client HUD route for the alert owner. */
    public HudNotificationFilter playerHud = defaultPlayerHud();
    /** QQ official bot, WeChat Official Account, email, and WeCom targets. */
    public List<NotificationTarget> notificationTargets = new ArrayList<NotificationTarget>();
    /** Per-alert fan-out budget across external targets. */
    public int notificationMaxDeliveriesPerAlert = 16;
    public int notificationRetryMaxAttempts = 3;
    public int notificationConnectTimeoutMs = 3000;
    public int notificationReadTimeoutMs = 5000;
    public int notificationCircuitBreakFailures = 5;
    public int notificationCircuitBreakSeconds = 60;

    private static NotificationFilter defaultBrowserNotifications() {
        NotificationFilter filter = new NotificationFilter();
        filter.enabled = true;
        return filter;
    }

    private static NotificationFilter defaultPlayerChat() {
        NotificationFilter filter = new NotificationFilter();
        filter.enabled = true;
        return filter;
    }

    private static HudNotificationFilter defaultPlayerHud() {
        HudNotificationFilter filter = new HudNotificationFilter();
        filter.enabled = true;
        filter.severities.add("warning");
        filter.severities.add("error");
        return filter;
    }

    public static class NotificationFilter {

        public boolean enabled = true;
        /** Empty means all known alert event types. */
        public List<String> events = new ArrayList<String>();
        /** Empty means all severities. */
        public List<String> severities = new ArrayList<String>();
    }

    public static final class HudNotificationFilter extends NotificationFilter {

        public int durationSeconds = 10;
        public int maxVisible = 3;
        /** top_left, top_right, bottom_left, or bottom_right. */
        public String position = "top_right";
        public boolean soundEnabled = false;
    }

    /**
     * External notification destination. Fields are interpreted by {@link #type}; keeping a single
     * DTO makes the JSON/API forward-compatible while the dispatcher remains transport-pluggable.
     */
    public static final class NotificationTarget extends NotificationFilter {

        public String id = "";
        /** qq_official, wechat_official, email, wecom_bot, or wecom_app. */
        public String type = "email";
        /** Empty means alerts for every owner. */
        public List<String> ownerUuids = new ArrayList<String>();

        /** Webhook endpoint for wecom_bot. Never exposed in full to browsers. */
        public String url = "";
        public transient boolean urlConfigured;

        /** QQ/WeChat application identity and secret. */
        public String appId = "";
        public String appSecret = "";
        public transient boolean appSecretConfigured;
        /** Optional platform API base override; empty selects the official production endpoint. */
        public String baseUrl = "";
        /** Optional OAuth/token endpoint override. */
        public String tokenUrl = "";
        /** QQ: group, c2c, or channel. */
        public String targetType = "group";
        /** QQ group_openid/user openid/channel id, or WeChat Official Account user openid. */
        public String targetId = "";

        /** WeChat Official Account: customer_service or template. */
        public String mode = "customer_service";
        public String templateId = "";
        public String templateUrl = "";

        /** WeCom application-message credentials and recipients. */
        public String corpId = "";
        public String corpSecret = "";
        public transient boolean corpSecretConfigured;
        public int agentId;
        public String toUser = "";
        public String toParty = "";
        public String toTag = "";

        /** SMTP destination. */
        public String smtpHost = "";
        public int smtpPort = 587;
        /** none, starttls, or ssl. */
        public String smtpSecurity = "starttls";
        public String smtpUsername = "";
        public String smtpPassword = "";
        public transient boolean smtpPasswordConfigured;
        public String mailFrom = "";
        public List<String> mailTo = new ArrayList<String>();
        public List<String> mailCc = new ArrayList<String>();
        public String subjectPrefix = "[WebAE]";
    }

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
