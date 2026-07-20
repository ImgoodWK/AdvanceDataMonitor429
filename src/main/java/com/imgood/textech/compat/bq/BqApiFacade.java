package com.imgood.textech.compat.bq;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestLineEdgeDto;
import com.imgood.textech.webae.dto.QuestLineGraphDto;
import com.imgood.textech.webae.dto.QuestLineNodeDto;
import com.imgood.textech.webae.dto.QuestLineSummaryDto;
import com.imgood.textech.webae.dto.QuestProgressDto;
import com.imgood.textech.webae.dto.QuestProgressEntryDto;
import com.imgood.textech.webae.dto.QuestRelationDto;
import com.imgood.textech.webae.dto.QuestRewardDto;
import com.imgood.textech.webae.dto.QuestSearchHitDto;
import com.imgood.textech.webae.dto.QuestTaskDto;
import com.imgood.textech.webae.icon.IconItemId;
import com.imgood.textech.webae.quest.QuestFluidIconResolver;
import com.imgood.textech.webae.recipe.RecipeItemEntries;

/**
 * Reflection bridge to BetterQuesting GTNH fork. All BQ types stay optional at compile time.
 */
public final class BqApiFacade {

    private static final String QUESTING_API = "betterquesting.api.api.QuestingAPI";
    private static final String API_REFERENCE = "betterquesting.api.api.ApiReference";
    private static final String NATIVE_PROPS = "betterquesting.api.properties.NativeProps";

    private static Object questDb;
    private static Object lineDb;
    private static Object nativeNameProp;
    private static Object nativeDescProp;
    private static Object nativeIconProp;
    private static Object nativeMainProp;
    private static Object nativeSilentProp;
    private static Object nativeRepeatTimeProp;
    private static boolean initAttempted;
    private static boolean initOk;

    private BqApiFacade() {}

