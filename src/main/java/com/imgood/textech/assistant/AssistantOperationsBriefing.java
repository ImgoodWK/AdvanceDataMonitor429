package com.imgood.textech.assistant;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formats the read-only operations briefing returned by {@link AssistantServerServices}.
 *
 * <p>
 * The briefing deliberately receives already-collected summaries instead of probing game
 * state itself. That keeps collection on the server-service boundary, makes the presentation
 * deterministic, and gives every section an independent failure boundary. The output is also
 * bounded before it is placed into an assistant response packet.
 * </p>
 */
public final class AssistantOperationsBriefing {

    public static final int MAX_SECTION_CHARS = 560;
    public static final int MAX_TOTAL_CHARS = 4096;
    /** Keep UTF-8 text below {@code PacketAssistantResponse}'s 4096-byte message field. */
    public static final int MAX_TOTAL_UTF8_BYTES = 4000;
    private static final int MAX_SECTION_UTF8_BYTES = 480;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final Pattern NAMED_SECRET = Pattern.compile(
        "(?i)(\\b(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|client[_ -]?secret|token|secret|password)\\b\\s*\\\"?\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern OPENAI_STYLE_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");

    private AssistantOperationsBriefing() {}

    public enum Status {
        HEALTHY,
        ATTENTION,
        UNAVAILABLE
    }

    /** A single, independently collected briefing section. */
    public static final class Section {

        public final String key;
        public final String title;
        public final String summary;
        public final Status status;

        private Section(String key, String title, String summary, Status status) {
            this.key = key == null ? "" : key.trim();
            this.title = title == null ? "" : title.trim();
            this.summary = sanitize(summary, MAX_SECTION_CHARS);
            this.status = status == null ? Status.UNAVAILABLE : status;
        }
    }

    /**
     * Creates a section and evaluates only explicit, already-present status markers. It never
     * derives values or guesses about an unavailable integration.
     */
    public static Section section(String key, String title, String summary) {
        String safeSummary = sanitize(summary, MAX_SECTION_CHARS);
        if (safeSummary.isEmpty()) {
            return new Section(key, title, safeSummary, Status.UNAVAILABLE);
        }
        // Network health has an authoritative status in its shared DTO. Requiring the explicit
        // overload prevents presentation text (including localized issue wording) from becoming
        // a second health evaluator.
        if ("networkHealth".equals(key)) {
            return new Section(key, title, safeSummary, Status.UNAVAILABLE);
        }
        return new Section(key, title, safeSummary, classify(key, safeSummary));
    }

    /** Creates a section with an authoritative status supplied by the data source. */
    public static Section section(String key, String title, String summary, Status status) {
        return new Section(key, title, summary, status);
    }

    /** Map a provider DTO status to the briefing's presentation status without inspecting text or evidence. */
    public static Status fromNetworkHealthStatus(String status) {
        if ("healthy".equalsIgnoreCase(status)) {
            return Status.HEALTHY;
        }
        if ("degraded".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
            return Status.ATTENTION;
        }
        return Status.UNAVAILABLE;
    }

    /** Creates a section whose source could not be queried safely. */
    public static Section unavailable(String key, String title, String reason) {
        String safeReason = sanitize(reason, MAX_SECTION_CHARS);
        if (safeReason.isEmpty()) {
            safeReason = "Unavailable.";
        }
        return new Section(key, title, safeReason, Status.UNAVAILABLE);
    }

    /** Formats a bounded bilingual report from the supplied section summaries. */
    public static String format(String locale, List<Section> inputSections) {
        boolean chinese = isChinese(locale);
        List<Section> sections = copySections(inputSections);
        Status overall = overallStatus(sections);
        StringBuilder builder = new StringBuilder();
        builder.append(chinese ? "基地运维简报（只读）" : "Operations Briefing (read-only)");
        builder.append("\n")
            .append(chinese ? "总体状态：" : "Overall: ")
            .append(statusLabel(overall, chinese));

        for (Section section : sections) {
            builder.append("\n\n[")
                .append(statusLabel(section.status, chinese))
                .append("] ")
                .append(section.title.isEmpty() ? section.key : section.title)
                .append("\n")
                .append(
                    section.summary.isEmpty() ? (chinese ? "未返回可用数据。" : "No usable data was returned.")
                        : section.summary);
        }

        appendRecommendations(builder, sections, chinese);
        return truncateUtf8(builder.toString(), MAX_TOTAL_UTF8_BYTES);
    }

