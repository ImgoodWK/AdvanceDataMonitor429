package com.imgood.textech.webae.assistant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.assistant.AssistantAiIntentJsonParser;
import com.imgood.textech.assistant.AssistantAiIntentService;
import com.imgood.textech.assistant.AssistantIntent;
import com.imgood.textech.assistant.AssistantIntentPlan;
import com.imgood.textech.assistant.AssistantIntentService;
import com.imgood.textech.assistant.AssistantIntentTask;
import com.imgood.textech.assistant.AssistantIntentType;
import com.imgood.textech.assistant.AssistantOrderLine;
import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.assistant.CandidateQueryResult;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.assistant.TeleportDestination;
import com.imgood.textech.assistant.TeleportService;
import com.imgood.textech.assistant.WithdrawSubmitOutcome;
import com.imgood.textech.assistant.WithdrawSubmitOutcome.Kind;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.assistant.WebAiHttpClient.Message;
import com.imgood.textech.webae.assistant.WebAssistantPendingStore.PendingAction;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.context.WebAeOwnerContext;

/**
 * Web bridge for the in-game assistant contract: AI intent plan, local fallback,
 * server tools, and explicit confirmation for mutating actions.
 */
public final class WebAssistantService {

    private static final AssistantIntentService RULES = new AssistantIntentService();
    private static final AssistantAiIntentJsonParser AI_PARSER = new AssistantAiIntentJsonParser();
    private static final long SERVER_TIMEOUT_MS = 20_000L;
    private static final int MAX_PROMPT_LENGTH = 4_000;
    private static final int MAX_CLIENT_AI_PLAN_LENGTH = 64_000;
    private static final int MAX_CLIENT_AI_REPLY_LENGTH = 32_000;

    private WebAssistantService() {}

    public static WebAssistantResult handleQuery(WebAuthSession auth, WebAssistantRequest request) {
        WebAssistantResult result = new WebAssistantResult();
        if (auth == null) return failure("auth_required", "Authentication is required.");
        String text = request == null ? "" : safe(request.text).trim();
        String locale = normalizeLocale(request == null ? null : request.locale);
        if (text.isEmpty()) return failure("empty_query", localized(locale, "请输入内容。", "Enter a message."));
        if (text.length() > MAX_PROMPT_LENGTH) {
            return failure("query_too_long", localized(locale, "输入内容过长。", "The message is too long."));
        }
        String clientAiPlan = request == null ? "" : safe(request.clientAiPlan).trim();
        String clientAiReply = request == null ? "" : safe(request.clientAiReply).trim();
        if (clientAiPlan.length() > MAX_CLIENT_AI_PLAN_LENGTH || clientAiReply.length() > MAX_CLIENT_AI_REPLY_LENGTH) {
            return failure(
                "client_ai_output_too_long",
                localized(locale, "浏览器 AI 返回内容过长。", "The browser AI response is too long."));
        }
        String aiSource;
        try {
            aiSource = resolveAiSource(request == null ? null : request.aiSource, clientAiPlan, clientAiReply);
        } catch (IllegalStateException e) {
            return failure("ai_source_disabled", e.getMessage());
        } catch (IllegalArgumentException e) {
            return failure("invalid_ai_source", e.getMessage());
        }
        if (!WebAssistantRateLimiter.tryAcquire(auth.actorUuid)) {
            result.success = false;
            result.message = localized(locale, "请求过于频繁，请稍后再试。", "Rate limited. Try again shortly.");
            result.code = "rate_limited";
            result.cooldownMs = WebAssistantRateLimiter.remainingCooldownMs(auth.actorUuid);
            return result;
        }

        ParsedPlan parsed = parsePlan(text, locale, auth.isGuest(), clientAiPlan, aiSource);
        result.source = parsed.source;
        result.aiUsed = parsed.aiUsed;
        result.fallbackReason = parsed.fallbackReason;

        if ((parsed.plan != null && parsed.plan.isChatOnly()) || (parsed.plan == null && parsed.fallbackIntent != null
            && parsed.fallbackIntent.type == AssistantIntentType.CHAT)) {
            return chat(auth, request, text, locale, result, aiSource);
        }

        final AssistantIntentPlan plan = parsed.plan;
        final AssistantIntent fallbackIntent = parsed.fallbackIntent;
        try {
            WebAssistantResult executed = callServer(new ServerCall<WebAssistantResult>() {

                @Override
                public WebAssistantResult run() {
                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(auth.ownerUuid);
                    if (player == null) {
                        return failure(
                            "no_player",
                            localized(locale, "玩家上下文不可用，无法执行游戏内工具。", "Player context is unavailable for game tools."));
                    }
                    return executePlan(auth, player, text, locale, plan, fallbackIntent);
                }
            });
            executed.source = result.source;
            executed.aiUsed = result.aiUsed;
            executed.fallbackReason = result.fallbackReason;
            return executed;
        } catch (Exception e) {
            return failure("query_failed", localized(locale, "助手执行失败。", "Assistant execution failed."));
        }
    }

