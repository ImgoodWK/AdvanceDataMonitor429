package com.imgood.advancedatamonitor.assistant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.assistant.ai.ChatRequestOptions;
import com.imgood.advancedatamonitor.assistant.ai.ChatResponse;
import com.imgood.advancedatamonitor.assistant.ai.DeepSeekChatClient;
import com.imgood.advancedatamonitor.assistant.ai.DeepSeekChatClient.ChatMessage;

public class AssistantAiIntentService {

    private final AssistantAiIntentJsonParser parser;

    public AssistantAiIntentService() {
        this(new AssistantAiIntentJsonParser());
    }

    public AssistantAiIntentService(AssistantAiIntentJsonParser parser) {
        this.parser = parser == null ? new AssistantAiIntentJsonParser() : parser;
    }

    public boolean isAvailable() {
        return Config.aiNetworkEnabled && !Config.getAiApiKey()
            .isEmpty();
    }

    public AssistantIntentPlan parse(String prompt, String locale) throws IOException {
        return parse(prompt, locale, null);
    }

    public AssistantIntentPlan parse(String prompt, String locale, String featureContextPrefix) throws IOException {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(new ChatMessage("system", buildSystemPrompt(locale)));
        String pendingContext = buildPendingSessionContext();
        if (!pendingContext.isEmpty()) {
            messages.add(new ChatMessage("system", pendingContext));
        }
        String userMessage = prompt == null ? "" : prompt;
        if (featureContextPrefix != null && !featureContextPrefix.isEmpty()) {
            userMessage = featureContextPrefix + userMessage;
        }
        messages.add(new ChatMessage("user", userMessage));
        DeepSeekChatClient client = new DeepSeekChatClient();
        ChatResponse response = client.chat(messages, new ChatRequestOptions(false, "off", false), null);
        return this.parser.parse(response.content);
    }

