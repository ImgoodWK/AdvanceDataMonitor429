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
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestRelationDto;
import com.imgood.textech.webae.dto.QuestLineEdgeDto;
import com.imgood.textech.webae.dto.QuestLineGraphDto;
import com.imgood.textech.webae.dto.QuestLineNodeDto;
import com.imgood.textech.webae.dto.QuestLineSummaryDto;
import com.imgood.textech.webae.dto.QuestProgressDto;
import com.imgood.textech.webae.dto.QuestProgressEntryDto;
import com.imgood.textech.webae.dto.QuestRewardDto;
import com.imgood.textech.webae.dto.QuestSearchHitDto;
import com.imgood.textech.webae.dto.QuestTaskDto;

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
            if (edge == null || edge.fromQuestId == null || presentIds.contains(edge.fromQuestId)
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
                QuestTaskDto task = com.imgood.textech.webae.quest.QuestTaskDeserializer.deserialize(
                    idx++,
                    taskEntry.id,
                    taskEntry.value,
                    questingUuid);
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
        return dto;
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

            // Accumulate per-line submittable quest counts in the same pass.
            if (pe.canSubmit) {
                String lineId = questToLine.get(pe.questId);
                if (lineId != null) {
                    Integer prev = dto.lineSubmittableCounts.get(lineId);
                    dto.lineSubmittableCounts.put(lineId, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
                }
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

    public static Object getQuest(UUID questId) {
        if (questId == null || !ensureInit()) {
            return null;
        }
        return getFromDb(questDb, questId);
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
        if (task == null || player == null || stack == null) {
            return false;
        }
        try {
            Method m = task.getClass()
                .getMethod("submitItem", EntityPlayer.class, UUID.class, ItemStack.class);
            Object result = m.invoke(task, player, questingUuid, stack);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (NoSuchMethodException e) {
            try {
                Method alt = task.getClass()
                    .getMethod("submitItem", EntityPlayerMP.class, UUID.class, ItemStack.class);
                Object result = alt.invoke(task, player, questingUuid, stack);
                return result instanceof Boolean && ((Boolean) result).booleanValue();
            } catch (Throwable ignored) {
                return false;
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] submitItem failed: {}", t.toString());
            return false;
        }
    }

    public static boolean submitFluidTask(Object task, EntityPlayerMP player, UUID questingUuid, FluidStack fluid) {
        if (task == null || player == null || fluid == null) {
            return false;
        }
        try {
            Method m = task.getClass()
                .getMethod("submitFluid", EntityPlayer.class, UUID.class, FluidStack.class);
            Object result = m.invoke(task, player, questingUuid, fluid);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (NoSuchMethodException e) {
            try {
                Method alt = task.getClass()
                    .getMethod("submitFluid", EntityPlayerMP.class, UUID.class, FluidStack.class);
                Object result = alt.invoke(task, player, questingUuid, fluid);
                return result instanceof Boolean && ((Boolean) result).booleanValue();
            } catch (Throwable ignored) {
                return false;
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE Quest] submitFluid failed: {}", t.toString());
            return false;
        }
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
                out.add(new IndexedEntry(e.getKey()
                    .toString(), e.getValue()));
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
                .getMethod("getProperty", prop.getClass()
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
            dto.iconItemId = registryNameForStack(stack);
            dto.iconMeta = stack.getItemDamage();
        }
    }

    private static void fillIcon(QuestLineNodeDto dto, Object bigStack) {
        ItemStack stack = bigStackToItem(bigStack);
        if (stack != null) {
            dto.iconItemId = registryNameForStack(stack);
            dto.iconMeta = stack.getItemDamage();
        }
    }

    private static void fillIcon(QuestDetailDto dto, Object bigStack) {
        ItemStack stack = bigStackToItem(bigStack);
        if (stack != null) {
            dto.iconItemId = registryNameForStack(stack);
            dto.iconMeta = stack.getItemDamage();
        }
    }

    private static QuestRelationDto buildQuestRelation(
        UUID relatedId,
        Object relatedQuest,
        EntityPlayerMP player,
        UUID questingUuid,
        String requirementType) {
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
        List<ItemStack> stacks = extractAllRewardStacks(reward);
        if (stacks.isEmpty()) {
            QuestRewardDto dto = new QuestRewardDto();
            dto.index = out.size();
            dto.rewardId = rewardId != null ? rewardId : "";
            dto.factoryId = factoryId;
            dto.name = name;
            dto.description = description;
            out.add(dto);
            return;
        }
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            QuestRewardDto dto = new QuestRewardDto();
            dto.index = out.size();
            dto.rewardId = stacks.size() == 1 ? (rewardId != null ? rewardId : "")
                : ((rewardId != null ? rewardId : "") + ":" + i);
            dto.factoryId = factoryId;
            dto.name = name;
            dto.description = description;
            if (stack != null) {
                dto.registryName = registryNameForStack(stack);
                dto.itemId = dto.registryName;
                dto.meta = stack.getItemDamage();
                dto.amount = stack.stackSize;
                String display = stack.getDisplayName();
                if (display != null && !display.isEmpty()) {
                    dto.name = display;
                }
            }
            out.add(dto);
        }
    }

    private static List<ItemStack> extractAllRewardStacks(Object reward) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (reward == null) {
            return out;
        }
        String[] fields = new String[] { "items", "choices", "rewards", "reward", "item" };
        for (int i = 0; i < fields.length; i++) {
            List<ItemStack> fromField = extractRewardStacksFromField(reward, fields[i]);
            if (!fromField.isEmpty()) {
                return fromField;
            }
        }
        return out;
    }

    private static List<ItemStack> extractRewardStacksFromField(Object reward, String fieldName) {
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

    private static ItemStack stackFromRewardValue(Object val) {
        ItemStack stack = bigStackToItem(val);
        if (stack == null && val instanceof ItemStack) {
            stack = (ItemStack) val;
        }
        return stack;
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
                    .equals(method)
                    && m.getParameterTypes().length == 1) {
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