    private static String resolveAiSource(String requested, String clientAiPlan, String clientAiReply) {
        String value = safe(requested).trim()
            .toLowerCase();
        if (value.isEmpty()) {
            if (!safe(clientAiPlan).isEmpty() || !safe(clientAiReply).isEmpty()) {
                return WebAiConfigStore.normalizeAiSource(WebAiConfigStore.SOURCE_BROWSER);
            }
            if (WebAiConfigStore.isServerKeyEnabled()) {
                return WebAiConfigStore.SOURCE_SERVER;
            }
            if (WebAiConfigStore.isBrowserKeyEnabled()) {
                return WebAiConfigStore.SOURCE_BROWSER;
            }
            return WebAiConfigStore.SOURCE_SERVER;
        }
        return WebAiConfigStore.normalizeAiSource(value);
    }

    public static WebAssistantResult confirm(WebAuthSession auth, WebAssistantActionRequest request) {
        if (auth == null) return failure("auth_required", "Authentication is required.");
        if (auth.isGuest()) return failure("guest_readonly", "Guest tokens cannot execute assistant actions.");
        final String token = request == null ? "" : safe(request.actionToken).trim();
        final int optionNumber = request == null ? 1 : Math.max(1, request.optionNumber);
        final long amount = request == null ? 0L : request.amount;
        if (token.isEmpty()) return failure("action_token_required", "Assistant action token is required.");
        try {
            return callServer(new ServerCall<WebAssistantResult>() {

                @Override
                public WebAssistantResult run() {
                    PendingAction pending = WebAssistantPendingStore.take(token, auth.actorUuid, auth.ownerUuid);
                    if (pending == null) {
                        return failure(
                            "action_expired",
                            "Assistant action expired or does not belong to this session.");
                    }
                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(auth.ownerUuid);
                    if (player == null) return failure("no_player", "Player context unavailable.");
                    return executePending(auth, player, pending, optionNumber, amount);
                }
            });
        } catch (Exception e) {
            return failure("action_failed", "Assistant action failed.");
        }
    }

    private static ParsedPlan parsePlan(String text, String locale, boolean guest, String clientAiPlan,
        String aiSource) {
        ParsedPlan parsed = new ParsedPlan();
        boolean browser = WebAiConfigStore.SOURCE_BROWSER.equals(aiSource);
        if (browser) {
            if (!safe(clientAiPlan).isEmpty()) {
                try {
                    AssistantIntentPlan plan = AI_PARSER.parse(clientAiPlan);
                    if (plan != null && !plan.isEmpty()) {
                        parsed.plan = plan;
                        parsed.source = "browser-ai";
                        parsed.aiUsed = true;
                        return parsed;
                    }
                    parsed.fallbackReason = "empty_browser_ai_plan";
                } catch (Exception e) {
                    parsed.fallbackReason = "browser_ai_parse_failed";
                }
            } else {
                parsed.fallbackReason = "browser_ai_not_configured";
            }
            parsed.fallbackIntent = RULES.parse(text);
            parsed.source = "rules";
            return parsed;
        }
        if (guest || !WebAiConfigStore.isServerKeyEnabled()
            || WebAiConfigStore.instance()
                .runtimes()
                .isEmpty()) {
            parsed.fallbackReason = guest ? "guest_rule_only" : "ai_not_configured";
            parsed.fallbackIntent = RULES.parse(text);
            parsed.source = "rules";
            return parsed;
        }
        try {
            String userPrompt = WebAiCompletionService.maybeAugmentUserPrompt(text, text);
            String raw = WebAiCompletionService
                .completeWithFailover(AssistantAiIntentService.buildSystemPrompt(locale), userPrompt).content;
            AssistantIntentPlan plan = AI_PARSER.parse(raw);
            if (plan != null && !plan.isEmpty()) {
                parsed.plan = plan;
                parsed.source = "ai";
                parsed.aiUsed = true;
                return parsed;
            }
            parsed.fallbackReason = "empty_ai_plan";
        } catch (Exception e) {
            parsed.fallbackReason = "ai_parse_failed";
        }
        parsed.fallbackIntent = RULES.parse(text);
        parsed.source = "rules";
        return parsed;
    }

