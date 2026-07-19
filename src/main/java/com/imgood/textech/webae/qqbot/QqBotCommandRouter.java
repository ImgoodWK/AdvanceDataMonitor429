package com.imgood.textech.webae.qqbot;

import java.util.Locale;

/** Pure deterministic command parser/formatter. AI work is delegated to {@link QqBotService}. */
public final class QqBotCommandRouter {

    private QqBotCommandRouter() {}

    public static RouteResult route(QqBotConfig cfg, QqBotSnapshot snapshot, String rawText, boolean admin) {
        String raw = safe(rawText).trim();
        String commandText = stripPrefix(raw, cfg.commandPrefix);
        String lower = commandText.toLowerCase(Locale.ROOT);
        String verb = firstWord(lower);
        String argument = remainder(commandText);

        if (is(verb, "help", "menu", "commands", "帮助", "菜单", "功能", "命令")) {
            return RouteResult.reply("help", help(cfg, admin));
        }
        if (is(verb, "ping", "在吗", "测试")) {
            return RouteResult.reply("ping", "Pong · QQ 网关与 TeXTech 服务正常响应");
        }
        if (is(verb, "status", "server", "report", "状态", "服务器", "概况", "播报")) {
            return cfg.statusCommandEnabled ? RouteResult.reply("status", status(snapshot, cfg, true))
                : RouteResult.disabled("status");
        }
        if (is(verb, "players", "online", "人数", "在线", "在线人数")) {
            return cfg.playersCommandEnabled ? RouteResult.reply("players", players(snapshot, false))
                : RouteResult.disabled("players");
        }
        if (is(verb, "list", "who", "名单", "谁在线", "玩家列表")) {
            return cfg.playerListCommandEnabled ? RouteResult.reply("player_list", players(snapshot, true))
                : RouteResult.disabled("player_list");
        }
        if (is(verb, "tps", "mspt", "性能", "延迟")) {
            return cfg.tpsCommandEnabled ? RouteResult.reply("tps", performance(snapshot))
                : RouteResult.disabled("tps");
        }
        if (is(verb, "memory", "mem", "内存", "ram")) {
            return cfg.memoryCommandEnabled ? RouteResult.reply("memory", memory(snapshot))
                : RouteResult.disabled("memory");
        }
        if (is(verb, "uptime", "运行时间", "开服时间")) {
            return cfg.uptimeCommandEnabled
                ? RouteResult.reply("uptime", "服务器已连续运行 " + duration(snapshot.uptimeSeconds))
                : RouteResult.disabled("uptime");
        }
        if (is(verb, "about", "version", "关于", "版本")) {
            return cfg.aboutCommandEnabled
                ? RouteResult.reply("about", cfg.botName + " · TeXTech / AdvanceDataMonitor WebAE QQ 群机器人 · 只读状态查询 + 共享 AI 对话")
                : RouteResult.disabled("about");
        }
        if (is(verb, "reset", "forget", "clear", "重置对话", "忘记对话", "清空对话")) {
            return RouteResult.clear("conversation_reset", "已清空你在当前会话中的 AI 上下文。");
        }
        if (is(verb, "ai", "ask", "chat", "问", "对话")) {
            if (!cfg.aiEnabled) return RouteResult.disabled("ai");
            if (argument.isEmpty()) return RouteResult.reply("ai_usage", "用法：" + prefix(cfg) + "ai <问题>");
            return RouteResult.ai("ai", argument);
        }
        if (admin && is(verb, "botstatus", "机器人状态")) {
            return RouteResult.reply("bot_status", "机器人命令路由正常；连接与队列详情请在 WebAE 管理控制台查看。");
        }

        if (cfg.aiEnabled && cfg.aiAutoReply) return RouteResult.ai("ai_auto", raw);
        if (cfg.replyUnknownWithHelp) return RouteResult.reply("unknown", "未识别该命令。发送 “" + prefix(cfg) + "help” 查看功能。");
        return RouteResult.ignore("unknown");
    }

    public static String status(QqBotSnapshot snapshot, QqBotConfig cfg, boolean detailed) {
        StringBuilder text = new StringBuilder();
        if (!snapshot.motd.isEmpty()) text.append(snapshot.motd).append('\n');
        text.append("在线：").append(snapshot.onlinePlayers).append('/');
        if (snapshot.maxPlayers > 0) text.append(snapshot.maxPlayers);
        else text.append('?');
        text.append(" · TPS ").append(one(snapshot.tps)).append(" · MSPT ").append(one(snapshot.mspt));
        text.append('\n').append("运行：").append(duration(snapshot.uptimeSeconds));
        if (detailed && cfg.scheduledReportIncludeMemory) {
            text.append(" · 内存 ").append(snapshot.usedMemoryMb).append('/').append(snapshot.maxMemoryMb).append(" MiB");
        }
        if (detailed && cfg.scheduledReportIncludePlayers && !snapshot.playerNames.isEmpty()) {
            text.append('\n').append("玩家：").append(joinNames(snapshot, 20));
        }
        return text.toString();
    }