    private static List<Section> copySections(List<Section> inputSections) {
        if (inputSections == null || inputSections.isEmpty()) {
            return Collections.emptyList();
        }
        List<Section> result = new ArrayList<Section>();
        for (Section section : inputSections) {
            if (section != null) {
                result.add(section);
            }
            // The server currently emits seven sections. Keep a defensive cap if this formatter is
            // later reused by another entry point.
            if (result.size() >= 8) {
                break;
            }
        }
        return result;
    }

    private static Status overallStatus(List<Section> sections) {
        Status result = Status.HEALTHY;
        for (Section section : sections) {
            if (section.status == Status.UNAVAILABLE) {
                return Status.UNAVAILABLE;
            }
            if (section.status == Status.ATTENTION) {
                result = Status.ATTENTION;
            }
        }
        return result;
    }

    private static Status classify(String key, String summary) {
        String normalized = normalized(summary);
        if (isUnavailable(normalized)) {
            return Status.UNAVAILABLE;
        }
        if ("bytes".equals(key) && isNearlyFull(normalized)) {
            return Status.ATTENTION;
        }
        if ("jobs".equals(key) && hasPendingJobs(normalized)) {
            return Status.ATTENTION;
        }
        if ("planner".equals(key) && hasPlannerTodos(normalized)) {
            return Status.ATTENTION;
        }
        return Status.HEALTHY;
    }

    private static boolean isUnavailable(String normalized) {
        return containsAny(
            normalized,
            "query failed",
            "unable to collect",
            "no nearby",
            "no recorded or nearby",
            "did not find a compatible",
            "advance network link: 0",
            "player or world unavailable",
            "you don't have an advanced planner",
            "\u67e5\u8be2\u5931\u8d25",
            "\u65e0\u6cd5\u6536\u96c6",
            "\u9644\u8fd1\u6ca1\u6709",
            "\u672a\u627e\u5230\u5df2\u8bb0\u5f55\u6216\u9644\u8fd1",
            "\u672a\u627e\u5230\u517c\u5bb9",
            "\u73a9\u5bb6\u6216\u4e16\u754c\u4e0d\u53ef\u7528",
            "\u6ca1\u6709\u9ad8\u7ea7\u8ba1\u5212\u5668");
    }

    private static boolean isNearlyFull(String normalized) {
        return containsAny(normalized, "nearly full", "\u63a5\u8fd1\u6ee1\u8f7d", "\u63a5\u8fd1\u6ee1");
    }

    private static boolean hasPendingJobs(String normalized) {
        if (containsAny(
            normalized,
            "no server-side ae2 crafting calculation is pending",
            "\u6ca1\u6709\u5f85\u5904\u7406")) {
            return false;
        }
        return normalized.contains("pending ae2 crafting calculations:")
            || normalized.contains("\u5f85\u5904\u7406\u7684ae2");
    }

    private static boolean hasPlannerTodos(String normalized) {
        return normalized.contains("[todo]") || normalized.contains("[\u5f85\u529e]");
    }