    private static WebAssistantResult chat(WebAuthSession auth, WebAssistantRequest request, String text, String locale,
        WebAssistantResult base, String aiSource) {
        boolean browser = WebAiConfigStore.SOURCE_BROWSER.equals(aiSource);
        if (browser) {
            String reply = request == null ? "" : safe(request.clientAiReply).trim();
            base.success = true;
            base.intentType = AssistantIntentType.CHAT.name();
            if (!reply.isEmpty()) {
                base.code = "ok";
                base.message = reply;
                base.source = "browser-ai-chat";
                base.aiUsed = true;
            } else {
                base.code = "browser_ai_unavailable";
                base.message = localized(
                    locale,
                    "这是一条普通对话。请先在设置中保存当前浏览器自己的 AI API Key；游戏工具查询仍可使用本地意图解析。",
                    "This is ordinary chat. Save this browser's own AI API key in Settings first; game tool queries still use local intent parsing.");
                base.source = "rules";
                base.aiUsed = false;
            }
            return base;
        }
        if (auth.isGuest() || !WebAiConfigStore.isServerKeyEnabled()
            || WebAiConfigStore.instance()
                .runtimes()
                .isEmpty()) {
            base.success = true;
            base.code = "chat_ai_unavailable";
            base.intentType = AssistantIntentType.CHAT.name();
            base.message = localized(
                locale,
                "这是一条普通对话。管理员可在设置中配置 AI API；游戏工具查询仍可使用本地意图解析。",
                "This is ordinary chat. An admin can configure an AI API in Settings; game tool queries still use local intent parsing.");
            return base;
        }
        try {
            List<Message> messages = new ArrayList<Message>();
            messages.add(new Message("system", chatSystemPrompt(locale)));
            appendHistory(messages, request == null ? null : request.history);
            messages.add(new Message("user", text));
            WebAiCompletionService.SearchAugmentResult augmented = WebAiCompletionService
                .maybeAugmentWithSearch(messages, text);
            base.message = WebAiCompletionService.completeWithFailover(augmented.messages).content;
            base.success = true;
            base.code = "ok";
            base.intentType = AssistantIntentType.CHAT.name();
            base.source = "ai-chat";
            base.aiUsed = true;
            return base;
        } catch (IOException e) {
            base.success = false;
            base.code = "ai_chat_failed";
            base.message = localized(locale, "AI 对话请求失败。", "AI chat request failed.");
            return base;
        }
    }

    private static WebAssistantResult executePlan(WebAuthSession auth, EntityPlayerMP player, String rawText,
        String locale, AssistantIntentPlan plan, AssistantIntent fallbackIntent) {
        List<AssistantIntent> intents = new ArrayList<AssistantIntent>();
        if (plan != null && !plan.isEmpty()) {
            for (AssistantIntentTask task : plan.getTasks()) {
                if (task != null) intents.add(task.toIntent(rawText));
            }
        } else if (fallbackIntent != null) {
            intents.add(fallbackIntent);
        }
        if (intents.isEmpty()) intents.add(AssistantIntent.chat(rawText));

        List<AssistantIntent> expanded = new ArrayList<AssistantIntent>();
        for (AssistantIntent intent : intents) {
            if ((intent.type == AssistantIntentType.ORDER_BATCH || intent.type == AssistantIntentType.WITHDRAW_BATCH)
                && intent.orderLines != null
                && !intent.orderLines.isEmpty()) {
                AssistantIntentType itemType = intent.type == AssistantIntentType.ORDER_BATCH
                    ? AssistantIntentType.ORDER_ITEM
                    : AssistantIntentType.WITHDRAW_ITEM;
                for (AssistantOrderLine line : intent.orderLines) {
                    if (line != null) {
                        expanded.add(new AssistantIntent(itemType, intent.rawText, line.target, line.amount, -1));
                    }
                }
            } else {
                expanded.add(intent);
            }
        }

        WebAssistantResult result = new WebAssistantResult();
        result.success = true;
        result.code = "ok";
        List<String> messages = new ArrayList<String>();
        for (AssistantIntent intent : expanded) {
            TaskResult task = executeIntent(auth, player, intent, locale);
            result.tasks.add(task);
            if (result.intentType.isEmpty()) {
                result.intentType = task.intentType;
                result.intentTarget = task.intentTarget;
            }
            if (!task.success) result.success = false;
            if (!safe(task.message).isEmpty()) messages.add(task.message);
        }
        result.message = join(messages);
        if (result.message.isEmpty()) result.message = localized(locale, "已处理。", "Handled.");
        if (!result.success) result.code = "partial_failure";
        return result;
    }

