package com.imgood.advancedatamonitor.assistant;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAIChat;
import com.imgood.advancedatamonitor.network.packet.PacketAssistantAction;

public class AssistantController {

    private final AssistantIntentService intentService = new AssistantIntentService();
    private final AssistantAiIntentService aiIntentService = new AssistantAiIntentService();
    private final GuiAIChat chat;

    public AssistantController(GuiAIChat chat) {
        this.chat = chat;
        AdvanceDataMonitor.LOG.info("[ADM Assistant] Chat assistant controller initialized.");
    }

    private String selectedFeatureKey;

    public void setSelectedFeature(String featureKey) {
        this.selectedFeatureKey = featureKey;
    }

    public void clearSelectedFeature() {
        this.selectedFeatureKey = null;
    }

    public String getSelectedFeatureKey() {
        return this.selectedFeatureKey;
    }

    public String buildFeatureContextPrefix(String locale) {
        if (selectedFeatureKey == null) return null;
        AssistantFeatureConfig.FeatureEntry feature = null;
        AssistantFeatureConfig.FeatureConfigData cfg = AssistantFeatureConfig.get();
        if (cfg.features != null) {
            for (AssistantFeatureConfig.FeatureEntry entry : cfg.features) {
                if (entry.key.equals(selectedFeatureKey)) {
                    feature = entry;
                    break;
                }
            }
        }
        return feature != null ? AssistantFeatureConfig.buildFeatureContextPrefix(feature, locale) : null;
    }

    public boolean handlePrompt(String prompt) {
        return handlePrompt(prompt, "en_US");
    }

