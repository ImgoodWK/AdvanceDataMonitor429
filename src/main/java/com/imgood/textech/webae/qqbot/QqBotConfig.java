package com.imgood.textech.webae.qqbot;

import java.util.ArrayList;
import java.util.List;

/**
 * Public/admin-editable QQ bot settings. The plaintext {@link #appSecret} is
 * accepted only on update and is never returned by the API or persisted as-is.
 */
public final class QqBotConfig {

    public int version = 1;
    public boolean enabled;
    public String appId = "";
    public String appSecret = "";
    public boolean appSecretConfigured;
    public String appSecretHint = "";
    public String apiBase = "";
    public String tokenUrl = "";

    public String botName = "ADM";
    public String commandPrefix = "/";
    public boolean allowGroups = true;
    public boolean allowC2c = true;
    public boolean allowChannels;
    public boolean requireMention = true;
    public boolean replyUnknownWithHelp = true;
    /** Empty means all groups seen by the configured bot application. */
    public List<String> allowedGroupIds = new ArrayList<String>();
    /** Empty means all users inside an allowed target. */
    public List<String> allowedUserIds = new ArrayList<String>();
    /** QQ user openids allowed to use bot administration commands. */
    public List<String> adminUserIds = new ArrayList<String>();

    public boolean statusCommandEnabled = true;
    public boolean playersCommandEnabled = true;
    public boolean playerListCommandEnabled = true;
    public boolean tpsCommandEnabled = true;
    public boolean memoryCommandEnabled = true;
    public boolean uptimeCommandEnabled = true;
    public boolean aboutCommandEnabled = true;

    public boolean aiEnabled = true;
    /** Unknown natural-language messages become AI chat when enabled. */
    public boolean aiAutoReply = true;
    /** Reuse the shared WebAE search configuration before AI completion. */
    public boolean aiWebSearch;
    public String aiSystemPrompt = "";
    public int maxConversationTurns = 8;
    public int conversationTtlMinutes = 30;

    /**
     * Optional shared-bot routing with AstrBot. Default off keeps WebAE independent.
     * When enabled, non-WebAE intents are silently ignored so AstrBot can answer.
     */
    public boolean astrBotCompatEnabled;
    /** Leading tokens that force WebAE ownership (case-insensitive ASCII). */
    public List<String> webaeExplicitPrefixes = new ArrayList<String>(QqBotIntentClassifier.DEFAULT_WEBAE_PREFIXES);
    /** Leading tokens that force AstrBot ownership. */
    public List<String> astrBotExplicitPrefixes = new ArrayList<String>(QqBotIntentClassifier.DEFAULT_ASTRBOT_PREFIXES);
    /** Substring keywords that classify a message as WebAE-owned when compat is on. */
    public List<String> webaeIntentKeywords = new ArrayList<String>(QqBotIntentClassifier.DEFAULT_WEBAE_KEYWORDS);

    public int userCooldownSeconds = 2;
    public int aiCooldownSeconds = 10;
    public int maxInputChars = 800;
    public int maxReplyChars = 1800;
    public int maxQueuedRequests = 128;

    public boolean scheduledReportEnabled;
    public int scheduledReportIntervalMinutes = 60;
    /** Target ids formatted as group id, c2c:user id, or channel:channel id. */
    public List<String> scheduledReportTargets = new ArrayList<String>();
    public boolean scheduledReportIncludePlayers = true;
    public boolean scheduledReportIncludeMemory = true;

    public boolean auditEnabled = true;
    public int auditMaxEntries = 200;

    public QqBotConfig() {}
}