    private static TaskResult executeIntent(WebAuthSession auth, EntityPlayerMP player, AssistantIntent intent,
        String locale) {
        TaskResult task = new TaskResult();
        task.intentType = intent.type.name();
        task.intentTarget = intent.target;
        task.success = true;
        boolean mutating = isMutating(intent.type);
        if (auth.isGuest() && mutating) {
            task.success = false;
            task.code = "guest_readonly";
            task.message = localized(locale, "访客令牌不能执行修改操作。", "Guest tokens cannot execute mutations.");
            return task;
        }
        switch (intent.type) {
            case ORDER_ITEM:
                return candidateTask(auth, player, intent, locale, true);
            case WITHDRAW_ITEM:
                return candidateTask(auth, player, intent, locale, false);
            case TELEPORT:
            case TELEPORT_LIST:
                return teleportTask(auth, player, intent, locale);
            case CONFIRM_OPTION:
                PendingAction pending = WebAssistantPendingStore.takeLatest(auth.actorUuid, auth.ownerUuid);
                if (pending == null) {
                    task.success = false;
                    task.code = "no_pending_action";
                    task.message = localized(locale, "没有等待确认的操作。", "No assistant action is awaiting confirmation.");
                    return task;
                }
                WebAssistantResult confirmed = executePending(
                    auth,
                    player,
                    pending,
                    intent.optionNumber > 0 ? intent.optionNumber : 1,
                    intent.amount);
                return confirmed.tasks.isEmpty() ? task : confirmed.tasks.get(0);
            case CANCEL:
                WebAssistantPendingStore.clearActor(auth.actorUuid);
                task.message = AssistantServerServices.cancelPendingJobs(player, locale);
                return task;
            case CLARIFY:
                task.message = localized(
                    locale,
                    "请说明是要从 AE2 存储取出已有物品，还是提交合成任务。",
                    "Please say whether to withdraw an existing item from AE2 storage or submit a crafting job.");
                return task;
            case CHAT:
            case HUD_OPEN:
            case HUD_CLOSE:
            case HISTORY_PREV:
            case HISTORY_NEXT:
                task.code = "web_client_only";
                task.message = localized(
                    locale,
                    "该客户端界面操作仅在游戏内可用。",
                    "That client UI action is available only in game.");
                return task;
            case ORDER_BATCH:
            case WITHDRAW_BATCH:
                task.success = false;
                task.code = "batch_requires_items";
                task.message = localized(
                    locale,
                    "请把批量请求写成多个明确物品；AI 会拆分为多个任务并逐项确认。",
                    "List explicit items in the batch; AI will split them into tasks for confirmation.");
                return task;
            case QUERY_STORAGE:
                task.message = AssistantServerServices
                    .queryStorage(player, intent.rawText, intent.target, intent.storageScope, locale);
                return task;
            default:
                task.message = AssistantServerServices
                    .query(player, intent.type, intent.rawText, intent.target, intent.amount, locale);
                return task;
        }
    }