    public boolean handlePrompt(final String prompt, final String locale) {
        if (tryHandlePendingBatchPrompt(prompt) || tryHandlePendingPartialWithdrawPrompt(prompt)) {
            return true;
        }
        if (!this.aiIntentService.isAvailable()) {
            AssistantIntent intent = parseRuleIntent(prompt);
            return executeIntent(intent);
        }
        final String featureContext = buildFeatureContextPrefix(locale);
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                parseAiIntentInBackground(prompt, locale, featureContext);
            }
        }, "ADM Assistant Intent Parser");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    private void parseAiIntentInBackground(final String prompt, final String locale, final String featureContext) {
        try {
            final AssistantIntentPlan plan = this.aiIntentService.parse(prompt, locale, featureContext);
            if (plan == null || plan.isEmpty()) {
                throw new IllegalArgumentException("AI intent plan is empty.");
            }
            runOnClientThread(new Runnable() {

                @Override
                public void run() {
                    executePlan(prompt, plan);
                }
            });
        } catch (final Exception e) {
            AdvanceDataMonitor.LOG
                .warn("[ADM Assistant] AI intent parse failed; falling back to rule parser: {}", e.getMessage());
            AssistantDebugLog.append("ai-parse-fallback", safe(e.getMessage()));
            runOnClientThread(new Runnable() {

                @Override
                public void run() {
                    executeRuleFallbackAfterAi(prompt);
                }
            });
        }
    }

    private void executePlan(String prompt, AssistantIntentPlan plan) {
        AdvanceDataMonitor.LOG
            .info("[ADM Assistant] AI intent plan parsed: raw='{}', tasks={}", safe(prompt), plan.size());
        AssistantDebugLog.append("ai-parse", "tasks=" + plan.size() + ", raw='" + safe(prompt) + "'");
        if (plan.isChatOnly()) {
            if (tryPendingCandidateRuleFallback(prompt)) {
                return;
            }
            AssistantIntent fallbackIntent = parseRuleIntent(prompt);
            if (executeIntent(fallbackIntent)) {
                return;
            }
            this.chat.startNormalAiChatAfterAssistantParse(prompt);
            return;
        }
        if (plan.size() > 1) {
            this.chat.addAssistantMessage(I18n.format("adm.ai.assistant.split_tasks", plan.size()));
        }
        List<AssistantIntentTask> orderTasks = new ArrayList<AssistantIntentTask>();
        List<AssistantIntentTask> withdrawTasks = new ArrayList<AssistantIntentTask>();
        for (AssistantIntentTask task : plan.getTasks()) {
            if (task == null || task.isChat()) {
                continue;
            }
            if (task.isOrderItem()) {
                orderTasks.add(task);
            } else if (task.isWithdrawItem()) {
                withdrawTasks.add(task);
            } else {
                executeTask(prompt, task);
            }
        }
        executeWithdrawTasks(prompt, withdrawTasks);
        executeOrderTasks(prompt, orderTasks);
    }

    private void executeOrderTasks(String prompt, List<AssistantIntentTask> orderTasks) {
        if (orderTasks == null || orderTasks.isEmpty()) {
            return;
        }
        if (AssistantSession.client()
            .getKind() == AssistantSessionKind.ORDER_BATCH_CANDIDATES && isPendingBatchAppendText(prompt)
            && appendOrderTasksToPendingBatch(prompt, orderTasks)) {
            return;
        }
        if (orderTasks.size() == 1) {
            executeTask(prompt, orderTasks.get(0));
            return;
        }
        // Sequential: queue all tasks after the first, execute the first
        List<AssistantIntentTask> remaining = new ArrayList<AssistantIntentTask>(
            orderTasks.subList(1, orderTasks.size()));
        AssistantSession.client()
            .enqueueRemainingTasks(remaining);
        executeTask(prompt, orderTasks.get(0));
    }

    private void processNextQueuedTask(AssistantSession session) {
        AssistantIntentTask nextTask = session.dequeueNextTask();
        if (nextTask == null) {
            return;
        }
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Processing next queued task: type={}, target='{}', amount={}",
            nextTask.type,
            safe(nextTask.target),
            nextTask.amount);
        chat.addAssistantMessage(I18n.format("adm.ai.assistant.next_queued_task", nextTask.target, nextTask.amount));
        String prompt = nextTask.target.isEmpty() ? "next queued task" : nextTask.target;
        executeTask(prompt, nextTask);
    }

    private boolean executeTask(String prompt, AssistantIntentTask task) {
        if (task == null) {
            return false;
        }
        return executeIntent(task.toIntent(prompt));
    }

    private boolean tryHandlePendingBatchPrompt(String prompt) {
        AssistantSession session = AssistantSession.client();
        AssistantSessionKind kind = session.getKind();
        if (kind != AssistantSessionKind.ORDER_BATCH_CANDIDATES
            && kind != AssistantSessionKind.WITHDRAW_BATCH_CANDIDATES) {
            return false;
        }
        boolean withdraw = kind == AssistantSessionKind.WITHDRAW_BATCH_CANDIDATES;
        if (isPendingBatchMergeText(prompt)) {
            return requestMergedPendingBatch(prompt, withdraw);
        }
        if (isPendingBatchConfirmText(prompt)) {
            return withdraw ? confirmWithdrawBatch(session) : confirmBatch(session);
        }
        if (isPendingBatchAppendText(prompt)) {
            AssistantIntent intent = parseRuleIntent(stripPendingBatchAppendWords(prompt, withdraw));
            return appendIntentToPendingBatch(prompt, intent, withdraw);
        }
        return false;
    }

    private boolean tryHandlePendingPartialWithdrawPrompt(String prompt) {
        if (AssistantSession.client()
            .getKind() != AssistantSessionKind.WITHDRAW_PARTIAL_CONFIRM) {
            return false;
        }
        if (isPendingBatchConfirmText(prompt)) {
            return confirmPartialWithdraw(parseRuleIntent(prompt));
        }
        AssistantIntent fallback = parseRuleIntent(prompt);
        if (fallback.type == AssistantIntentType.CANCEL) {
            return executeIntent(fallback);
        }
        return false;
    }

    private void executeWithdrawTasks(String prompt, List<AssistantIntentTask> withdrawTasks) {
        if (withdrawTasks == null || withdrawTasks.isEmpty()) {
            return;
        }
        if (AssistantSession.client()
            .getKind() == AssistantSessionKind.WITHDRAW_BATCH_CANDIDATES && isPendingBatchAppendText(prompt)
            && appendWithdrawTasksToPendingBatch(prompt, withdrawTasks)) {
            return;
        }
        if (withdrawTasks.size() == 1) {
            executeTask(prompt, withdrawTasks.get(0));
            return;
        }
        // Sequential: queue all tasks after the first, execute the first
        List<AssistantIntentTask> remaining = new ArrayList<AssistantIntentTask>(
            withdrawTasks.subList(1, withdrawTasks.size()));
        AssistantSession.client()
            .enqueueRemainingTasks(remaining);
        executeTask(prompt, withdrawTasks.get(0));
    }

    private boolean appendIntentToPendingBatch(String prompt, AssistantIntent intent) {
        return appendIntentToPendingBatch(prompt, intent, false);
    }

    private boolean appendIntentToPendingBatch(String prompt, AssistantIntent intent, boolean withdraw) {
        if (intent == null) {
            return false;
        }
        List<AssistantOrderLine> lines = new ArrayList<AssistantOrderLine>();
        if (intent.type == AssistantIntentType.ORDER_ITEM || intent.type == AssistantIntentType.WITHDRAW_ITEM) {
            if (!intent.target.trim()
                .isEmpty()) {
                lines.add(new AssistantOrderLine(1, intent.target, intent.amount));
            }
        } else
            if (intent.type == AssistantIntentType.ORDER_BATCH || intent.type == AssistantIntentType.WITHDRAW_BATCH) {
                for (AssistantOrderLine line : intent.orderLines) {
                    if (line != null && !line.target.trim()
                        .isEmpty()) {
                        lines.add(line.copyWithoutCandidates());
                    }
                }
            }
        return withdraw ? appendWithdrawLinesToPendingBatch(prompt, lines)
            : appendOrderLinesToPendingBatch(prompt, lines);
    }

    private boolean appendWithdrawTasksToPendingBatch(String prompt, List<AssistantIntentTask> withdrawTasks) {
        List<AssistantOrderLine> lines = new ArrayList<AssistantOrderLine>();
        for (AssistantIntentTask task : withdrawTasks) {
            if (task != null && !task.target.trim()
                .isEmpty()) {
                lines.add(task.toOrderLine(lines.size() + 1));
            }
        }
        return appendWithdrawLinesToPendingBatch(prompt, lines);
    }

    private boolean appendOrderTasksToPendingBatch(String prompt, List<AssistantIntentTask> orderTasks) {
        List<AssistantOrderLine> lines = new ArrayList<AssistantOrderLine>();
        for (AssistantIntentTask task : orderTasks) {
            if (task != null && !task.target.trim()
                .isEmpty()) {
                lines.add(task.toOrderLine(lines.size() + 1));
            }
        }
        return appendOrderLinesToPendingBatch(prompt, lines);
    }

    private boolean appendOrderLinesToPendingBatch(String prompt, List<AssistantOrderLine> appendedLines) {
        if (appendedLines == null || appendedLines.isEmpty()) {
            return false;
        }
        List<AssistantOrderLine> mergedLines = copyPendingOrderLinesWithoutCandidates();
        boolean appended = false;
        for (AssistantOrderLine line : appendedLines) {
            String target = line == null || line.target == null ? "" : line.target.trim();
            if (target.isEmpty()) {
                continue;
            }
            mergedLines.add(new AssistantOrderLine(mergedLines.size() + 1, target, line.amount));
            appended = true;
        }
        if (!appended) {
            return false;
        }
        requestBatchCraftingCandidates(AssistantIntent.orderBatch(prompt, mergedLines));
        return true;
    }

    private boolean requestMergedPendingBatch(String prompt) {
        return requestMergedPendingBatch(prompt, false);
    }

    private boolean requestMergedPendingBatch(String prompt, boolean withdraw) {
        List<AssistantOrderLine> pendingLines = AssistantSession.client()
            .getPendingOrderLines();
        if (pendingLines.isEmpty()) {
            return false;
        }
        Map<String, AssistantOrderLine> mergedByTarget = new LinkedHashMap<String, AssistantOrderLine>();
        for (AssistantOrderLine line : pendingLines) {
            String target = line == null || line.target == null ? "" : line.target.trim();
            if (target.isEmpty()) {
                continue;
            }
            String key = target;
            AssistantOrderLine existing = mergedByTarget.get(key);
            if (existing == null) {
                mergedByTarget.put(key, new AssistantOrderLine(mergedByTarget.size() + 1, target, line.amount));
            } else {
                existing.amount += Math.max(1L, line.amount);
            }
        }
        if (mergedByTarget.isEmpty()) {
            return false;
        }
        List<AssistantOrderLine> mergedLines = new ArrayList<AssistantOrderLine>(mergedByTarget.values());
        if (withdraw) {
            requestBatchWithdrawCandidates(AssistantIntent.withdrawBatch(prompt, mergedLines));
        } else {
            requestBatchCraftingCandidates(AssistantIntent.orderBatch(prompt, mergedLines));
        }
        return true;
    }

    private boolean appendWithdrawLinesToPendingBatch(String prompt, List<AssistantOrderLine> appendedLines) {
        if (appendedLines == null || appendedLines.isEmpty()) {
            return false;
        }
        List<AssistantOrderLine> mergedLines = copyPendingOrderLinesWithoutCandidates();
        boolean appended = false;
        for (AssistantOrderLine line : appendedLines) {
            String target = line == null || line.target == null ? "" : line.target.trim();
            if (target.isEmpty()) {
                continue;
            }
            mergedLines.add(new AssistantOrderLine(mergedLines.size() + 1, target, line.amount));
            appended = true;
        }
        if (!appended) {
            return false;
        }
        requestBatchWithdrawCandidates(AssistantIntent.withdrawBatch(prompt, mergedLines));
        return true;
    }

    private List<AssistantOrderLine> copyPendingOrderLinesWithoutCandidates() {
        List<AssistantOrderLine> result = new ArrayList<AssistantOrderLine>();
        for (AssistantOrderLine line : AssistantSession.client()
            .getPendingOrderLines()) {
            String target = line == null || line.target == null ? "" : line.target.trim();
            if (target.isEmpty()) {
                continue;
            }
            result.add(new AssistantOrderLine(result.size() + 1, target, line.amount));
        }
        return result;
    }

    private boolean isPendingBatchConfirmText(String prompt) {
        String compact = pendingBatchComparableText(prompt);
        if (compact.isEmpty()) {
            return false;
        }
        return equalsAnyText(
            compact,
            "\u786e\u8ba4",
            "\u63d0\u4ea4",
            "\u7ee7\u7eed",
            "\u786e\u8ba4\u4e0b\u5355",
            "\u63d0\u4ea4\u4e0b\u5355",
            "\u7ee7\u7eed\u4e0b\u5355",
            "\u7ee7\u7eed\u63d0\u4ea4",
            "\u786e\u8ba4\u63d0\u4ea4",
            "\u63d0\u4ea4\u8ba2\u5355",
            "\u786e\u8ba4\u8ba2\u5355",
            "\u73b0\u5728\u53ef\u4ee5\u7ee7\u7eed",
            "\u73b0\u5728\u53ef\u4ee5\u7ee7\u7eed\u4e0b\u5355",
            "\u53ef\u4ee5\u7ee7\u7eed",
            "\u53ef\u4ee5\u7ee7\u7eed\u4e0b\u5355",
            "\u53ef\u4ee5\u63d0\u4ea4",
            "confirm",
            "submit",
            "continue");
    }

    private boolean isPendingBatchMergeText(String prompt) {
        String compact = pendingBatchComparableText(prompt);
        return containsAnyText(compact, "\u521a\u521a", "\u521a\u624d", "\u4e4b\u524d", "\u4e0a\u6b21", "\u524d\u9762")
            && containsAnyText(
                compact,
                "\u52a0\u8d77\u6765",
                "\u5408\u5e76",
                "\u6c47\u603b",
                "\u4e00\u8d77",
                "\u603b\u5171",
                "\u52a0\u603b");
    }

    private boolean isPendingBatchAppendText(String prompt) {
        String compact = pendingBatchComparableText(prompt);
        return containsAnyText(
            compact,
            "\u518d\u52a0",
            "\u52a0\u4e00\u4e2a",
            "\u6dfb\u52a0",
            "\u8865\u4e00\u4e2a",
            "\u8ffd\u52a0",
            "\u518d\u6dfb\u52a0",
            "\u518d\u8865",
            "\u518d\u6765",
            "\u518d\u8981",
            "add",
            "append");
    }

    private String pendingBatchComparableText(String prompt) {
        String text = AssistantTextNormalizer.stripCommon(prompt);
        text = AssistantTextNormalizer.stripModalParticles(text);
        text = AssistantTextNormalizer.stripPunctuation(text);
        return text.toLowerCase()
            .replaceAll("[\\s,.;:!?\\uFF0C\\u3002\\uFF1B\\uFF1A\\uFF01\\uFF1F\\u3001]+", "");
    }

    private String stripPendingBatchAppendWords(String prompt) {
        return stripPendingBatchAppendWords(prompt, false);
    }

    private String stripPendingBatchAppendWords(String prompt, boolean withdraw) {
        String text = prompt == null ? "" : prompt;
        text = AssistantTextNormalizer.removeWords(
            text,
            "\u518d\u52a0\u4e00\u4e2a",
            "\u518d\u52a0",
            "\u52a0\u4e00\u4e2a",
            "\u6dfb\u52a0",
            "\u8865\u4e00\u4e2a",
            "\u8ffd\u52a0",
            "\u518d\u6dfb\u52a0",
            "\u518d\u8865",
            "\u518d\u6765",
            "\u518d\u8981");
        text = AssistantTextNormalizer.stripPunctuation(text);
        if (text.isEmpty()) {
            return text;
        }
        return withdraw ? "\u53d6\u51fa" + text : "\u4e0b\u5355" + text;
    }

    private boolean equalsAnyText(String text, String... values) {
        if (text == null || values == null) {
            return false;
        }
        for (String value : values) {
            if (text.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyText(String text, String... values) {
        if (text == null || values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.isEmpty() && text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private void executeRuleFallbackAfterAi(String prompt) {
        AssistantIntent intent = parseRuleIntent(prompt);
        if (!executeIntent(intent)) {
            this.chat.startNormalAiChatAfterAssistantParse(prompt);
        }
    }

    private AssistantIntent parseRuleIntent(String prompt) {
        AssistantIntent intent = this.intentService.parse(prompt);
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Prompt parsed by rules: type={}, raw='{}', target='{}', amount={}, option={}",
            intent.type,
            safe(intent.rawText),
            safe(intent.target),
            intent.amount,
            intent.optionNumber);
        AssistantDebugLog.append(
            "client-parse",
            "type=" + intent.type
                + ", raw='"
                + safe(intent.rawText)
                + "', target='"
                + safe(intent.target)
                + "', amount="
                + intent.amount
                + ", option="
                + intent.optionNumber);
        return intent;
    }

    private boolean executeIntent(AssistantIntent intent) {
        switch (intent.type) {
            case TELEPORT:
            case TELEPORT_LIST:
                requestTeleportCandidates(intent);
                return true;
            case CANCEL:
                AssistantSession.client()
                    .clear();
                AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketAssistantAction.cancelServerJobs(intent.rawText));
                chat.addAssistantMessage(I18n.format("adm.ai.assistant.cancelled_action"));
                return true;
            case PLAN_CREATE:
            case PLAN_ADD:
            case PLAN_LIST:
            case PLAN_COMPLETE:
            case PLAN_DELETE:
            case PLAN_MODIFY:
                requestServerQuery(intent);
                return true;
            case CONFIRM_OPTION:
                return confirmOption(intent);
            case ORDER_ITEM:
                requestCraftingCandidates(intent);
                return true;
            case ORDER_BATCH:
                requestBatchCraftingCandidates(intent);
                return true;
            case WITHDRAW_ITEM:
                requestWithdrawCandidates(intent);
                return true;
            case WITHDRAW_BATCH:
                requestBatchWithdrawCandidates(intent);
                return true;
            case CLARIFY:
                chat.addAssistantMessage(I18n.format("adm.ai.assistant.clarify_order_withdraw"));
                return true;
            case QUERY_STORAGE:
            case QUERY_RECIPE:
            case QUERY_POWER:
            case QUERY_STEAM:
            case QUERY_WEATHER:
            case QUERY_TIME:
            case QUERY_POSITION:
            case QUERY_BIOME:
            case QUERY_INVENTORY:
            case QUERY_NETWORK:
            case QUERY_JOBS:
            case QUERY_ITEM_COUNT:
            case QUERY_BYTES:
                requestServerQuery(intent);
                return true;
            default:
                AdvanceDataMonitor.LOG
                    .info("[ADM Assistant] Falling back to normal AI chat for prompt='{}'", safe(intent.rawText));
                return false;
        }
    }

    private boolean confirmOption(AssistantIntent intent) {
        AssistantSession session = AssistantSession.client();
        if (session.getKind() == AssistantSessionKind.ORDER_BATCH_CANDIDATES) {
            return confirmBatch(session);
        }
        if (session.getKind() == AssistantSessionKind.WITHDRAW_BATCH_CANDIDATES) {
            return confirmWithdrawBatch(session);
        }
        if (session.getKind() == AssistantSessionKind.WITHDRAW_PARTIAL_CONFIRM) {
            return confirmPartialWithdraw(intent);
        }
        if (session.getKind() == AssistantSessionKind.TELEPORT_CANDIDATES) {
            return confirmTeleport(session, intent);
        }
        CraftingCandidate candidate = resolveSingleCandidateConfirmation(session, intent);
        if (candidate == null) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] Confirm failed: no candidate for option {} target '{}' kind={} pendingCount={}",
                intent.optionNumber,
                safe(intent.target),
                session.getKind(),
                session.getPendingCandidates()
                    .size());
            chat.addAssistantMessage(I18n.format("adm.ai.assistant.no_pending_candidate"));
            return true;
        }
        boolean withdraw = session.getKind() == AssistantSessionKind.WITHDRAW_CANDIDATES
            || session.getKind() == AssistantSessionKind.ITEM_COUNT_CANDIDATES
            || session.getKind() == AssistantSessionKind.STORAGE_CANDIDATES;
        long maxAmount = withdraw ? Config.assistantMaxWithdrawAmount : Config.assistantMaxOrderAmount;
        long amount = Math.max(1L, Math.min(intent.amount > 0 ? intent.amount : candidate.amount, maxAmount));
        if (session.getKind() == AssistantSessionKind.RECIPE_CANDIDATES) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] Requesting recipe details from candidate: option={}, item='{}', amount={}",
                intent.optionNumber,
                safe(candidate.displayName),
                amount);
            AdvanceDataMonitor.ADMCHANEL
                .sendToServer(PacketAssistantAction.queryRecipeCandidate(candidate, amount, session.getLastUserText()));
            chat.addAssistantMessage(I18n.format("adm.ai.assistant.querying_recipe_details", candidate.displayName));
            processNextQueuedTask(session);
            return true;
        }
        if (withdraw) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] Sending withdraw submit: option={}, item='{}', registry='{}', amount={}, memoryRaw='{}'",
                intent.optionNumber,
                safe(candidate.displayName),
                safe(candidate.registryName),
                amount,
                safe(session.getLastUserText()));
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                PacketAssistantAction.submitWithdraw(candidate, amount, session.getLastUserText(), false));
            chat.addAssistantMessage(
                I18n.format("adm.ai.assistant.submitted_withdraw_confirmation", candidate.displayName, amount));
            session.clear();
            processNextQueuedTask(session);
            return true;
        }
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Sending craft submit: option={}, item='{}', registry='{}', amount={}, memoryRaw='{}'",
            intent.optionNumber,
            safe(candidate.displayName),
            safe(candidate.registryName),
            amount,
            safe(session.getLastUserText()));
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketAssistantAction.submitCraft(candidate, amount, session.getLastUserText()));
        chat.addAssistantMessage(
            I18n.format("adm.ai.assistant.submitted_order_confirmation", candidate.displayName, amount));
        session.clear();
        processNextQueuedTask(session);
        return true;
    }

    private CraftingCandidate resolveSingleCandidateConfirmation(AssistantSession session, AssistantIntent intent) {
        CraftingCandidate candidate = null;
        if (intent.optionNumber > 0) {
            candidate = session.select(intent.optionNumber);
        } else if (intent.target != null && !intent.target.trim()
            .isEmpty()) {
                candidate = session.findByName(intent.target);
            }
        if (candidate != null) {
            return candidate;
        }
        List<CraftingCandidate> pendingCandidates = session.getPendingCandidates();
        if (pendingCandidates.size() == 1 && intent.type == AssistantIntentType.CONFIRM_OPTION) {
            CraftingCandidate soleCandidate = pendingCandidates.get(0);
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] Confirm defaulted to sole pending candidate: kind={}, item='{}', amountOverride={}, raw='{}'",
                session.getKind(),
                soleCandidate == null ? "" : safe(soleCandidate.displayName),
                intent.amount,
                safe(intent.rawText));
            return soleCandidate;
        }
        return null;
    }

    private boolean tryPendingCandidateRuleFallback(String prompt) {
        AssistantSession session = AssistantSession.client();
        AssistantSessionKind kind = session.getKind();
        boolean hasPendingCandidateSession = (kind == AssistantSessionKind.ORDER_CANDIDATES
            || kind == AssistantSessionKind.RECIPE_CANDIDATES
            || kind == AssistantSessionKind.WITHDRAW_CANDIDATES
            || kind == AssistantSessionKind.ITEM_COUNT_CANDIDATES
            || kind == AssistantSessionKind.STORAGE_CANDIDATES
            || kind == AssistantSessionKind.WITHDRAW_PARTIAL_CONFIRM)
            && !session.getPendingCandidates()
                .isEmpty();
        boolean hasPendingTeleportSession = kind == AssistantSessionKind.TELEPORT_CANDIDATES
            && !session.getPendingTeleportDestinations()
                .isEmpty();
        boolean hasPendingBatchSession = (kind == AssistantSessionKind.ORDER_BATCH_CANDIDATES
            || kind == AssistantSessionKind.WITHDRAW_BATCH_CANDIDATES)
            && !session.getPendingOrderLines()
                .isEmpty();
        if (!hasPendingCandidateSession && !hasPendingBatchSession && !hasPendingTeleportSession) {
            return false;
        }
        AssistantIntent fallback = parseRuleIntent(prompt);
        if (fallback.type == AssistantIntentType.CONFIRM_OPTION || fallback.type == AssistantIntentType.CANCEL
            || fallback.type == AssistantIntentType.ORDER_ITEM
            || fallback.type == AssistantIntentType.WITHDRAW_ITEM) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] AI returned chat during pending session; executing rule fallback type={} kind={}",
                fallback.type,
                kind);
            return executeIntent(fallback);
        }
        return false;
    }

    private boolean confirmBatch(AssistantSession session) {
        List<AssistantOrderLine> lines = session.getPendingOrderLines();
        if (lines.isEmpty()) {
            chat.addAssistantMessage(I18n.format("adm.ai.assistant.no_pending_batch"));
            return true;
        }
        for (AssistantOrderLine line : lines) {
            if (!line.hasUsableCandidate()) {
                chat.addAssistantMessage(
                    I18n.format("adm.ai.assistant.batch_line_no_candidate", line.lineIndex, line.target));
                return true;
            }
        }
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketAssistantAction.submitBatchCraft(session.getLastUserText(), lines));
        chat.addAssistantMessage(I18n.format("adm.ai.assistant.submitted_batch_confirmation", lines.size()));
        return true;
    }

    private void requestCraftingCandidates(AssistantIntent intent) {
        List<CraftingCandidate> local = localInventoryFallback(intent);
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Requesting craft candidates: target='{}', amount={}, localFallbackCount={}",
            safe(intent.target),
            intent.amount,
            local.size());
        if (!local.isEmpty()) {
            AssistantSession.client()
                .setPendingCandidates(intent.rawText, local, AssistantSessionKind.ORDER_CANDIDATES);
            chat.addAssistantCandidatesMessage(
                AssistantFormatter.candidates(I18n.format("adm.ai.assistant.local_matching_candidates"), local),
                local);
        }
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketAssistantAction.requestCraftCandidates(intent.rawText, intent.target, intent.amount));
        if (local.isEmpty()) {
            chat.addAssistantMessage(I18n.format("adm.ai.assistant.searching_craft_candidates"));
        }
    }

    private void requestBatchCraftingCandidates(AssistantIntent intent) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Requesting batch craft candidates: raw='{}', lines={}",
            safe(intent.rawText),
            intent.orderLines.size());
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketAssistantAction.requestBatchCandidates(intent.rawText, intent.orderLines));
        chat.addAssistantMessage(I18n.format("adm.ai.assistant.searching_batch_candidates", intent.orderLines.size()));
    }

    private void requestWithdrawCandidates(AssistantIntent intent) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Requesting withdraw candidates: target='{}', amount={}",
            safe(intent.target),
            intent.amount);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            PacketAssistantAction.requestWithdrawCandidates(intent.rawText, intent.target, intent.amount));
        chat.addAssistantMessage(I18n.format("adm.ai.assistant.searching_withdraw_candidates"));
    }

    private void requestBatchWithdrawCandidates(AssistantIntent intent) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Requesting batch withdraw candidates: raw='{}', lines={}",
            safe(intent.rawText),
            intent.orderLines.size());
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketAssistantAction.requestBatchWithdrawCandidates(intent.rawText, intent.orderLines));
        chat.addAssistantMessage(
            I18n.format("adm.ai.assistant.searching_batch_withdraw_candidates", intent.orderLines.size()));
    }

    private boolean confirmWithdrawBatch(AssistantSession session) {
        List<AssistantOrderLine> lines = session.getPendingOrderLines();
        if (lines.isEmpty()) {
            chat.addAssistantMessage(I18n.format("adm.ai.assistant.no_pending_batch"));
            return true;
        }
        for (AssistantOrderLine line : lines) {
            if (!line.hasUsableCandidate()) {
                chat.addAssistantMessage(
                    I18n.format("adm.ai.assistant.batch_line_no_candidate", line.lineIndex, line.target));
                return true;
            }
        }
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketAssistantAction.submitBatchWithdraw(session.getLastUserText(), lines));
        chat.addAssistantMessage(I18n.format("adm.ai.assistant.submitted_batch_withdraw_confirmation", lines.size()));
        return true;
    }

    private boolean confirmPartialWithdraw(AssistantIntent intent) {
        AssistantSession session = AssistantSession.client();
        CraftingCandidate candidate = resolveSingleCandidateConfirmation(session, intent);
        if (candidate == null) {
            chat.addAssistantMessage(I18n.format("adm.ai.assistant.no_pending_candidate"));
            return true;
        }
        long amount = session.getPendingPartialAmount();
        if (intent.amount > 0L) {
            amount = Math.min(intent.amount, session.getPendingPartialAmount());
        }
        amount = Math.max(1L, Math.min(amount, Config.assistantMaxWithdrawAmount));
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketAssistantAction.submitWithdraw(candidate, amount, session.getLastUserText(), true));
        chat.addAssistantMessage(
            I18n.format("adm.ai.assistant.submitted_withdraw_confirmation", candidate.displayName, amount));
        return true;
    }

    private boolean confirmTeleport(AssistantSession session, AssistantIntent intent) {
        TeleportDestination dest = null;
        if (intent.optionNumber > 0) {
            dest = session.selectTeleportDestination(intent.optionNumber);
        } else if (intent.target != null && !intent.target.trim()
            .isEmpty()) {
                dest = session.findTeleportDestinationByName(intent.target);
            }
        if (dest == null) {
            List<TeleportDestination> pending = session.getPendingTeleportDestinations();
            if (pending.size() == 1 && intent.type == AssistantIntentType.CONFIRM_OPTION) {
                dest = pending.get(0);
            }
        }
        if (dest == null) {
            chat.addAssistantMessage(I18n.format("adm.ai.assistant.no_pending_teleport_candidate"));
            return true;
        }
        requestTeleportSubmit(dest, session.getLastUserText());
        return true;
    }

    private List<CraftingCandidate> localInventoryFallback(AssistantIntent intent) {
        List<CraftingCandidate> result = new ArrayList<CraftingCandidate>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || intent.target == null
            || intent.target.trim()
                .isEmpty()) {
            return result;
        }
        int index = 1;
        for (int i = 0; i < mc.thePlayer.inventory.getSizeInventory() && result.size() < 5; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (ItemStackUtils.fuzzyNameMatches(stack, intent.target)) {
                result.add(new CraftingCandidate(index++, stack, Math.max(1L, intent.amount)));
            }
        }
        return result;
    }

    private void requestServerQuery(AssistantIntent intent) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Sending server query: type={}, raw='{}', target='{}', amount={}",
            intent.type,
            safe(intent.rawText),
            safe(intent.target),
            intent.amount);
        long requestAmount = intent.amount;
        if (intent.type == AssistantIntentType.PLAN_COMPLETE || intent.type == AssistantIntentType.PLAN_DELETE
            || intent.type == AssistantIntentType.PLAN_MODIFY) {
            requestAmount = intent.optionNumber;
        }
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            PacketAssistantAction
                .query(intent.type, intent.rawText, intent.target, requestAmount, intent.storageScope));
        chat.addAssistantMessage(I18n.format("adm.ai.assistant.querying_server_data", intent.rawText));
    }

    private void requestTeleportCandidates(AssistantIntent intent) {
        AdvanceDataMonitor.LOG.info("[ADM Assistant] Requesting teleport candidates: target='{}'", safe(intent.target));
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketAssistantAction.requestTeleportCandidates(intent.rawText, intent.target));
        chat.addAssistantMessage(I18n.format("adm.ai.assistant.searching_teleport_candidates"));
    }

    private void requestTeleportSubmit(TeleportDestination dest, String rawText) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Sending teleport submit: dest='{}', dim={}, ({},{},{})",
            dest.name,
            dest.dimensionId,
            dest.x,
            dest.y,
            dest.z);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketAssistantAction.submitTeleport(dest, rawText));
        chat.addAssistantMessage(
            I18n.format("adm.ai.assistant.submitted_teleport_confirmation", dest.formatEntry("zh_CN")));
        AssistantSession.client()
            .clear();
    }

    private void runOnClientThread(Runnable runnable) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            Method method = mc.getClass()
                .getMethod("func_152344_a", Runnable.class);
            method.invoke(mc, runnable);
        } catch (Exception ignored) {
            runnable.run();
        }
    }

    public static void handleServerMessage(String message) {
        AdvanceDataMonitor.LOG.info("[ADM Assistant] Server message received: {}", safe(message));
        AssistantDebugLog.append("client-response", safe(message));
        if (message != null
            && (message.contains("\u5df2\u53d6\u51fa\u5230\u80cc\u5305") || message.contains("Withdrawn to inventory")
                || message.contains("\u6279\u91cf\u53d6\u51fa\u7ed3\u679c")
                || message.contains("Batch withdraw results")
                || message.contains("\u5df2\u4f20\u9001\u5230")
                || message.contains("Teleported to"))) {
            AssistantSession.client()
                .clear();
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiAIChat) {
            ((GuiAIChat) mc.currentScreen).addAssistantMessage(message);
        } else if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("[ADM AI] " + message));
        }
    }

    public static void handleCandidates(String rawText, List<CraftingCandidate> candidates, AssistantSessionKind kind) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Server candidates received: raw='{}', kind={}, count={}",
            safe(rawText),
            kind,
            candidates == null ? 0 : candidates.size());
        AssistantSessionKind effectiveKind = kind == null ? AssistantSessionKind.ORDER_CANDIDATES : kind;
        AssistantSession.client()
            .setPendingCandidates(rawText, candidates, effectiveKind);
        String title;
        if (effectiveKind == AssistantSessionKind.RECIPE_CANDIDATES) {
            title = I18n.format("adm.ai.assistant.recipe_candidates_title");
        } else if (effectiveKind == AssistantSessionKind.WITHDRAW_CANDIDATES) {
            title = I18n.format("adm.ai.assistant.withdraw_candidates_title");
        } else if (effectiveKind == AssistantSessionKind.ITEM_COUNT_CANDIDATES) {
            title = I18n.format("adm.ai.assistant.item_count_candidates_title");
        } else if (effectiveKind == AssistantSessionKind.STORAGE_CANDIDATES) {
            title = I18n.format("adm.ai.assistant.storage_candidates_title");
        } else {
            title = I18n.format("adm.ai.assistant.craft_candidates_title");
        }
        Minecraft mc = Minecraft.getMinecraft();
        String message = AssistantFormatter.candidates(title, candidates);
        if (mc.currentScreen instanceof GuiAIChat) {
            ((GuiAIChat) mc.currentScreen).addAssistantCandidatesMessage(message, candidates);
        } else {
            handleServerMessage(message);
        }
    }

    public static void handleBatchCandidates(String rawText, List<AssistantOrderLine> lines) {
        handleBatchCandidates(rawText, lines, AssistantSessionKind.ORDER_BATCH_CANDIDATES);
    }

    public static void handleBatchCandidates(String rawText, List<AssistantOrderLine> lines,
        AssistantSessionKind kind) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Server batch candidates received: raw='{}', kind={}, lines={}",
            safe(rawText),
            kind,
            lines == null ? 0 : lines.size());
        AssistantSessionKind effectiveKind = kind == null ? AssistantSessionKind.ORDER_BATCH_CANDIDATES : kind;
        AssistantSession.client()
            .setPendingOrderLines(rawText, lines, effectiveKind);
        String title = effectiveKind == AssistantSessionKind.WITHDRAW_BATCH_CANDIDATES
            ? I18n.format("adm.ai.assistant.batch_withdraw_candidates_title")
            : I18n.format("adm.ai.assistant.batch_candidates_title");
        Minecraft mc = Minecraft.getMinecraft();
        String message = AssistantFormatter.batchOrderLines(title, lines);
        if (mc.currentScreen instanceof GuiAIChat) {
            ((GuiAIChat) mc.currentScreen).addAssistantBatchMessage(message, lines);
        } else {
            handleServerMessage(message);
        }
    }

    public static void handleWithdrawPartial(String rawText, String message, CraftingCandidate candidate,
        long requestedAmount, long fitAmount, long storageAmount) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Withdraw partial confirm required: raw='{}', requested={}, fit={}, storage={}",
            safe(rawText),
            requestedAmount,
            fitAmount,
            storageAmount);
        List<CraftingCandidate> candidates = new ArrayList<CraftingCandidate>();
        if (candidate != null) {
            candidates.add(candidate);
        }
        AssistantSession.client()
            .setPendingPartialWithdraw(rawText, candidates, requestedAmount, fitAmount);
        handleServerMessage(message);
    }

    public static void handleTeleportCandidates(String rawText, List<TeleportDestination> destinations) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Teleport candidates received: raw='{}', count={}",
            safe(rawText),
            destinations == null ? 0 : destinations.size());
        if (destinations == null || destinations.isEmpty()) {
            handleServerMessage(I18n.format("adm.ai.assistant.no_teleport_destinations"));
            return;
        }
        AssistantSession.client()
            .setPendingTeleportDestinations(rawText, destinations);
        String title = I18n.format("adm.ai.assistant.teleport_candidates_title");
        Minecraft mc = Minecraft.getMinecraft();
        String message = AssistantFormatter.teleportDestinations(title, destinations);
        if (mc.currentScreen instanceof GuiAIChat) {
            ((GuiAIChat) mc.currentScreen).addAssistantMessage(message);
        } else {
            handleServerMessage(message);
        }
    }

    private static String safe(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ')
            .replace('\r', ' ');
    }
}