    public static UUID getQuestingUuid(EntityPlayer player) {
        if (player == null || !ensureInit()) {
            return null;
        }
        try {
            Method m = Class.forName(QUESTING_API)
                .getMethod("getQuestingUUID", EntityPlayer.class);
            Object result = m.invoke(null, player);
            return result instanceof UUID ? (UUID) result : null;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] getQuestingUUID failed: {}", t.toString());
            return player.getGameProfile()
                .getId();
        }
    }

    public static int countLines() {
        if (!ensureInit() || lineDb == null) {
            return 0;
        }
        return sizeOfDb(lineDb);
    }

    public static List<QuestLineSummaryDto> collectLines() {
        List<QuestLineSummaryDto> out = new ArrayList<QuestLineSummaryDto>();
        if (!ensureInit() || lineDb == null) {
            return out;
        }
        int order = 0;
        for (Map.Entry<UUID, Object> entry : entriesOfDb(lineDb)) {
            UUID lineId = entry.getKey();
            Object line = entry.getValue();
            if (lineId == null || line == null) {
                continue;
            }
            QuestLineSummaryDto dto = new QuestLineSummaryDto();
            dto.lineId = lineId.toString();
            dto.order = getOrderIndex(lineDb, lineId, order++);
            dto.name = localize(readLineName(line));
            dto.description = localize(readLineDescription(line));
            fillIcon(dto, readProperty(line, nativeIconProp));
            dto.questCount = sizeOfDb(line);
            out.add(dto);
        }
        return out;
    }

    public static QuestLineGraphDto collectLineGraph(UUID lineUuid, EntityPlayerMP player) {
        QuestLineGraphDto graph = new QuestLineGraphDto();
        if (lineUuid == null || !ensureInit()) {
            return graph;
        }
        Object line = getFromDb(lineDb, lineUuid);
        if (line == null) {
            return graph;
        }
        graph.lineId = lineUuid.toString();
        graph.name = localize(readLineName(line));
        UUID questingUuid = getQuestingUuid(player);

        for (Map.Entry<UUID, Object> entry : entriesOfDb(line)) {
            UUID questId = entry.getKey();
            Object questEntry = entry.getValue();
            Object quest = getFromDb(questDb, questId);
            if (questId == null || questEntry == null || quest == null) {
                continue;
            }
            QuestLineNodeDto node = new QuestLineNodeDto();
            node.questId = questId.toString();
            node.name = localize(readQuestName(quest));
            node.x = invokeInt(questEntry, "getPosX");
            node.y = invokeInt(questEntry, "getPosY");
            node.sizeX = Math.max(16, invokeInt(questEntry, "getSizeX"));
            node.sizeY = Math.max(16, invokeInt(questEntry, "getSizeY"));
            node.state = readQuestState(quest, player, questingUuid);
            node.mainQuest = readBooleanProperty(quest, nativeMainProp);
            node.canSubmit = canSubmitQuest(quest, player);
            fillIcon(node, readProperty(quest, nativeIconProp));
            graph.nodes.add(node);

            Set<UUID> reqs = readRequirements(quest);
            if (reqs != null) {
                for (UUID reqId : reqs) {
                    if (reqId == null) {
                        continue;
                    }
                    QuestLineEdgeDto edge = new QuestLineEdgeDto();
                    edge.fromQuestId = reqId.toString();
                    edge.toQuestId = questId.toString();
                    edge.requirementType = readRequirementType(quest, reqId);
                    graph.edges.add(edge);
                }
            }
        }

        // Attach ghost nodes for cross-line prerequisites so edges are not dropped in the UI.
        java.util.HashSet<String> presentIds = new java.util.HashSet<String>();
        for (QuestLineNodeDto n : graph.nodes) {
            if (n != null && n.questId != null) {
                presentIds.add(n.questId);
            }
        }
        java.util.HashSet<String> ghostAdded = new java.util.HashSet<String>();
        for (QuestLineEdgeDto edge : graph.edges) {
            if (edge == null || edge.fromQuestId == null
                || presentIds.contains(edge.fromQuestId)
                || ghostAdded.contains(edge.fromQuestId)) {
                continue;
            }
            UUID reqUuid;
            try {
                reqUuid = UUID.fromString(edge.fromQuestId);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Object reqQuest = getFromDb(questDb, reqUuid);
            if (reqQuest == null) {
                continue;
            }
            QuestLineNodeDto ghost = new QuestLineNodeDto();
            ghost.questId = edge.fromQuestId;
            ghost.name = localize(readQuestName(reqQuest));
            ghost.state = readQuestState(reqQuest, player, questingUuid);
            ghost.mainQuest = readBooleanProperty(reqQuest, nativeMainProp);
            ghost.canSubmit = false;
            ghost.ghost = true;
            ghost.sizeX = 24;
            ghost.sizeY = 24;
            ghost.sourceLineId = findLineIdForQuest(reqUuid);
            // Place near the first dependent node that references this prereq.
            for (QuestLineNodeDto n : graph.nodes) {
                if (n != null && edge.toQuestId != null && edge.toQuestId.equals(n.questId)) {
                    ghost.x = n.x - 48;
                    ghost.y = n.y;
                    break;
                }
            }
            fillIcon(ghost, readProperty(reqQuest, nativeIconProp));
            graph.nodes.add(ghost);
            presentIds.add(ghost.questId);
            ghostAdded.add(ghost.questId);
        }
        return graph;
    }

    public static QuestDetailDto collectQuestDetail(UUID questId, EntityPlayerMP player) {
        QuestDetailDto dto = new QuestDetailDto();
        if (questId == null || !ensureInit()) {
            return dto;
        }
        Object quest = getFromDb(questDb, questId);
        if (quest == null) {
            return dto;
        }
        UUID questingUuid = getQuestingUuid(player);
        dto.questId = questId.toString();
        dto.name = localize(readQuestName(quest));
        dto.description = localize(readQuestDescription(quest));
        dto.state = readQuestState(quest, player, questingUuid);
        dto.canSubmit = canSubmitQuest(quest, player);
        dto.canClaim = invokeBoolean(quest, "canClaimBasically", player);
        dto.hasClaimed = questingUuid != null && invokeBooleanUuid(quest, "hasClaimed", questingUuid);
        dto.mainQuest = readBooleanProperty(quest, nativeMainProp);
        dto.silent = readBooleanProperty(quest, nativeSilentProp);
        dto.repeatable = readIntProperty(quest, nativeRepeatTimeProp) >= 0;
        fillIcon(dto, readProperty(quest, nativeIconProp));

        Set<UUID> reqs = readRequirements(quest);
        if (reqs != null) {
            for (UUID req : reqs) {
                if (req != null) {
                    dto.requirementQuestIds.add(req.toString());
                    Object reqQuest = getFromDb(questDb, req);
                    QuestRelationDto prereq = buildQuestRelation(
                        req,
                        reqQuest,
                        player,
                        questingUuid,
                        readRequirementType(quest, req));
                    dto.prerequisites.add(prereq);
                }
            }
        }

        for (Map.Entry<UUID, Object> entry : entriesOfDb(questDb)) {
            UUID otherId = entry.getKey();
            Object otherQuest = entry.getValue();
            if (otherId == null || otherQuest == null || questId.equals(otherId)) {
                continue;
            }
            Set<UUID> otherReqs = readRequirements(otherQuest);
            if (otherReqs != null && otherReqs.contains(questId)) {
                dto.dependents.add(
                    buildQuestRelation(
                        otherId,
                        otherQuest,
                        player,
                        questingUuid,
                        readRequirementType(otherQuest, questId)));
            }
        }

        Object tasksDb = invoke(quest, "getTasks");
        if (tasksDb != null) {
            int idx = 0;
            for (IndexedEntry taskEntry : entriesOfIndexedDb(tasksDb)) {
                QuestTaskDto task = com.imgood.textech.webae.quest.QuestTaskDeserializer
                    .deserialize(idx++, taskEntry.id, taskEntry.value, questingUuid);
                if (task != null) {
                    dto.tasks.add(task);
                }
            }
        }

        Object rewardsDb = invoke(quest, "getRewards");
        if (rewardsDb != null) {
            for (IndexedEntry rewardEntry : entriesOfIndexedDb(rewardsDb)) {
                appendRewardDtos(dto.rewards, rewardEntry.id, rewardEntry.value);
            }
        }
        fillWebClaimFlags(dto);
        return dto;
    }

    /**
     * Marks {@link QuestDetailDto#webClaimable} when the quest is unclaimed and every reward is a
     * deterministic item or choice factory.
     */
    private static void fillWebClaimFlags(QuestDetailDto dto) {
        if (dto == null) {
            return;
        }
        dto.webClaimable = false;
        dto.claimBlockReason = "";
        if (!Config.webQuestClaimEnabled) {
            dto.claimBlockReason = "claim_disabled";
            return;
        }
        boolean unclaimed = "UNCLAIMED".equals(dto.state) || dto.canClaim;
        if (!unclaimed || dto.hasClaimed) {
            dto.claimBlockReason = "not_unclaimed";
            return;
        }
        if (dto.rewards == null || dto.rewards.isEmpty()) {
            // No rewards → BQ treats as already claimed; nothing for WebAE to deliver.
            dto.claimBlockReason = "no_rewards";
            return;
        }
        boolean anyUnsupported = false;
        boolean anyEmptyItem = false;
        for (QuestRewardDto reward : dto.rewards) {
            if (reward == null) {
                continue;
            }
            if (!reward.webClaimable || "unsupported".equals(reward.kind)) {
                anyUnsupported = true;
                break;
            }
            if (("item".equals(reward.kind) || reward.choiceOption)
                && (reward.registryName == null || reward.registryName.isEmpty() || reward.amount <= 0)) {
                anyEmptyItem = true;
            }
        }
        if (anyUnsupported) {
            dto.claimBlockReason = "non_item_reward";
            return;
        }
        if (anyEmptyItem) {
            dto.claimBlockReason = "empty_item";
            return;
        }
        dto.webClaimable = true;
    }

    public static Object getQuest(UUID questId) {
        if (questId == null || !ensureInit()) {
            return null;
        }
        return getFromDb(questDb, questId);
    }

    public static boolean canClaimQuest(Object quest, EntityPlayerMP player) {
        if (quest == null || player == null) {
            return false;
        }
        try {
            Method m = quest.getClass()
                .getMethod("canClaim", EntityPlayer.class, boolean.class);
            Object result = m.invoke(quest, player, Boolean.FALSE);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {}
        return invokeBoolean(quest, "canClaimBasically", player);
    }

    public static boolean claimQuestRewards(Object quest, EntityPlayerMP player) {
        if (quest == null || player == null) {
            return false;
        }
        try {
            Method m = quest.getClass()
                .getMethod("claimReward", EntityPlayer.class, boolean.class);
            m.invoke(quest, player, Boolean.FALSE);
            return true;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] claimReward failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Applies choice selections via {@code RewardChoice#setSelection(UUID, int)}.
     *
     * @return false if any listed reward id is missing or not a choice reward
     */
    public static boolean applyChoiceSelections(Object quest, UUID questingUuid, Map<String, Integer> selections) {
        if (quest == null || questingUuid == null) {
            return false;
        }
        if (selections == null || selections.isEmpty()) {
            return true;
        }
        Object rewardsDb = invoke(quest, "getRewards");
        if (rewardsDb == null) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : selections.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                return false;
            }
            int rewardId;
            try {
                rewardId = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                return false;
            }
            Object reward = getRewardValue(rewardsDb, rewardId);
            if (reward == null || !isChoiceFactory(readFactoryId(reward))) {
                return false;
            }
            if (!setChoiceSelection(
                reward,
                questingUuid,
                entry.getValue()
                    .intValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Expected item stacks after choice selections are applied. Returns {@code null} if any reward is
     * not a supported item/choice factory.
     */
    public static List<ItemStack> collectExpectedClaimStacks(Object quest, UUID questingUuid) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (quest == null) {
            return null;
        }
        Object rewardsDb = invoke(quest, "getRewards");
        if (rewardsDb == null) {
            return out;
        }
        for (IndexedEntry entry : entriesOfIndexedDb(rewardsDb)) {
            Object reward = entry.value;
            if (reward == null) {
                continue;
            }
            String factoryId = readFactoryId(reward);
            if (isItemFactory(factoryId)) {
                List<ItemStack> stacks = extractRewardItemStacks(reward, "items");
                if (stacks.isEmpty()) {
                    stacks = extractRewardItemStacks(reward, "rewards");
                }
                if (stacks.isEmpty()) {
                    return null;
                }
                out.addAll(stacks);
            } else if (isChoiceFactory(factoryId)) {
                int selected = getChoiceSelection(reward, questingUuid);
                List<ItemStack> choices = extractRewardItemStacks(reward, "choices");
                if (selected < 0 || selected >= choices.size()) {
                    return null;
                }
                ItemStack picked = choices.get(selected);
                if (picked == null) {
                    return null;
                }
                out.add(picked.copy());
            } else {
                return null;
            }
        }
        return out;
    }

    private static Object getRewardValue(Object rewardsDb, int rewardId) {
        if (rewardsDb == null) {
            return null;
        }
        try {
            Method getValue = rewardsDb.getClass()
                .getMethod("getValue", int.class);
            return getValue.invoke(rewardsDb, Integer.valueOf(rewardId));
        } catch (Throwable ignored) {}
        for (IndexedEntry entry : entriesOfIndexedDb(rewardsDb)) {
            if (entry.id != null && entry.id.equals(String.valueOf(rewardId))) {
                return entry.value;
            }
        }
        return null;
    }

    private static boolean setChoiceSelection(Object reward, UUID questingUuid, int index) {
        try {
            Method m = reward.getClass()
                .getMethod("setSelection", UUID.class, int.class);
            m.invoke(reward, questingUuid, Integer.valueOf(index));
            return true;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] setSelection failed: {}", t.toString());
            return false;
        }
    }

    private static int getChoiceSelection(Object reward, UUID questingUuid) {
        try {
            Method m = reward.getClass()
                .getMethod("getSelecton", UUID.class);
            Object result = m.invoke(reward, questingUuid);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {}
        try {
            Method m = reward.getClass()
                .getMethod("getSelection", UUID.class);
            Object result = m.invoke(reward, questingUuid);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static boolean isItemFactory(String factoryId) {
        return "bq_standard:item".equals(factoryId);
    }

    private static boolean isChoiceFactory(String factoryId) {
        return "bq_standard:choice".equals(factoryId);
    }

    public static QuestProgressDto collectProgress(EntityPlayerMP player) {
        QuestProgressDto dto = new QuestProgressDto();
        if (!ensureInit() || player == null) {
            return dto;
        }
        UUID questingUuid = getQuestingUuid(player);
        dto.questingUuid = questingUuid != null ? questingUuid.toString() : "";
        dto.updatedAt = System.currentTimeMillis();

        // Build questId → lineId map in one pass over lineDb.
        java.util.HashMap<String, String> questToLine = new java.util.HashMap<String, String>();
        if (lineDb != null) {
            for (Map.Entry<UUID, Object> lineEntry : entriesOfDb(lineDb)) {
                UUID lineId = lineEntry.getKey();
                Object line = lineEntry.getValue();
                if (lineId == null || line == null) {
                    continue;
                }
                String lineIdStr = lineId.toString();
                for (Map.Entry<UUID, Object> questEntry : entriesOfDb(line)) {
                    UUID questId = questEntry.getKey();
                    if (questId != null) {
                        questToLine.put(questId.toString(), lineIdStr);
                    }
                }
            }
        }

        dto.lineSubmittableCounts = new java.util.HashMap<String, Integer>();
        dto.lineCompletedCounts = new java.util.HashMap<String, Integer>();
        for (Map.Entry<UUID, Object> entry : entriesOfDb(questDb)) {
            UUID questId = entry.getKey();
            Object quest = entry.getValue();
            if (questId == null || quest == null) {
                continue;
            }
            QuestProgressEntryDto pe = new QuestProgressEntryDto();
            pe.questId = questId.toString();
            pe.state = readQuestState(quest, player, questingUuid);
            pe.canSubmit = canSubmitQuest(quest, player);
            dto.entries.add(pe);

            String lineId = questToLine.get(pe.questId);
            if (lineId == null) {
                continue;
            }
            // Accumulate per-line submittable quest counts in the same pass.
            if (pe.canSubmit) {
                Integer prev = dto.lineSubmittableCounts.get(lineId);
                dto.lineSubmittableCounts.put(lineId, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
            }
            // COMPLETED + UNCLAIMED both count as finished objectives.
            if ("COMPLETED".equals(pe.state) || "UNCLAIMED".equals(pe.state)) {
                Integer prevDone = dto.lineCompletedCounts.get(lineId);
                dto.lineCompletedCounts.put(lineId, Integer.valueOf(prevDone == null ? 1 : prevDone.intValue() + 1));
            }
        }
        return dto;
    }

    public static List<QuestSearchHitDto> search(String query, EntityPlayerMP player) {
        List<QuestSearchHitDto> hits = new ArrayList<QuestSearchHitDto>();
        if (query == null || query.trim()
            .isEmpty() || !ensureInit()) {
            return hits;
        }
        String q = query.trim()
            .toLowerCase(Locale.ROOT);
        UUID questingUuid = getQuestingUuid(player);
        for (Map.Entry<UUID, Object> lineEntry : entriesOfDb(lineDb)) {
            UUID lineId = lineEntry.getKey();
            Object line = lineEntry.getValue();
            if (lineId == null || line == null) {
                continue;
            }
            String lineName = localize(readLineName(line));
            for (Map.Entry<UUID, Object> questEntry : entriesOfDb(line)) {
                UUID questId = questEntry.getKey();
                Object quest = getFromDb(questDb, questId);
                if (questId == null || quest == null) {
                    continue;
                }
                String questName = localize(readQuestName(quest));
                if (!questName.toLowerCase(Locale.ROOT)
                    .contains(q)
                    && !lineName.toLowerCase(Locale.ROOT)
                        .contains(q)) {
                    if (!matchesTaskText(quest, q)) {
                        continue;
                    }
                }
                QuestSearchHitDto hit = new QuestSearchHitDto();
                hit.questId = questId.toString();
                hit.lineId = lineId.toString();
                hit.lineName = lineName;
                hit.questName = questName;
                hit.state = readQuestState(quest, player, questingUuid);
                hits.add(hit);
                if (hits.size() >= 50) {
                    return hits;
                }
            }
        }
        return hits;
    }

    public static void detectQuest(Object quest, EntityPlayerMP player) {
        if (quest == null || player == null) {
            return;
        }
        invokeVoid(quest, "detect", player);
    }

    public static void updateQuest(Object quest, EntityPlayerMP player) {
        if (quest == null || player == null) {
            return;
        }
        invokeVoid(quest, "update", player);
    }

    public static boolean submitItemTask(Object task, EntityPlayerMP player, UUID questingUuid, ItemStack stack) {
        return submitItemTask(task, player, questingUuid, null, null, stack);
    }

    /**
     * Submit item into a consume-style BQ task. Prefers BQ 3.8.72
     * {@code submitItem(UUID, Map.Entry, ItemStack)}; falls back to legacy signatures.
     * On full consume, also {@link #finalizeConsumeTaskIfReady} (BQ submitItem updates progress
     * but does not always mark the task complete until inventory detect).
     */
    public static boolean submitItemTask(Object task, EntityPlayerMP player, UUID questingUuid, UUID questId,
        Object quest, ItemStack stack) {
        ItemStack leftover = submitItemTaskLeftover(task, player, questingUuid, questId, quest, stack);
        return leftover == null || leftover.stackSize <= 0;
    }

    /**
     * Same as {@link #submitItemTask} but returns BQ leftover (null = fully consumed).
     * Callers that removed the stack from escrow/AE must reinject any leftover.
     */
    public static ItemStack submitItemTaskLeftover(Object task, EntityPlayerMP player, UUID questingUuid, UUID questId,
        Object quest, ItemStack stack) {
        if (task == null || stack == null) {
            return stack;
        }
        UUID owner = questingUuid != null ? questingUuid : (player != null ? getQuestingUuid(player) : null);
        if (owner == null) {
            return stack;
        }
        try {
            Map.Entry<?, ?> questEntry = questEntryOf(questId, quest);
            if (questEntry != null) {
                Method m = findMethod(task.getClass(), "submitItem", UUID.class, Map.Entry.class, ItemStack.class);
                if (m != null) {
                    Object leftover = m.invoke(task, owner, questEntry, stack.copy());
                    ItemStack left = leftover instanceof ItemStack ? (ItemStack) leftover : null;
                    if (left == null || left.stackSize <= 0) {
                        finalizeConsumeTaskIfReady(task, owner);
                        return null;
                    }
                    return left;
                }
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] submitItem(UUID,Entry) failed: {}", t.toString());
        }
        try {
            Method m = task.getClass()
                .getMethod("submitItem", EntityPlayer.class, UUID.class, ItemStack.class);
            Object result = m.invoke(task, player, owner, stack);
            if (result instanceof Boolean && ((Boolean) result).booleanValue()) {
                finalizeConsumeTaskIfReady(task, owner);
                return null;
            }
            return stack.copy();
        } catch (NoSuchMethodException e) {
            try {
                Method alt = task.getClass()
                    .getMethod("submitItem", EntityPlayerMP.class, UUID.class, ItemStack.class);
                Object result = alt.invoke(task, player, owner, stack);
                if (result instanceof Boolean && ((Boolean) result).booleanValue()) {
                    finalizeConsumeTaskIfReady(task, owner);
                    return null;
                }
                return stack.copy();
            } catch (Throwable ignored) {
                return stack.copy();
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] submitItem failed: {}", t.toString());
            return stack.copy();
        }
    }

    /**
     * After consume submit updated progress, mark complete when all required amounts are met.
     * BQ's {@code submitItem}/{@code submitFluid} often only bump progress arrays.
     */
    public static void finalizeConsumeTaskIfReady(Object task, UUID questingUuid) {
        if (task == null || questingUuid == null || isTaskComplete(task, questingUuid)) {
            return;
        }
        try {
            Method getProgress = findMethod(task.getClass(), "getUsersProgress", UUID.class);
            if (getProgress == null) {
                return;
            }
            Object progress = getProgress.invoke(task, questingUuid);
            if (!(progress instanceof int[])) {
                return;
            }
            int[] arr = (int[]) progress;
            int[] required = readRequiredAmounts(task);
            if (required == null || required.length == 0) {
                return;
            }
            for (int i = 0; i < required.length; i++) {
                int have = i < arr.length ? arr[i] : 0;
                if (have < required[i]) {
                    return;
                }
            }
            Method setComplete = findMethod(task.getClass(), "setComplete", UUID.class);
            if (setComplete != null) {
                setComplete.invoke(task, questingUuid);
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] finalizeConsumeTaskIfReady failed: {}", t.toString());
        }
    }

    public static boolean submitFluidTask(Object task, EntityPlayerMP player, UUID questingUuid, FluidStack fluid) {
        return submitFluidTask(task, player, questingUuid, null, null, fluid);
    }

    /**
     * Submit fluid into a consume-style BQ task. Prefers BQ 3.8.72
     * {@code submitFluid(UUID, Map.Entry, FluidStack)}.
     */
    public static boolean submitFluidTask(Object task, EntityPlayerMP player, UUID questingUuid, UUID questId,
        Object quest, FluidStack fluid) {
        FluidStack leftover = submitFluidTaskLeftover(task, player, questingUuid, questId, quest, fluid);
        return leftover == null || leftover.amount <= 0;
    }

    /**
     * Same as {@link #submitFluidTask} but returns BQ leftover (null = fully consumed).
     */
    public static FluidStack submitFluidTaskLeftover(Object task, EntityPlayerMP player, UUID questingUuid,
        UUID questId, Object quest, FluidStack fluid) {
        if (task == null || fluid == null) {
            return fluid;
        }
        UUID owner = questingUuid != null ? questingUuid : (player != null ? getQuestingUuid(player) : null);
        if (owner == null) {
            return fluid;
        }
        try {
            Map.Entry<?, ?> questEntry = questEntryOf(questId, quest);
            if (questEntry != null) {
                Method m = findMethod(task.getClass(), "submitFluid", UUID.class, Map.Entry.class, FluidStack.class);
                if (m != null) {
                    Object leftover = m.invoke(task, owner, questEntry, fluid.copy());
                    FluidStack left = leftover instanceof FluidStack ? (FluidStack) leftover : null;
                    if (left == null || left.amount <= 0) {
                        finalizeConsumeTaskIfReady(task, owner);
                        return null;
                    }
                    return left;
                }
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] submitFluid(UUID,Entry) failed: {}", t.toString());
        }
        try {
            Method m = task.getClass()
                .getMethod("submitFluid", EntityPlayer.class, UUID.class, FluidStack.class);
            Object result = m.invoke(task, player, owner, fluid);
            if (result instanceof Boolean && ((Boolean) result).booleanValue()) {
                finalizeConsumeTaskIfReady(task, owner);
                return null;
            }
            return fluid.copy();
        } catch (NoSuchMethodException e) {
            try {
                Method alt = task.getClass()
                    .getMethod("submitFluid", EntityPlayerMP.class, UUID.class, FluidStack.class);
                Object result = alt.invoke(task, player, owner, fluid);
                if (result instanceof Boolean && ((Boolean) result).booleanValue()) {
                    finalizeConsumeTaskIfReady(task, owner);
                    return null;
                }
                return fluid.copy();
            } catch (Throwable ignored) {
                return fluid.copy();
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] submitFluid failed: {}", t.toString());
            return fluid.copy();
        }
    }

    /**
     * Complete a non-consuming Retrieval / fluid hold task without {@code detect(player)} / QuestCache.
     * Prefers {@code retrieveItems}/{@code retrieveFluids}; falls back to progress + {@code setComplete}.
     */
    public static boolean completeRetrievalTask(Object task, EntityPlayerMP player, UUID questingUuid, UUID questId,
        Object quest, ItemStack item, FluidStack fluid) {
        if (task == null || questingUuid == null) {
            return false;
        }
        if (isTaskComplete(task, questingUuid)) {
            return true;
        }
        Map.Entry<?, ?> questEntry = questEntryOf(questId, quest);
        Object participantInfo = buildParticipantInfo(player);
        boolean updated = false;

        if (item != null && participantInfo != null && questEntry != null) {
            try {
                Method m = findMethod(
                    task.getClass(),
                    "retrieveItems",
                    Class.forName("betterquesting.api2.utils.ParticipantInfo"),
                    Map.Entry.class,
                    ItemStack[].class);
                if (m != null) {
                    m.invoke(task, participantInfo, questEntry, new ItemStack[] { item.copy() });
                    updated = true;
                }
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE Quest] retrieveItems failed: {}", t.toString());
            }
        }

        if (fluid != null && participantInfo != null && questEntry != null) {
            try {
                Method m = findMethod(
                    task.getClass(),
                    "retrieveFluids",
                    Class.forName("betterquesting.api2.utils.ParticipantInfo"),
                    Map.Entry.class,
                    FluidStack[].class);
                if (m != null) {
                    m.invoke(task, participantInfo, questEntry, new FluidStack[] { fluid.copy() });
                    updated = true;
                }
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.warn("[WebAE Quest] retrieveFluids failed: {}", t.toString());
            }
        }

        if (isTaskComplete(task, questingUuid)) {
            return true;
        }

        // consume=true tasks must use submitItem/submitFluid; never force-complete.
        if (isTaskConsumeTrue(task)) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE Quest] completeRetrieval refused for consume=true task {}",
                task.getClass()
                    .getSimpleName());
            return false;
        }

        // Fallback: write full progress + setComplete(UUID) without inventory detect.
        if (!forceCompleteRetrievalProgress(task, questingUuid, item, fluid)) {
            if (!updated) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE Quest] completeRetrieval failed for task {}",
                    task.getClass()
                        .getSimpleName());
            }
            return isTaskComplete(task, questingUuid);
        }
        return isTaskComplete(task, questingUuid);
    }

    /** True when BQ task has {@code consume=true} (item/fluid submit, not hold-detect). */
    private static boolean isTaskConsumeTrue(Object task) {
        if (task == null) {
            return false;
        }
        try {
            Field f = findDeclaredField(task.getClass(), "consume");
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(task);
                if (val instanceof Boolean) {
                    return ((Boolean) val).booleanValue();
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean forceCompleteRetrievalProgress(Object task, UUID questingUuid, ItemStack item,
        FluidStack fluid) {
        try {
            Method setComplete = findMethod(task.getClass(), "setComplete", UUID.class);
            if (setComplete == null) {
                return false;
            }
            try {
                Method getProgress = findMethod(task.getClass(), "getUsersProgress", UUID.class);
                Method setProgress = findMethodByName(task.getClass(), "setUserProgress", 2);
                if (getProgress != null && setProgress != null) {
                    Object progress = getProgress.invoke(task, questingUuid);
                    if (progress instanceof int[]) {
                        int[] arr = (int[]) ((int[]) progress).clone();
                        int[] required = readRequiredAmounts(task);
                        for (int i = 0; i < arr.length; i++) {
                            int need = required != null && i < required.length ? required[i] : Integer.MAX_VALUE / 4;
                            if (arr[i] < need) {
                                arr[i] = need;
                            }
                        }
                        setProgress.invoke(task, questingUuid, arr);
                    }
                }
            } catch (Throwable ignored) {}
            setComplete.invoke(task, questingUuid);
            return true;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] forceCompleteRetrieval failed: {}", t.toString());
            return false;
        }
    }

    private static int[] readRequiredAmounts(Object task) {
        try {
            Field requiredItems = findDeclaredField(task.getClass(), "requiredItems");
            if (requiredItems != null) {
                requiredItems.setAccessible(true);
                Object list = requiredItems.get(task);
                if (list instanceof List) {
                    List<?> req = (List<?>) list;
                    int[] amounts = new int[req.size()];
                    for (int i = 0; i < req.size(); i++) {
                        Object big = req.get(i);
                        amounts[i] = bigStackSize(big);
                    }
                    return amounts;
                }
            }
            Field requiredFluids = findDeclaredField(task.getClass(), "requiredFluids");
            if (requiredFluids != null) {
                requiredFluids.setAccessible(true);
                Object list = requiredFluids.get(task);
                if (list instanceof List) {
                    List<?> req = (List<?>) list;
                    int[] amounts = new int[req.size()];
                    for (int i = 0; i < req.size(); i++) {
                        Object fs = req.get(i);
                        if (fs instanceof FluidStack) {
                            amounts[i] = ((FluidStack) fs).amount;
                        }
                    }
                    return amounts;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int bigStackSize(Object bigStack) {
        if (bigStack == null) {
            return 1;
        }
        try {
            Field f = findDeclaredField(bigStack.getClass(), "stackSize");
            if (f != null) {
                f.setAccessible(true);
                Object val = f.get(bigStack);
                if (val instanceof Number) {
                    return Math.max(1, ((Number) val).intValue());
                }
            }
        } catch (Throwable ignored) {}
        return 1;
    }

    private static Field findDeclaredField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethodByName(Class<?> type, String name, int paramCount) {
        Class<?> current = type;
        while (current != null) {
            Method[] methods = current.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                Method m = methods[i];
                if (name.equals(m.getName()) && m.getParameterTypes().length == paramCount) {
                    m.setAccessible(true);
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        for (Method m : type.getMethods()) {
            if (name.equals(m.getName()) && m.getParameterTypes().length == paramCount) {
                return m;
            }
        }
        return null;
    }

    private static Object buildParticipantInfo(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("betterquesting.api2.utils.ParticipantInfo");
            return clazz.getConstructor(EntityPlayer.class)
                .newInstance(player);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] ParticipantInfo create failed: {}", t.toString());
            return null;
        }
    }

    private static Map.Entry<?, ?> questEntryOf(UUID questId, Object quest) {
        if (questId == null || quest == null) {
            return null;
        }
        return new java.util.AbstractMap.SimpleEntry<UUID, Object>(questId, quest);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method m = current.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                try {
                    return current.getMethod(name, params);
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
        }
        // Interface / overload scan (Map.Entry erasure).
        for (Method m : type.getMethods()) {
            if (!name.equals(m.getName()) || m.getParameterTypes().length != params.length) {
                continue;
            }
            boolean match = true;
            Class<?>[] actual = m.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (!actual[i].isAssignableFrom(params[i]) && !params[i].isAssignableFrom(actual[i])) {
                    // Allow Map.Entry vs concrete entry.
                    if (!(Map.Entry.class.isAssignableFrom(actual[i]) && Map.Entry.class.isAssignableFrom(params[i]))) {
                        match = false;
                        break;
                    }
                }
            }
            if (match) {
                return m;
            }
        }
        return null;
    }

    /**
     * Ordered task/reward (or UUID) database entry. TaskStorage/RewardStorage use int IDs via DBEntry;
     * quest/line DBs use UUID keys exposed as {@link #id} strings.
     */
    public static final class IndexedEntry {

        public final String id;
        public final Object value;

        public IndexedEntry(String id, Object value) {
            this.id = id != null ? id : "";
            this.value = value;
        }
    }

    public static List<IndexedEntry> entriesOfTasks(Object quest) {
        Object tasksDb = invoke(quest, "getTasks");
        if (tasksDb == null) {
            return new ArrayList<IndexedEntry>();
        }
        return entriesOfIndexedDb(tasksDb);
    }

    private static boolean ensureInit() {
        if (!BqCompat.isModLoaded()) {
            return false;
        }
        if (initAttempted) {
            return initOk;
        }
        initAttempted = true;
        try {
            Class<?> apiKeyClass = Class.forName("betterquesting.api.api.ApiKey");
            Class<?> questingApi = Class.forName(QUESTING_API);
            Method getApi = questingApi.getMethod("getAPI", apiKeyClass);
            Class<?> apiRef = Class.forName(API_REFERENCE);
            Object questDbKey = apiRef.getField("QUEST_DB")
                .get(null);
            Object lineDbKey = apiRef.getField("LINE_DB")
                .get(null);
            questDb = getApi.invoke(null, questDbKey);
            lineDb = getApi.invoke(null, lineDbKey);

            Class<?> nativeProps = Class.forName(NATIVE_PROPS);
            nativeNameProp = nativeProps.getField("NAME")
                .get(null);
            nativeDescProp = nativeProps.getField("DESC")
                .get(null);
            nativeIconProp = nativeProps.getField("ICON")
                .get(null);
            nativeMainProp = nativeProps.getField("MAIN")
                .get(null);
            nativeSilentProp = nativeProps.getField("SILENT")
                .get(null);
            nativeRepeatTimeProp = nativeProps.getField("REPEAT_TIME")
                .get(null);
            initOk = questDb != null && lineDb != null;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] BQ API init failed: {}", t.toString());
            initOk = false;
        }
        return initOk;
    }

    private static int sizeOfDb(Object db) {
        if (db instanceof Map) {
            return ((Map<?, ?>) db).size();
        }
        try {
            Method size = db.getClass()
                .getMethod("size");
            Object result = size.invoke(db);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {}
        return entriesOfDb(db).size();
    }

    /**
     * Prefer BQ SimpleDatabase {@code getEntries()} (int-keyed DBEntry) used by TaskStorage/RewardStorage;
     * fall back to UUID Map databases used by quest/line DBs.
     */
    private static List<IndexedEntry> entriesOfIndexedDb(Object db) {
        List<IndexedEntry> out = new ArrayList<IndexedEntry>();
        if (db == null) {
            return out;
        }
        try {
            Method getEntries = db.getClass()
                .getMethod("getEntries");
            Object list = getEntries.invoke(db);
            if (list instanceof List) {
                for (Object entry : (List<?>) list) {
                    addIndexedDbEntry(out, entry);
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
        } catch (Throwable ignored) {}
        for (Map.Entry<UUID, Object> e : entriesOfDb(db)) {
            if (e.getKey() != null) {
                out.add(
                    new IndexedEntry(
                        e.getKey()
                            .toString(),
                        e.getValue()));
            }
        }
        return out;
    }

    private static void addIndexedDbEntry(List<IndexedEntry> out, Object raw) {
        if (raw == null) {
            return;
        }
        try {
            Method getId = raw.getClass()
                .getMethod("getID");
            Method getValue = raw.getClass()
                .getMethod("getValue");
            Object id = getId.invoke(raw);
            Object value = getValue.invoke(raw);
            if (id instanceof Number && value != null) {
                out.add(new IndexedEntry(String.valueOf(((Number) id).intValue()), value));
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static List<Map.Entry<UUID, Object>> entriesOfDb(Object db) {
        List<Map.Entry<UUID, Object>> out = new ArrayList<Map.Entry<UUID, Object>>();
        if (db == null) {
            return out;
        }
        try {
            Method getOrdered = db.getClass()
                .getMethod("getOrderedEntries");
            Object list = getOrdered.invoke(db);
            if (list instanceof List) {
                for (Object entry : (List<?>) list) {
                    addUuidEntry(out, entry);
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Method ordered = db.getClass()
                .getMethod("orderedEntries");
            Object stream = ordered.invoke(db);
            if (stream != null) {
                Method iterator = stream.getClass()
                    .getMethod("iterator");
                Iterator<?> it = (Iterator<?>) iterator.invoke(stream);
                while (it.hasNext()) {
                    addUuidEntry(out, it.next());
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
        } catch (Throwable ignored) {}
        if (db instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) db;
            for (Object raw : map.entrySet()) {
                addUuidEntry(out, raw);
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        try {
            Method entrySet = db.getClass()
                .getMethod("entrySet");
            Object set = entrySet.invoke(db);
            if (set instanceof Set) {
                for (Object raw : (Set<?>) set) {
                    addUuidEntry(out, raw);
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static void addUuidEntry(List<Map.Entry<UUID, Object>> out, Object raw) {
        if (!(raw instanceof Map.Entry)) {
            return;
        }
        Map.Entry<?, ?> e = (Map.Entry<?, ?>) raw;
        if (e.getKey() instanceof UUID) {
            out.add(new java.util.AbstractMap.SimpleEntry<UUID, Object>((UUID) e.getKey(), e.getValue()));
        }
    }

    private static int getOrderIndex(Object db, UUID id, int fallback) {
        if (db == null || id == null) {
            return fallback;
        }
        try {
            Method m = db.getClass()
                .getMethod("getOrderIndex", UUID.class);
            Object result = m.invoke(db, id);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static Object getFromDb(Object db, UUID id) {
        if (db == null || id == null) {
            return null;
        }
        if (db instanceof Map) {
            return ((Map<?, ?>) db).get(id);
        }
        try {
            Method get = db.getClass()
                .getMethod("get", Object.class);
            return get.invoke(db, id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readLineName(Object line) {
        String raw = invokeString(line, "getUnlocalisedName");
        if (raw == null || raw.isEmpty()) {
            raw = readPropertyString(line, nativeNameProp);
        }
        return raw != null ? raw : "";
    }

    private static String readLineDescription(Object line) {
        String raw = invokeString(line, "getUnlocalisedDescription");
        if (raw == null || raw.isEmpty()) {
            raw = readPropertyString(line, nativeDescProp);
        }
        return raw != null ? raw : "";
    }

    private static String readQuestName(Object quest) {
        return readPropertyString(quest, nativeNameProp);
    }

    private static String readQuestDescription(Object quest) {
        return readPropertyString(quest, nativeDescProp);
    }

    private static String readPropertyString(Object container, Object prop) {
        Object val = readProperty(container, prop);
        return val instanceof String ? (String) val : "";
    }

    private static boolean readBooleanProperty(Object container, Object prop) {
        Object val = readProperty(container, prop);
        return val instanceof Boolean && ((Boolean) val).booleanValue();
    }

    private static int readIntProperty(Object container, Object prop) {
        Object val = readProperty(container, prop);
        return val instanceof Number ? ((Number) val).intValue() : -1;
    }

    private static Object readProperty(Object container, Object prop) {
        if (container == null || prop == null) {
            return null;
        }
        try {
            Method m = container.getClass()
                .getMethod(
                    "getProperty",
                    prop.getClass()
                        .getInterfaces()[0]);
            return m.invoke(container, prop);
        } catch (Throwable t) {
            try {
                for (Method method : container.getClass()
                    .getMethods()) {
                    if ("getProperty".equals(method.getName()) && method.getParameterTypes().length == 1) {
                        return method.invoke(container, prop);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void fillIcon(QuestLineSummaryDto dto, Object bigStack) {
        ItemStack stack = bigStackToItem(bigStack);
        if (stack != null) {
            applyDisplayIcon(dto, stack);
        }
    }

    private static void fillIcon(QuestLineNodeDto dto, Object bigStack) {
        ItemStack stack = bigStackToItem(bigStack);
        if (stack != null) {
            applyDisplayIcon(dto, stack);
        }
    }

    private static void fillIcon(QuestDetailDto dto, Object bigStack) {
        ItemStack stack = bigStackToItem(bigStack);
        if (stack != null) {
            applyDisplayIcon(dto, stack);
        }
    }

    private static void applyDisplayIcon(QuestLineSummaryDto dto, ItemStack stack) {
        dto.iconItemId = resolveDisplayIconItemId(stack);
        dto.iconMeta = iconMetaForDisplayId(dto.iconItemId, stack);
    }

    private static void applyDisplayIcon(QuestLineNodeDto dto, ItemStack stack) {
        dto.iconItemId = resolveDisplayIconItemId(stack);
        dto.iconMeta = iconMetaForDisplayId(dto.iconItemId, stack);
    }

    private static void applyDisplayIcon(QuestDetailDto dto, ItemStack stack) {
        dto.iconItemId = resolveDisplayIconItemId(stack);
        dto.iconMeta = iconMetaForDisplayId(dto.iconItemId, stack);
    }

    /**
     * Display icon for quest trees / detail headers / rewards.
     * Filled fluid cells keep {@code mod:id[:meta]} (recipe NEI path).
     * Only {@code GregTech_FluidDisplay} becomes {@code fluid:name}.
     */
    private static String resolveDisplayIconItemId(ItemStack stack) {
        String fluidName = QuestFluidIconResolver.resolveFluidDisplayIconName(stack);
        if (fluidName != null && !fluidName.isEmpty()) {
            return IconItemId.FLUID_PREFIX + fluidName;
        }
        String registry = registryNameForStack(stack);
        return RecipeItemEntries.buildItemId(registry, normalizeMeta(stack.getItemDamage()));
    }

    /** {@code fluid:} ids must not carry cell damage as iconMeta. */
    private static int iconMetaForDisplayId(String iconItemId, ItemStack stack) {
        if (iconItemId != null && iconItemId.startsWith(IconItemId.FLUID_PREFIX)) {
            return 0;
        }
        return normalizeMeta(stack.getItemDamage());
    }

    private static int normalizeMeta(int meta) {
        return meta == Short.MAX_VALUE ? 0 : meta;
    }

    private static QuestRelationDto buildQuestRelation(UUID relatedId, Object relatedQuest, EntityPlayerMP player,
        UUID questingUuid, String requirementType) {
        QuestRelationDto rel = new QuestRelationDto();
        rel.questId = relatedId.toString();
        rel.name = relatedQuest != null ? localize(readQuestName(relatedQuest)) : relatedId.toString();
        rel.lineId = findLineIdForQuest(relatedId);
        rel.state = relatedQuest != null ? readQuestState(relatedQuest, player, questingUuid) : "LOCKED";
        rel.requirementType = requirementType != null && !requirementType.isEmpty() ? requirementType : "NORMAL";
        return rel;
    }

    private static String findLineIdForQuest(UUID questId) {
        if (questId == null || lineDb == null) {
            return "";
        }
        for (Map.Entry<UUID, Object> lineEntry : entriesOfDb(lineDb)) {
            UUID lineId = lineEntry.getKey();
            Object line = lineEntry.getValue();
            if (lineId == null || line == null) {
                continue;
            }
            for (Map.Entry<UUID, Object> entry : entriesOfDb(line)) {
                if (questId.equals(entry.getKey())) {
                    return lineId.toString();
                }
            }
        }
        return "";
    }

    private static ItemStack bigStackToItem(Object bigStack) {
        if (bigStack == null) {
            return null;
        }
        try {
            Method m = bigStack.getClass()
                .getMethod("getBaseStack");
            Object stack = m.invoke(bigStack);
            if (stack instanceof ItemStack) {
                return (ItemStack) stack;
            }
        } catch (Throwable ignored) {}
        try {
            Field f = bigStack.getClass()
                .getField("baseStack");
            Object stack = f.get(bigStack);
            if (stack instanceof ItemStack) {
                return (ItemStack) stack;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String readQuestState(Object quest, EntityPlayerMP player, UUID questingUuid) {
        if (quest == null) {
            return "LOCKED";
        }
        try {
            if (player != null) {
                Method m = quest.getClass()
                    .getMethod("getState", EntityPlayer.class);
                Object state = m.invoke(quest, player);
                if (state != null) {
                    return state.toString();
                }
            }
        } catch (Throwable ignored) {}
        if (questingUuid != null && invokeBooleanUuid(quest, "isComplete", questingUuid)) {
            return invokeBooleanUuid(quest, "hasClaimed", questingUuid) ? "COMPLETED" : "UNCLAIMED";
        }
        if (questingUuid != null && invokeBooleanUuid(quest, "isUnlocked", questingUuid)) {
            return "UNLOCKED";
        }
        return "LOCKED";
    }

    public static boolean canSubmitQuest(Object quest, EntityPlayerMP player) {
        return canSubmitQuestInternal(quest, player);
    }

    private static boolean canSubmitQuestInternal(Object quest, EntityPlayerMP player) {
        if (quest == null || player == null) {
            return false;
        }
        return invokeBoolean(quest, "canSubmit", player);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> readRequirements(Object quest) {
        if (quest == null) {
            return null;
        }
        try {
            Method m = quest.getClass()
                .getMethod("getRequirements");
            Object result = m.invoke(quest);
            if (result instanceof Set) {
                return (Set<UUID>) result;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String readRequirementType(Object quest, UUID reqId) {
        if (quest == null || reqId == null) {
            return "NORMAL";
        }
        try {
            Method m = quest.getClass()
                .getMethod("getRequirementType", UUID.class);
            Object type = m.invoke(quest, reqId);
            return type != null ? type.toString() : "NORMAL";
        } catch (Throwable ignored) {
            return "NORMAL";
        }
    }

    private static void appendRewardDtos(List<QuestRewardDto> out, String rewardId, Object reward) {
        if (reward == null || out == null) {
            return;
        }
        String factoryId = readFactoryId(reward);
        String name = localize(invokeString(reward, "getUnlocalisedName"));
        String description = readRewardText(reward);
        String id = rewardId != null ? rewardId : "";

        if (isChoiceFactory(factoryId)) {
            List<ItemStack> choices = extractRewardItemStacks(reward, "choices");
            if (choices.isEmpty()) {
                QuestRewardDto dto = baseRewardDto(out.size(), id, factoryId, name, description);
                dto.kind = "unsupported";
                dto.webClaimable = false;
                out.add(dto);
                return;
            }
            for (int i = 0; i < choices.size(); i++) {
                QuestRewardDto dto = baseRewardDto(out.size(), id, factoryId, name, description);
                dto.kind = "choice";
                dto.choiceOption = true;
                dto.choiceIndex = i;
                dto.webClaimable = true;
                fillRewardItemFields(dto, choices.get(i));
                out.add(dto);
            }
            return;
        }

        if (isItemFactory(factoryId)) {
            List<ItemStack> stacks = extractRewardItemStacks(reward, "items");
            if (stacks.isEmpty()) {
                stacks = extractRewardItemStacks(reward, "rewards");
            }
            if (stacks.isEmpty()) {
                QuestRewardDto dto = baseRewardDto(out.size(), id, factoryId, name, description);
                dto.kind = "unsupported";
                dto.webClaimable = false;
                out.add(dto);
                return;
            }
            for (int i = 0; i < stacks.size(); i++) {
                QuestRewardDto dto = baseRewardDto(out.size(), id, factoryId, name, description);
                dto.kind = "item";
                dto.choiceOption = false;
                dto.choiceIndex = -1;
                dto.webClaimable = true;
                fillRewardItemFields(dto, stacks.get(i));
                out.add(dto);
            }
            return;
        }

        // Unsupported / non-item: still emit a preview row when possible.
        List<ItemStack> preview = extractAllRewardStacks(reward);
        if (preview.isEmpty()) {
            QuestRewardDto dto = baseRewardDto(out.size(), id, factoryId, name, description);
            dto.kind = "unsupported";
            dto.webClaimable = false;
            out.add(dto);
            return;
        }
        for (int i = 0; i < preview.size(); i++) {
            QuestRewardDto dto = baseRewardDto(out.size(), id, factoryId, name, description);
            dto.kind = "unsupported";
            dto.webClaimable = false;
            fillRewardItemFields(dto, preview.get(i));
            out.add(dto);
        }
    }

    private static QuestRewardDto baseRewardDto(int index, String rewardId, String factoryId, String name,
        String description) {
        QuestRewardDto dto = new QuestRewardDto();
        dto.index = index;
        dto.rewardId = rewardId != null ? rewardId : "";
        dto.factoryId = factoryId;
        dto.name = name;
        dto.description = description;
        return dto;
    }

    private static void fillRewardItemFields(QuestRewardDto dto, ItemStack stack) {
        if (dto == null || stack == null) {
            return;
        }
        int meta = normalizeMeta(stack.getItemDamage());
        dto.registryName = registryNameForStack(stack);
        dto.itemId = RecipeItemEntries.buildItemId(dto.registryName, meta);
        dto.meta = meta;
        dto.iconItemId = resolveDisplayIconItemId(stack);
        dto.amount = stack.stackSize;
        String display = stack.getDisplayName();
        if (display != null && !display.isEmpty()) {
            dto.name = display;
        }
    }

    private static List<ItemStack> extractAllRewardStacks(Object reward) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (reward == null) {
            return out;
        }
        String[] fields = new String[] { "items", "choices", "rewards", "reward", "item" };
        for (int i = 0; i < fields.length; i++) {
            List<ItemStack> fromField = extractRewardItemStacks(reward, fields[i]);
            if (!fromField.isEmpty()) {
                return fromField;
            }
        }
        return out;
    }

    /**
     * Reads BigItemStack / ItemStack lists from a reward field, preserving {@code BigItemStack.stackSize}.
     */
    private static List<ItemStack> extractRewardItemStacks(Object reward, String fieldName) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        try {
            Field f = findField(reward.getClass(), fieldName);
            if (f == null) {
                return out;
            }
            f.setAccessible(true);
            Object val = f.get(reward);
            if (val == null) {
                return out;
            }
            if (val instanceof List) {
                List<?> list = (List<?>) val;
                for (int i = 0; i < list.size(); i++) {
                    ItemStack stack = stackFromRewardValue(list.get(i));
                    if (stack != null) {
                        out.add(stack);
                    }
                }
                return out;
            }
            ItemStack single = stackFromRewardValue(val);
            if (single != null) {
                out.add(single);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static List<ItemStack> extractRewardStacksFromField(Object reward, String fieldName) {
        return extractRewardItemStacks(reward, fieldName);
    }

    private static ItemStack stackFromRewardValue(Object val) {
        ItemStack stack = bigStackToItemWithCount(val);
        if (stack == null && val instanceof ItemStack) {
            stack = ((ItemStack) val).copy();
        }
        return stack;
    }

    /** Prefer BigItemStack count; fall back to base stack. */
    private static ItemStack bigStackToItemWithCount(Object bigStack) {
        ItemStack base = bigStackToItem(bigStack);
        if (base == null) {
            return null;
        }
        ItemStack copy = base.copy();
        int count = readBigStackSize(bigStack);
        if (count > 0) {
            copy.stackSize = count;
        } else if (copy.stackSize <= 0) {
            copy.stackSize = 1;
        }
        return copy;
    }

    private static int readBigStackSize(Object bigStack) {
        if (bigStack == null) {
            return -1;
        }
        try {
            Field f = bigStack.getClass()
                .getField("stackSize");
            Object val = f.get(bigStack);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        } catch (Throwable ignored) {}
        try {
            Method m = bigStack.getClass()
                .getMethod("getCombinedStacks");
            Object list = m.invoke(bigStack);
            if (list instanceof List) {
                int total = 0;
                for (Object o : (List<?>) list) {
                    if (o instanceof ItemStack) {
                        total += ((ItemStack) o).stackSize;
                    }
                }
                return total;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String readRewardText(Object reward) {
        try {
            Method m = reward.getClass()
                .getMethod("getRewardText");
            Object text = m.invoke(reward);
            if (text != null) {
                return localize(text.toString());
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String readFactoryId(Object taskOrReward) {
        try {
            Method m = taskOrReward.getClass()
                .getMethod("getFactoryID");
            Object rl = m.invoke(taskOrReward);
            if (rl != null) {
                Method toString = rl.getClass()
                    .getMethod("toString");
                return (String) toString.invoke(rl);
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static boolean matchesTaskText(Object quest, String q) {
        Object tasksDb = invoke(quest, "getTasks");
        if (tasksDb == null) {
            return false;
        }
        for (IndexedEntry entry : entriesOfIndexedDb(tasksDb)) {
            Object task = entry.value;
            if (task == null) {
                continue;
            }
            String name = localize(invokeString(task, "getUnlocalisedName"));
            if (name.toLowerCase(Locale.ROOT)
                .contains(q)) {
                return true;
            }
            try {
                Method texts = task.getClass()
                    .getMethod("getTextsForSearch");
                Object list = texts.invoke(task);
                if (list instanceof Collection) {
                    for (Object item : (Collection<?>) list) {
                        if (item != null && item.toString()
                            .toLowerCase(Locale.ROOT)
                            .contains(q)) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static String registryNameForStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        Object nameObj = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        return nameObj != null ? nameObj.toString() : "";
    }

    private static String localize(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String translated = StatCollector.translateToLocal(key);
        if (translated.equals(key) && key.contains(":")) {
            return key.substring(key.lastIndexOf(':') + 1)
                .replace('_', ' ');
        }
        return translated;
    }

    private static Object invoke(Object target, String method, Class<?>... params) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass()
                .getMethod(method, params);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass()
                .getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void invokeVoid(Object target, String method, Object arg) {
        if (target == null) {
            return;
        }
        try {
            for (Method m : target.getClass()
                .getMethods()) {
                if (m.getName()
                    .equals(method) && m.getParameterTypes().length == 1) {
                    m.invoke(target, arg);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String invokeString(Object target, String method) {
        Object result = invoke(target, method);
        return result != null ? result.toString() : "";
    }

    private static int invokeInt(Object target, String method) {
        Object result = invoke(target, method);
        return result instanceof Number ? ((Number) result).intValue() : 0;
    }

    private static boolean invokeBoolean(Object target, String method, EntityPlayerMP player) {
        if (target == null || player == null) {
            return false;
        }
        try {
            Method m = target.getClass()
                .getMethod(method, EntityPlayer.class);
            Object result = m.invoke(target, player);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean invokeBooleanUuid(Object target, String method, UUID uuid) {
        if (target == null || uuid == null) {
            return false;
        }
        try {
            Method m = target.getClass()
                .getMethod(method, UUID.class);
            Object result = m.invoke(target, uuid);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static NBTTagCompound writeTaskNbt(Object task) {
        if (task == null) {
            return new NBTTagCompound();
        }
        try {
            NBTTagCompound tag = new NBTTagCompound();
            Method m = task.getClass()
                .getMethod("writeToNBT", NBTTagCompound.class);
            m.invoke(task, tag);
            return tag;
        } catch (Throwable ignored) {
            return new NBTTagCompound();
        }
    }

    public static boolean isTaskComplete(Object task, UUID questingUuid) {
        return questingUuid != null && invokeBooleanUuid(task, "isComplete", questingUuid);
    }
}