    private String buildPendingSessionContext() {
        AssistantSession session = AssistantSession.client();
        AssistantSessionKind kind = session.getKind();
        if (kind == AssistantSessionKind.ORDER_CANDIDATES || kind == AssistantSessionKind.RECIPE_CANDIDATES) {
            List<CraftingCandidate> candidates = session.getPendingCandidates();
            if (candidates.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("There is a pending assistant candidate selection session. ");
            builder.append("kind=")
                .append(kind.name())
                .append(". ");
            builder.append("originalRequest=\"")
                .append(safeForPrompt(session.getLastUserText()))
                .append("\". ");
            builder.append("Candidates, use one-based optionNumber: ");
            int count = Math.min(20, candidates.size());
            for (int i = 0; i < count; i++) {
                CraftingCandidate candidate = candidates.get(i);
                if (candidate == null) {
                    continue;
                }
                if (i > 0) {
                    builder.append("; ");
                }
                builder.append("#")
                    .append(i + 1)
                    .append(" displayName=\"")
                    .append(safeForPrompt(candidate.displayName))
                    .append("\"")
                    .append(", registryName=\"")
                    .append(safeForPrompt(candidate.registryName))
                    .append("\"")
                    .append(", amount=")
                    .append(candidate.amount);
            }
            builder.append(
                ". For candidate number, confirmation, or a short yes/ok/confirm phrase, return CONFIRM_OPTION. ");
            builder.append("If exactly one candidate and the user confirms without a number, set optionNumber=1. ");
            builder.append(
                "If the user only changes quantity, such as 500, 500\u4e2a, or \u6539\u6210500\u4e2a, return CONFIRM_OPTION with optionNumber=1 when exactly one candidate and amount equal to the new quantity. ");
            builder.append(
                "If the user changes to a different target, return ORDER_ITEM. If the user cancels, return CANCEL. ");
            builder.append(
                "Do not return CHAT for candidate selection, confirmation, quantity override, retargeting, or cancellation.");
            return builder.toString();
        }
        if (kind == AssistantSessionKind.WITHDRAW_CANDIDATES || kind == AssistantSessionKind.WITHDRAW_PARTIAL_CONFIRM) {
            List<CraftingCandidate> candidates = session.getPendingCandidates();
            if (candidates.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("There is a pending assistant storage withdraw candidate session. ");
            builder.append("kind=")
                .append(kind.name())
                .append(". ");
            builder.append("originalRequest=\"")
                .append(safeForPrompt(session.getLastUserText()))
                .append("\". ");
            if (kind == AssistantSessionKind.WITHDRAW_PARTIAL_CONFIRM) {
                builder.append("requestedAmount=")
                    .append(session.getPendingRequestedAmount())
                    .append(". ");
                builder.append("fitAmount=")
                    .append(session.getPendingPartialAmount())
                    .append(". ");
                builder.append("For confirmation, return CONFIRM_OPTION with amount equal to fitAmount. ");
            } else {
                builder.append("Candidates, use one-based optionNumber: ");
                int count = Math.min(20, candidates.size());
                for (int i = 0; i < count; i++) {
                    CraftingCandidate candidate = candidates.get(i);
                    if (candidate == null) {
                        continue;
                    }
                    if (i > 0) {
                        builder.append("; ");
                    }
                    builder.append("#")
                        .append(i + 1)
                        .append(" displayName=\"")
                        .append(safeForPrompt(candidate.displayName))
                        .append("\"")
                        .append(", registryName=\"")
                        .append(safeForPrompt(candidate.registryName))
                        .append("\"")
                        .append(", storedAmount=")
                        .append(candidate.amount);
                }
                builder.append(
                    ". For candidate number, confirmation, or a short yes/ok/confirm phrase, return CONFIRM_OPTION. ");
                builder.append("If exactly one candidate and the user confirms without a number, set optionNumber=1. ");
                builder.append(
                    "If the user only changes quantity, return CONFIRM_OPTION with optionNumber=1 when exactly one candidate and amount equal to the new quantity. ");
            }
            builder.append(
                "If the user changes to a different target, return WITHDRAW_ITEM. If the user cancels, return CANCEL. ");
            builder.append(
                "Do not return CHAT for withdraw candidate selection, confirmation, quantity override, retargeting, or cancellation.");
            return builder.toString();
        }
        if (kind == AssistantSessionKind.WITHDRAW_BATCH_CANDIDATES) {
            List<AssistantOrderLine> lines = session.getPendingOrderLines();
            if (lines.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("There is a pending assistant batch withdraw confirmation session. ");
            builder.append("kind=")
                .append(kind.name())
                .append(". ");
            builder.append("originalRequest=\"")
                .append(safeForPrompt(session.getLastUserText()))
                .append("\". ");
            builder.append("Pending withdraw lines: ");
            int count = Math.min(20, lines.size());
            for (int i = 0; i < count; i++) {
                AssistantOrderLine line = lines.get(i);
                if (line == null) {
                    continue;
                }
                CraftingCandidate candidate = line.selectedOrFirstCandidate();
                if (i > 0) {
                    builder.append("; ");
                }
                builder.append("line ")
                    .append(line.lineIndex)
                    .append(" target=\"")
                    .append(safeForPrompt(line.target))
                    .append("\"")
                    .append(", amount=")
                    .append(line.amount);
                if (candidate != null) {
                    builder.append(", candidate=\"")
                        .append(safeForPrompt(candidate.displayName))
                        .append("\"")
                        .append(", storedAmount=")
                        .append(candidate.amount);
                }
            }
            builder.append(". For confirmation, return CONFIRM_OPTION. For cancellation, return CANCEL. ");
            builder.append(
                "If the user adds another withdraw line, return WITHDRAW_ITEM. Do not return CHAT for batch withdraw confirmation, cancellation, or adding another line.");
            return builder.toString();
        }
        if (kind == AssistantSessionKind.TELEPORT_CANDIDATES) {
            List<TeleportDestination> destinations = session.getPendingTeleportDestinations();
            if (destinations.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("There is a pending teleport destination selection session. ");
            builder.append("kind=")
                .append(kind.name())
                .append(". ");
            builder.append("originalRequest=\"")
                .append(safeForPrompt(session.getLastUserText()))
                .append("\". ");
            builder.append("Teleport destinations, use one-based optionNumber: ");
            int count = Math.min(20, destinations.size());
            for (int i = 0; i < count; i++) {
                TeleportDestination dest = destinations.get(i);
                if (dest == null) {
                    continue;
                }
                if (i > 0) {
                    builder.append("; ");
                }
                builder.append("#")
                    .append(i + 1)
                    .append(" name=\"")
                    .append(safeForPrompt(dest.name))
                    .append("\"")
                    .append(", dimension=\"")
                    .append(safeForPrompt(dest.dimensionName))
                    .append("\"")
                    .append(", x=")
                    .append(dest.x)
                    .append(", y=")
                    .append(dest.y)
                    .append(", z=")
                    .append(dest.z);
            }
            builder.append(
                ". For candidate number, confirmation, or a short yes/ok/confirm phrase, return CONFIRM_OPTION. ");
            builder.append("If exactly one candidate and the user confirms without a number, set optionNumber=1. ");
            builder.append(
                "If the user says a destination name rather than a number, return CONFIRM_OPTION with target set to the name. ");
            builder.append("If the user cancels, return CANCEL. ");
            builder.append("Do not return CHAT for teleport selection, confirmation, or cancellation.");
            return builder.toString();
        }
        if (kind == AssistantSessionKind.ORDER_BATCH_CANDIDATES) {
            List<AssistantOrderLine> lines = session.getPendingOrderLines();
            if (lines.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("There is a pending assistant batch order confirmation session. ");
            builder.append("kind=")
                .append(kind.name())
                .append(". ");
            builder.append("originalRequest=\"")
                .append(safeForPrompt(session.getLastUserText()))
                .append("\". ");
            builder.append("Pending order lines: ");
            int count = Math.min(20, lines.size());
            for (int i = 0; i < count; i++) {
                AssistantOrderLine line = lines.get(i);
                if (line == null) {
                    continue;
                }
                CraftingCandidate candidate = line.selectedOrFirstCandidate();
                if (i > 0) {
                    builder.append("; ");
                }
                builder.append("line ")
                    .append(line.lineIndex)
                    .append(" target=\"")
                    .append(safeForPrompt(line.target))
                    .append("\"")
                    .append(", amount=")
                    .append(line.amount);
                if (candidate != null) {
                    builder.append(", candidate=\"")
                        .append(safeForPrompt(candidate.displayName))
                        .append("\"");
                }
            }
            builder.append(". For confirmation, return CONFIRM_OPTION. For cancellation, return CANCEL. ");
            builder.append(
                "If the user adds another order line, return ORDER_ITEM. Do not return CHAT for batch confirmation, cancellation, or adding another order line.");
            return builder.toString();
        }
        return "";
    }

    private String safeForPrompt(String text) {
        if (text == null) {
            return "";
        }
        String safe = text.replace('\n', ' ')
            .replace('\r', ' ')
            .replace('"', ' ')
            .trim();
        if (safe.length() > 200) {
            return safe.substring(0, 200);
        }
        return safe;
    }

    private String buildSystemPrompt(String locale) {
        String effectiveLocale = locale == null || locale.trim()
            .isEmpty() ? "en_US" : locale.trim();
        boolean zh = effectiveLocale.toLowerCase()
            .startsWith("zh");
        StringBuilder sb = new StringBuilder();
        sb.append(
            zh ? "你将一个Minecraft GTNH助手的用户输入分类为可执行的结构化意图。只返回一个JSON对象，不要输出markdown、注释或代码块。"
                : "You classify a Minecraft GTNH assistant user prompt into executable structured intent. "
                    + "Return only one JSON object and no markdown, comments, prose, or code fences. ");
        sb.append(
            zh ? "使用此精确格式：{\"tasks\":[{\"type\":\"QUERY_RECIPE|QUERY_STORAGE|QUERY_BYTES|QUERY_POWER|QUERY_STEAM|QUERY_WEATHER|QUERY_TIME|QUERY_POSITION|QUERY_BIOME|QUERY_INVENTORY|QUERY_NETWORK|QUERY_JOBS|QUERY_ITEM_COUNT|TELEPORT|TELEPORT_LIST|ORDER_ITEM|WITHDRAW_ITEM|CONFIRM_OPTION|PLAN_ADD|PLAN_LIST|PLAN_COMPLETE|PLAN_DELETE|PLAN_MODIFY|CANCEL|CLARIFY|CHAT\",\"target\":\"...\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all|items|fluids\",\"confidence\":0.9}]}。"
                : "Use this exact schema: {\"tasks\":[{\"type\":\"QUERY_RECIPE|QUERY_STORAGE|QUERY_BYTES|QUERY_POWER|QUERY_STEAM|QUERY_WEATHER|QUERY_TIME|QUERY_POSITION|QUERY_BIOME|QUERY_INVENTORY|QUERY_NETWORK|QUERY_JOBS|QUERY_ITEM_COUNT|TELEPORT|TELEPORT_LIST|ORDER_ITEM|WITHDRAW_ITEM|CONFIRM_OPTION|PLAN_ADD|PLAN_LIST|PLAN_COMPLETE|PLAN_DELETE|PLAN_MODIFY|CANCEL|CLARIFY|CHAT\",\"target\":\"...\",\"amount\":1,\"optionNumber\":-1,\"storageScope\":\"all|items|fluids\",\"confidence\":0.9}]}. ");
        sb.append(
            zh ? "CHAT仅用于不涉及查询AE2、合成、取出、确认、取消或管理计划的普通对话。每个请求的动作一个任务，最多8个任务。"
                : "Choose CHAT only for ordinary conversation that does not query AE2, order crafting, withdraw from storage, confirm, cancel, or manage plans. "
                    + "Use one task per requested action, up to eight tasks. ");
        sb.append(
            zh ? "ORDER_ITEM表示提交AE2合成任务。WITHDRAW_ITEM表示从AE2存储取出物品到背包。ORDER_ITEM和WITHDRAW_ITEM必须包含非空target。"
                : "ORDER_ITEM means request AE2 crafting job. WITHDRAW_ITEM means withdraw items from AE2 storage to inventory. "
                    + "ORDER_ITEM and WITHDRAW_ITEM must include a non-empty target. ");
        sb.append(
            zh ? "QUERY_ITEM_COUNT表示查询AE2存储网络中物品或流体的实际库存数量（target可选填名称过滤，留空则列出所有）。"
                : "QUERY_ITEM_COUNT means query AE2 storage network for actual item/fluid quantities (target is optional name filter; leave empty to list all). ");
        sb.append(
            zh ? "QUERY_BYTES表示查询AE2存储网络的字节占用详细信息（物品/流体各占用/总量字节、百分比）和是否有无限存储元件。target留空。"
                : "QUERY_BYTES means query AE2 storage network byte usage details (item/fluid used/total bytes, percentages) and whether infinite storage cells exist. Leave target empty. ");
        sb.append(
            zh ? "TELEPORT表示传送到一个指定的传送点（target填写目的地名称）。TELEPORT_LIST表示列出所有可用的传送点（target留空）。"
                : "TELEPORT means teleport to a named destination (fill target with destination name). TELEPORT_LIST means list all available teleport destinations (leave target empty). ");
        sb.append("\n\n")
            .append(AssistantFeatureConfig.buildFeaturesInstruction(effectiveLocale));
        sb.append("\n")
            .append(
                zh ? "如果用户只说来/要/给而没有明确的合成vs存储上下文，返回CLARIFY而不是猜测。"
                    : "If the user only says come/want/get without clear craft-vs-storage context, return CLARIFY instead of guessing. ");
        sb.append(
            zh ? "如果有存储上下文词但没有合成上下文，优先使用WITHDRAW_ITEM。如果有合成上下文但没有存储上下文，优先使用ORDER_ITEM。"
                : "If storage context words appear without craft context, prefer WITHDRAW_ITEM. If craft context appears without storage context, prefer ORDER_ITEM. ");
        sb.append(zh ? "用户语言是" + effectiveLocale + "。" : "The user's locale is " + effectiveLocale + ".");
        return sb.toString();
    }
}