    private static TaskResult candidateTask(WebAuthSession auth, EntityPlayerMP player, AssistantIntent intent,
        String locale, boolean craft) {
        TaskResult task = new TaskResult();
        task.intentType = intent.type.name();
        task.intentTarget = intent.target;
        if (auth.isGuest()) {
            task.success = false;
            task.code = "guest_readonly";
            task.message = localized(locale, "访客令牌不能执行该操作。", "Guest tokens cannot execute this action.");
            return task;
        }
        CandidateQueryResult query = craft
            ? AssistantServerServices.queryCraftingCandidates(player, intent.rawText, intent.target, intent.amount)
            : AssistantServerServices.queryWithdrawCandidates(player, intent.rawText, intent.target, intent.amount);
        if (query.candidates.isEmpty()) {
            task.success = false;
            task.code = "no_candidates";
            task.message = localized(locale, "没有找到匹配候选项。", "No matching candidates were found.");
            return task;
        }
        PendingAction pending = WebAssistantPendingStore.createCandidates(
            auth.actorUuid,
            auth.ownerUuid,
            craft ? "craft" : "withdraw",
            intent.rawText,
            locale,
            Math.max(1L, intent.amount),
            query.candidates);
        task.success = true;
        task.code = "confirmation_required";
        task.actionKind = pending.kind;
        task.actionToken = pending.token;
        task.truncated = query.truncated;
        task.candidates = candidateViews(query.candidates);
        task.message = localized(
            locale,
            craft ? "请选择候选项并确认合成订单。" : "请选择候选项并确认取出到背包。",
            craft ? "Choose a candidate and confirm the crafting order."
                : "Choose a candidate and confirm withdrawal to inventory.");
        return task;
    }

    private static TaskResult teleportTask(WebAuthSession auth, EntityPlayerMP player, AssistantIntent intent,
        String locale) {
        TaskResult task = new TaskResult();
        task.intentType = intent.type.name();
        task.intentTarget = intent.target;
        List<TeleportDestination> all = TeleportService.scanDislocators(player);
        List<TeleportDestination> filtered = intent.type == AssistantIntentType.TELEPORT_LIST ? all
            : TeleportService.filterDestinations(all, intent.target);
        task.teleportDestinations = teleportViews(filtered);
        if (filtered.isEmpty()) {
            task.success = false;
            task.code = "no_destinations";
            task.message = localized(locale, "没有找到可用传送点。", "No teleport destinations were found.");
            return task;
        }
        task.success = true;
        if (intent.type == AssistantIntentType.TELEPORT_LIST) {
            task.code = "ok";
            task.message = localized(locale, "可用传送点如下。", "Available teleport destinations are listed below.");
            return task;
        }
        if (auth.isGuest()) {
            task.success = false;
            task.code = "guest_readonly";
            task.message = localized(locale, "访客令牌不能执行传送。", "Guest tokens cannot teleport players.");
            return task;
        }
        PendingAction pending = WebAssistantPendingStore
            .createTeleport(auth.actorUuid, auth.ownerUuid, intent.rawText, locale, filtered);
        task.code = "confirmation_required";
        task.actionKind = pending.kind;
        task.actionToken = pending.token;
        task.message = localized(locale, "请选择目的地并确认传送。", "Choose a destination and confirm teleportation.");
        return task;
    }

    private static WebAssistantResult executePending(WebAuthSession auth, EntityPlayerMP player, PendingAction pending,
        int optionNumber, long amountOverride) {
        WebAssistantResult result = new WebAssistantResult();
        TaskResult task = new TaskResult();
        result.tasks.add(task);
        result.success = true;
        result.code = "ok";
        task.success = true;
        task.actionKind = pending.kind;
        int index = Math.max(1, optionNumber) - 1;
        long amount = amountOverride > 0L ? amountOverride : pending.amount;
        if ("teleport".equals(pending.kind)) {
            task.intentType = AssistantIntentType.TELEPORT.name();
            if (pending.destinations == null || index >= pending.destinations.size()) {
                return invalidOption(result, task, pending.locale);
            }
            task.message = TeleportService.executeTeleport(player, pending.destinations.get(index), pending.locale);
        } else {
            if (pending.candidates == null || index >= pending.candidates.size()) {
                return invalidOption(result, task, pending.locale);
            }
            CraftingCandidate candidate = pending.candidates.get(index);
            if ("craft".equals(pending.kind)) {
                task.intentType = AssistantIntentType.ORDER_ITEM.name();
                task.message = AssistantServerServices
                    .submitCraft(player, candidate, amount, pending.rawText, pending.locale);
            } else {
                task.intentType = AssistantIntentType.WITHDRAW_ITEM.name();
                WithdrawSubmitOutcome outcome = AssistantServerServices
                    .submitWithdraw(player, candidate, amount, pending.rawText, pending.locale, pending.confirmPartial);
                task.message = outcome.message;
                if (outcome.kind == Kind.PARTIAL_CONFIRM && outcome.candidate != null) {
                    PendingAction partial = WebAssistantPendingStore.createPartial(
                        auth.actorUuid,
                        auth.ownerUuid,
                        pending.rawText,
                        pending.locale,
                        outcome.candidate,
                        outcome.fitAmount);
                    task.code = "partial_confirmation_required";
                    task.actionToken = partial.token;
                    task.actionKind = partial.kind;
                    task.candidates = candidateViews(Collections.singletonList(outcome.candidate));
                } else if (outcome.kind == Kind.FAILURE) {
                    task.success = false;
                    result.success = false;
                    result.code = "action_failed";
                }
            }
        }
        result.intentType = task.intentType;
        result.message = task.message;
        return result;
    }

