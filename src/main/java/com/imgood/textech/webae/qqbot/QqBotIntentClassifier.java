package com.imgood.textech.webae.qqbot;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Optional ownership router for sharing one QQ official bot with AstrBot.
 * When {@link QqBotConfig#astrBotCompatEnabled} is false, every message stays WebAE.
 */
public final class QqBotIntentClassifier {

    public enum Owner {
        WEBAE,
        ASTRBOT
    }

    public static final List<String> DEFAULT_WEBAE_PREFIXES = Arrays.asList("webae", "游戏", "mc", "gtnh", "服务器");
    public static final List<String> DEFAULT_ASTRBOT_PREFIXES = Arrays.asList("tt");
    public static final List<String> DEFAULT_WEBAE_KEYWORDS = Arrays.asList(
        "webae",
        "textech",
        "gtnh",
        "tps",
        "mspt",
        "仪表盘",
        "告警",
        "在线玩家",
        "服务器状态",
        "adm",
        "高级数据",
        "监视器",
        "内存",
        "开服",
        "谁在线");

    private static final String[] WEBAE_COMMAND_VERBS = { "help", "menu", "commands", "帮助", "菜单", "功能", "命令", "ping",
        "在吗", "测试", "status", "server", "report", "状态", "服务器", "概况", "播报", "players", "online", "人数", "在线", "在线人数",
        "list", "who", "名单", "谁在线", "玩家列表", "tps", "mspt", "性能", "延迟", "memory", "mem", "内存", "ram", "uptime", "运行时间",
        "开服时间", "about", "version", "关于", "版本", "reset", "forget", "clear", "重置对话", "忘记对话", "清空对话", "ai", "ask", "chat",
        "问", "对话", "botstatus", "机器人状态" };
    /**
     * Do not let a URL's protocol/host/path accidentally claim a shared-bot
     * message for WebAE. For example, {@code https://textech.top/tps} contains
     * both the default {@code tps} and {@code textech} keywords, but it should
     * still reach AstrBot's link-summary plugin. Keep this deliberately small:
     * URL extraction is only used for routing, not for validating or fetching
     * the link (the downstream plugin owns that boundary).
     */
    private static final Pattern URL_PATTERN = Pattern
        .compile("https?://[^\\s<>\\\"'`，。！？；：、）】》」』]+", Pattern.CASE_INSENSITIVE);

    private QqBotIntentClassifier() {}

    public static Decision classify(QqBotConfig cfg, String rawText) {
        String raw = safe(rawText).trim();
        Decision decision = new Decision();
        decision.rawText = raw;
        if (cfg == null || !cfg.astrBotCompatEnabled) {
            decision.owner = Owner.WEBAE;
            decision.textForHandler = raw;
            decision.reason = "compat_off";
            return decision;
        }

        PrefixHit webaePrefix = matchLeadingToken(raw, cfg.webaeExplicitPrefixes, DEFAULT_WEBAE_PREFIXES, false);
        if (webaePrefix.matched) {
            decision.owner = Owner.WEBAE;
            decision.textForHandler = webaePrefix.remainder;
            decision.reason = "explicit_webae:" + webaePrefix.token;
            return decision;
        }

        PrefixHit astrPrefix = matchLeadingToken(raw, cfg.astrBotExplicitPrefixes, DEFAULT_ASTRBOT_PREFIXES, true);
        if (astrPrefix.matched) {
            decision.owner = Owner.ASTRBOT;
            decision.textForHandler = astrPrefix.remainder;
            decision.reason = "explicit_astrbot:" + astrPrefix.token;
            return decision;
        }

        String commandText = stripCommandPrefix(raw, cfg == null ? "/" : cfg.commandPrefix);
        String verb = firstWord(commandText.toLowerCase(Locale.ROOT));
        if (isWebaeCommandVerb(verb)) {
            decision.owner = Owner.WEBAE;
            decision.textForHandler = raw;
            decision.reason = "webae_command:" + verb;
            return decision;
        }

        String keyword = findKeyword(raw, cfg.webaeIntentKeywords, DEFAULT_WEBAE_KEYWORDS);
        if (keyword != null) {
            decision.owner = Owner.WEBAE;
            decision.textForHandler = raw;
            decision.reason = "webae_keyword:" + keyword;
            return decision;
        }

        decision.owner = Owner.ASTRBOT;
        decision.textForHandler = raw;
        decision.reason = "default_astrbot";
        return decision;
    }

    public static boolean isWebaeOwned(QqBotConfig cfg, String rawText) {
        return classify(cfg, rawText).owner == Owner.WEBAE;
    }

    private static PrefixHit matchLeadingToken(String raw, List<String> configured, List<String> defaults,
        boolean allowCompact) {
        List<String> tokens = effectiveTokens(configured, defaults);
        String lowerRaw = raw.toLowerCase(Locale.ROOT);
        PrefixHit best = new PrefixHit();
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            String lowerToken = token.toLowerCase(Locale.ROOT);
            if (!startsWithToken(raw, lowerRaw, token, lowerToken, allowCompact)) continue;
            if (token.length() < best.token.length()) continue;
            PrefixHit hit = new PrefixHit();
            hit.matched = true;
            hit.token = token;
            hit.remainder = stripLeadingToken(raw, token.length()).trim();
            best = hit;
        }
        return best;
    }

    private static boolean startsWithToken(String raw, String lowerRaw, String token, String lowerToken,
        boolean allowCompact) {
        if (raw.length() < token.length()) return false;
        if (!lowerRaw.startsWith(lowerToken)) return false;
        if (raw.length() == token.length()) return true;
        char next = raw.charAt(token.length());
        return Character.isWhitespace(next) || next == ':'
            || next == '：'
            || next == '/'
            || next == '-'
            || next == '|'
            || (allowCompact && next > 0x7f);
    }

    private static String stripLeadingToken(String raw, int tokenLength) {
        if (raw.length() <= tokenLength) return "";
        String rest = raw.substring(tokenLength)
            .trim();
        if (rest.startsWith(":") || rest.startsWith("：") || rest.startsWith("-") || rest.startsWith("|")) {
            return rest.substring(1)
                .trim();
        }
        return rest;
    }

    private static String findKeyword(String raw, List<String> configured, List<String> defaults) {
        String lower = URL_PATTERN.matcher(raw)
            .replaceAll(" ")
            .toLowerCase(Locale.ROOT);
        for (String keyword : effectiveTokens(configured, defaults)) {
            if (keyword.isEmpty()) continue;
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) return keyword;
        }
        return null;
    }

    private static boolean isWebaeCommandVerb(String verb) {
        if (verb == null || verb.isEmpty()) return false;
        for (String option : WEBAE_COMMAND_VERBS) {
            if (option.equals(verb)) return true;
        }
        return false;
    }

    private static List<String> effectiveTokens(List<String> configured, List<String> defaults) {
        if (configured == null || configured.isEmpty()) return defaults;
        return configured;
    }

    private static String stripCommandPrefix(String text, String prefix) {
        String p = safe(prefix);
        return !p.isEmpty() && text.startsWith(p) ? text.substring(p.length())
            .trim() : text;
    }

    private static String firstWord(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Decision {

        public Owner owner = Owner.WEBAE;
        public String rawText = "";
        public String textForHandler = "";
        public String reason = "";
    }

    private static final class PrefixHit {

        boolean matched;
        String token = "";
        String remainder = "";
    }
}