    private static String help(QqBotConfig cfg, boolean admin) {
        String p = prefix(cfg);
        StringBuilder text = new StringBuilder("可用功能：");
        if (cfg.statusCommandEnabled) text.append('\n').append(p).append("status - 服务器概况");
        if (cfg.playersCommandEnabled) text.append('\n').append(p).append("players - 在线人数");
        if (cfg.playerListCommandEnabled) text.append('\n').append(p).append("list - 在线名单");
        if (cfg.tpsCommandEnabled) text.append('\n').append(p).append("tps - TPS/MSPT");
        if (cfg.memoryCommandEnabled) text.append('\n').append(p).append("memory - JVM 内存");
        if (cfg.uptimeCommandEnabled) text.append('\n').append(p).append("uptime - 运行时间");
        if (cfg.aiEnabled) {
            text.append('\n').append(p).append("ai <问题> - AI 对话");
            text.append('\n').append(p).append("reset - 清空个人会话");
        }
        text.append('\n').append(p).append("ping - 连通性测试");
        if (admin) text.append('\n').append(p).append("botstatus - 机器人管理提示");
        return text.toString();
    }

    private static String players(QqBotSnapshot snapshot, boolean names) {
        String result = "当前在线 " + snapshot.onlinePlayers + (snapshot.maxPlayers > 0 ? "/" + snapshot.maxPlayers : "") + " 人";
        if (names && !snapshot.playerNames.isEmpty()) result += "\n" + joinNames(snapshot, 30);
        return result;
    }

    private static String performance(QqBotSnapshot snapshot) {
        String level = snapshot.tps >= 19.0D ? "流畅" : snapshot.tps >= 17.0D ? "轻微繁忙"
            : snapshot.tps >= 14.0D ? "负载较高" : "严重卡顿";
        return "TPS " + one(snapshot.tps) + " · MSPT " + one(snapshot.mspt) + " ms · " + level;
    }

    private static String memory(QqBotSnapshot snapshot) {
        long percent = snapshot.maxMemoryMb <= 0L ? 0L : snapshot.usedMemoryMb * 100L / snapshot.maxMemoryMb;
        return "JVM 内存 " + snapshot.usedMemoryMb + "/" + snapshot.maxMemoryMb + " MiB（" + percent + "%）";
    }

    private static String joinNames(QqBotSnapshot snapshot, int max) {
        StringBuilder text = new StringBuilder();
        int limit = Math.min(max, snapshot.playerNames.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) text.append("、");
            text.append(snapshot.playerNames.get(i));
        }
        if (snapshot.playerNames.size() > limit) text.append(" 等 ").append(snapshot.playerNames.size()).append(" 人");
        return text.toString();
    }

    private static String duration(long seconds) {
        long days = seconds / 86400L;
        long hours = seconds % 86400L / 3600L;
        long minutes = seconds % 3600L / 60L;
        StringBuilder text = new StringBuilder();
        if (days > 0L) text.append(days).append("天");
        if (hours > 0L) text.append(hours).append("小时");
        if (minutes > 0L || text.length() == 0) text.append(minutes).append("分钟");
        return text.toString();
    }

    private static String stripPrefix(String text, String prefix) {
        String p = safe(prefix);
        return !p.isEmpty() && text.startsWith(p) ? text.substring(p.length()).trim() : text;
    }

    private static String prefix(QqBotConfig cfg) {
        return safe(cfg.commandPrefix).isEmpty() ? "" : cfg.commandPrefix;
    }

    private static String firstWord(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }

    private static String remainder(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? "" : value.substring(space + 1).trim();
    }

    private static boolean is(String value, String... options) {
        for (String option : options) if (option.equals(value)) return true;
        return false;
    }

    private static String one(double value) {
        return String.valueOf(Math.round(value * 10.0D) / 10.0D);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class RouteResult {

        public String kind;
        public String command;
        public String reply;
        public String aiText;
        public boolean clearConversation;

        static RouteResult reply(String command, String reply) {
            RouteResult result = base("reply", command);
            result.reply = reply;
            return result;
        }

        static RouteResult ai(String command, String text) {
            RouteResult result = base("ai", command);
            result.aiText = text;
            return result;
        }

        static RouteResult clear(String command, String reply) {
            RouteResult result = reply(command, reply);
            result.clearConversation = true;
            return result;
        }

        static RouteResult disabled(String command) {
            return reply(command, "该功能已被服务器管理员关闭。");
        }

        static RouteResult ignore(String command) {
            return base("ignore", command);
        }

        private static RouteResult base(String kind, String command) {
            RouteResult result = new RouteResult();
            result.kind = kind;
            result.command = command;
            return result;
        }
    }
}