    private static WebAssistantResult invalidOption(WebAssistantResult result, TaskResult task, String locale) {
        result.success = false;
        result.code = "invalid_option";
        task.success = false;
        task.code = "invalid_option";
        task.message = localized(locale, "候选编号无效。", "Invalid candidate number.");
        result.message = task.message;
        return result;
    }

    private static boolean isMutating(AssistantIntentType type) {
        return type == AssistantIntentType.ORDER_ITEM || type == AssistantIntentType.ORDER_BATCH
            || type == AssistantIntentType.WITHDRAW_ITEM
            || type == AssistantIntentType.WITHDRAW_BATCH
            || type == AssistantIntentType.TELEPORT
            || type == AssistantIntentType.CONFIRM_OPTION
            || type == AssistantIntentType.CANCEL
            || type == AssistantIntentType.PLAN_ADD
            || type == AssistantIntentType.PLAN_CREATE
            || type == AssistantIntentType.PLAN_COMPLETE
            || type == AssistantIntentType.PLAN_DELETE
            || type == AssistantIntentType.PLAN_MODIFY;
    }

    private static void appendHistory(List<Message> messages, List<HistoryEntry> history) {
        if (history == null || history.isEmpty()) return;
        int start = Math.max(0, history.size() - 12);
        for (int i = start; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);
            if (entry == null) continue;
            String content = safe(entry.content);
            if (content.isEmpty()) continue;
            if (content.length() > 4_000) content = content.substring(0, 4_000);
            messages.add(new Message("assistant".equals(entry.role) ? "assistant" : "user", content));
        }
    }

    public static ClientAiContext clientAiContext(String requestedLocale, String searchQuery) {
        if (!WebAiConfigStore.isBrowserKeyEnabled()) {
            throw new IllegalStateException("Per-browser AI keys are disabled in server config.");
        }
        String locale = normalizeLocale(requestedLocale);
        ClientAiContext context = new ClientAiContext();
        context.keyMode = "browser";
        context.aiSource = WebAiConfigStore.SOURCE_BROWSER;
        String intentUser = safe(searchQuery);
        String chatUser = intentUser;
        context.intentSystemPrompt = AssistantAiIntentService.buildSystemPrompt(locale);
        context.chatSystemPrompt = chatSystemPrompt(locale);
        context.intentUserPrompt = WebAiCompletionService.maybeAugmentUserPrompt(intentUser, intentUser);
        context.chatUserPrompt = WebAiCompletionService.maybeAugmentUserPrompt(chatUser, chatUser);
        context.searchInjected = !context.intentUserPrompt.equals(intentUser)
            || !context.chatUserPrompt.equals(chatUser);
        return context;
    }

    /** @deprecated use {@link #clientAiContext(String, String)} */
    @Deprecated
    public static ClientAiContext clientAiContext(String requestedLocale) {
        return clientAiContext(requestedLocale, "");
    }

    private static String chatSystemPrompt(String locale) {
        return localized(
            locale,
            "你是 TeXTech WebAE AI 助手。简洁回答普通问题；涉及游戏工具时说明用户可以直接用自然语言查询存储、配方、电力、网络、计划、合成、取出和传送。不要声称执行了未实际执行的操作。",
            "You are the TeXTech WebAE AI assistant. Answer ordinary questions concisely. For game tools, explain that users can ask naturally about storage, recipes, power, networks, plans, crafting, withdrawals, and teleportation. Never claim an action ran unless the tool result says so.");
    }

    private static List<CandidateView> candidateViews(List<CraftingCandidate> candidates) {
        List<CandidateView> result = new ArrayList<CandidateView>();
        if (candidates == null) return result;
        int count = Math.min(20, candidates.size());
        for (int i = 0; i < count; i++) {
            CraftingCandidate candidate = candidates.get(i);
            CandidateView view = new CandidateView();
            view.optionNumber = i + 1;
            view.displayName = candidate.displayName;
            view.registryName = candidate.registryName;
            view.meta = candidate.meta;
            view.availableAmount = candidate.amount;
            result.add(view);
        }
        return result;
    }

    private static List<TeleportView> teleportViews(List<TeleportDestination> destinations) {
        List<TeleportView> result = new ArrayList<TeleportView>();
        if (destinations == null) return result;
        int count = Math.min(20, destinations.size());
        for (int i = 0; i < count; i++) {
            TeleportDestination destination = destinations.get(i);
            TeleportView view = new TeleportView();
            view.optionNumber = i + 1;
            view.name = destination.name;
            view.dimensionId = destination.dimensionId;
            view.dimensionName = destination.dimensionName;
            view.x = destination.x;
            view.y = destination.y;
            view.z = destination.z;
            result.add(view);
        }
        return result;
    }

    private static <T> T callServer(final ServerCall<T> call) throws Exception {
        final Object[] value = new Object[1];
        final Throwable[] error = new Throwable[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    value[0] = call.run();
                } catch (Throwable t) {
                    error[0] = t;
                } finally {
                    latch.countDown();
                }
            }
        });
        if (!latch.await(SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new IOException("Assistant server task timed out.");
        }
        if (error[0] != null) throw new Exception("Assistant server task failed.", error[0]);
        @SuppressWarnings("unchecked")
        T result = (T) value[0];
        return result;
    }

    private static WebAssistantResult failure(String code, String message) {
        WebAssistantResult result = new WebAssistantResult();
        result.success = false;
        result.code = code;
        result.message = message;
        return result;
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append("\n\n");
            result.append(value);
        }
        return result.toString();
    }

    private static String normalizeLocale(String locale) {
        return locale != null && locale.toLowerCase()
            .startsWith("en") ? "en_US" : "zh_CN";
    }

    private static String localized(String locale, String zh, String en) {
        return locale != null && locale.toLowerCase()
            .startsWith("en") ? en : zh;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private interface ServerCall<T> {

        T run();
    }

    private static final class ParsedPlan {

        AssistantIntentPlan plan;
        AssistantIntent fallbackIntent;
        String source = "rules";
        boolean aiUsed;
        String fallbackReason = "";
    }

    public static final class WebAssistantRequest {

        public String text;
        public String locale;
        public List<HistoryEntry> history;
        /** Preferred AI source for this request: {@code server} or {@code browser}. */
        public String aiSource;
        /** AI intent JSON produced by the browser in browser-key mode; never an API key. */
        public String clientAiPlan;
        /** Ordinary chat reply produced directly in the browser; never an API key. */
        public String clientAiReply;
    }

    public static final class ClientAiContext {

        public String keyMode;
        public String aiSource;
        public String intentSystemPrompt;
        public String chatSystemPrompt;
        /** User prompt for intent completion; may already include server-side search context. */
        public String intentUserPrompt;
        /** User prompt for chat completion; may already include server-side search context. */
        public String chatUserPrompt;
        public boolean searchInjected;
    }

    public static final class HistoryEntry {

        public String role;
        public String content;
    }

    public static final class WebAssistantActionRequest {

        public String actionToken;
        public int optionNumber = 1;
        public long amount;
    }

    public static final class WebAssistantResult {

        public boolean success;
        public String message = "";
        public String code = "";
        public String intentType = "";
        public String intentTarget = "";
        public long cooldownMs;
        public String source = "";
        public boolean aiUsed;
        public String fallbackReason = "";
        public List<TaskResult> tasks = new ArrayList<TaskResult>();
    }

    public static final class TaskResult {

        public boolean success;
        public String code = "";
        public String message = "";
        public String intentType = "";
        public String intentTarget = "";
        public String actionKind = "";
        public String actionToken = "";
        public boolean truncated;
        public List<CandidateView> candidates = new ArrayList<CandidateView>();
        public List<TeleportView> teleportDestinations = new ArrayList<TeleportView>();
    }

    public static final class CandidateView {

        public int optionNumber;
        public String displayName;
        public String registryName;
        public int meta;
        public long availableAmount;
    }

    public static final class TeleportView {

        public int optionNumber;
        public String name;
        public int dimensionId;
        public String dimensionName;
        public int x;
        public int y;
        public int z;
    }
}