    private static void appendRecommendations(StringBuilder builder, List<Section> sections, boolean chinese) {
        boolean unavailable = false;
        boolean nearlyFull = false;
        boolean pendingJobs = false;
        boolean plannerTodos = false;
        for (Section section : sections) {
            String summary = normalized(section.summary);
            unavailable |= section.status == Status.UNAVAILABLE;
            nearlyFull |= "bytes".equals(section.key) && isNearlyFull(summary);
            pendingJobs |= "jobs".equals(section.key) && hasPendingJobs(summary);
            plannerTodos |= "planner".equals(section.key) && hasPlannerTodos(summary);
        }
        builder.append("\n\n")
            .append(chinese ? "建议：" : "Suggestions:");
        if (unavailable) {
            appendRecommendation(
                builder,
                chinese ? "检查不可用分段所需的 ADM Link、无线服务或高级计划器，然后重新查询。"
                    : "Check the ADM Link, wireless service, or Advanced Planner required by unavailable sections, then query again.");
        }
        if (nearlyFull) {
            appendRecommendation(
                builder,
                chinese ? "AE2 字节接近满载；扩容或清理不再需要的存储内容。"
                    : "AE2 byte capacity is nearly full; add storage or clear unneeded contents.");
        }
        if (pendingJobs) {
            appendRecommendation(
                builder,
                chinese ? "合成计算正在排队；提交大型批量订单前先检查现有任务。"
                    : "Crafting calculations are pending; inspect existing jobs before adding a large batch.");
        }
        if (plannerTodos) {
            appendRecommendation(
                builder,
                chinese ? "高级计划器中仍有待办；按优先级复查后再继续自动化操作。"
                    : "The Advanced Planner has open todos; review their priority before continuing automation work.");
        }
        if (!unavailable && !nearlyFull && !pendingJobs && !plannerTodos) {
            appendRecommendation(
                builder,
                chinese ? "已收集的数据中没有明确的紧急动作。" : "No urgent action is indicated by the collected data.");
        }
    }

    private static void appendRecommendation(StringBuilder builder, String text) {
        builder.append("\n- ")
            .append(text);
    }

    private static String statusLabel(Status status, boolean chinese) {
        if (status == Status.UNAVAILABLE) {
            return chinese ? "不可用" : "UNAVAILABLE";
        }
        if (status == Status.ATTENTION) {
            return chinese ? "注意" : "ATTENTION";
        }
        return chinese ? "正常" : "HEALTHY";
    }

    private static String sanitize(String value, int limit) {
        if (value == null) {
            return "";
        }
        String result = value.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace((char) 0, ' ')
            .trim();
        result = NAMED_SECRET.matcher(result)
            .replaceAll("$1<redacted>");
        result = OPENAI_STYLE_KEY.matcher(result)
            .replaceAll("<redacted>");
        return truncateUtf8(truncate(result, limit), Math.min(MAX_SECTION_UTF8_BYTES, Math.max(0, limit)));
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value == null ? "" : value;
        }
        if (limit <= 3) {
            return value.substring(0, Math.max(0, limit));
        }
        return value.substring(0, limit - 3) + "...";
    }

    /** Truncates at a Unicode code-point boundary so packet UTF-8 stays valid and bounded. */
    private static String truncateUtf8(String value, int maxBytes) {
        if (value == null || maxBytes <= 0) {
            return "";
        }
        if (value.isEmpty()) {
            return value;
        }
        if (maxBytes <= 3) {
            return "...".substring(0, maxBytes);
        }
        if (value.getBytes(UTF8).length <= maxBytes) {
            return value;
        }
        int textBudget = Math.max(0, maxBytes - 3);
        StringBuilder result = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String codePointText = new String(Character.toChars(codePoint));
            int codePointBytes = codePointText.getBytes(UTF8).length;
            if (used + codePointBytes > textBudget) {
                break;
            }
            result.append(codePointText);
            used += codePointBytes;
            offset += Character.charCount(codePoint);
        }
        return result.append("...")
            .toString();
    }

    private static boolean containsAny(String text, String... markers) {
        if (text == null || markers == null) {
            return false;
        }
        for (String marker : markers) {
            if (marker != null && !marker.isEmpty() && text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean isChinese(String locale) {
        return locale == null || locale.trim()
            .isEmpty()
            || locale.trim()
                .toLowerCase(Locale.ROOT)
                .startsWith("zh");
    }
}
