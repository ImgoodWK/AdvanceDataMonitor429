package com.imgood.textech.cardbattle.pve;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.cardbattle.CardBattleTypes;
import com.imgood.textech.cardbattle.CardBattleTypes.BattleState;
import com.imgood.textech.cardbattle.CardBattleTypes.CardDef;
import com.imgood.textech.cardbattle.data.CardCatalog;
import com.imgood.textech.cardbattle.engine.BattleEngine;

public final class CardBattleSessions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final java.util.Map<String, BattleState> MATCHES = new java.util.concurrent.ConcurrentHashMap<String, BattleState>();
    private static final java.util.Map<String, RunState> RUNS = new java.util.concurrent.ConcurrentHashMap<String, RunState>();
    private static final java.util.Map<String, String> MATCH_OWNER = new java.util.concurrent.ConcurrentHashMap<String, String>();

    public static final String[] ALL_THEMES = { "vanilla", "gt", "thaum", "forestry", "astral", "avaritia", "ee",
        "genetics", "ae", "dlb" };

    private CardBattleSessions() {}

    public static class EquipDef {
        public String id;
        public String nameZh;
        public int attack;
        public int health;
        public int armor;

        public EquipDef(String id, String nameZh, int a, int h, int ar) {
            this.id = id;
            this.nameZh = nameZh;
            this.attack = a;
            this.health = h;
            this.armor = ar;
        }
    }

    public static final EquipDef[] EQUIPMENT = { new EquipDef("leather", "皮革套", 0, 1, 1),
        new EquipDef("wood_sword", "木剑", 1, 0, 0), new EquipDef("iron_chest", "铁胸甲", 0, 0, 2) };

    public static class StageDef {
        public String id;
        public String nameZh;
        public List<String> aiThemes = new ArrayList<String>();
        public String aiVoltage;
        public double difficulty;
        public String feature;
        public String kind;
        public int column;
        public int lane;
        public List<String> nextStageIds = new ArrayList<String>();
        public String skinRewardId;
    }

    public static class RewardChoice {
        public String id;
        public String labelZh;
        public boolean hard;
        public List<RewardItem> items = new ArrayList<RewardItem>();
        public List<String> cardIds = new ArrayList<String>();
        public String powerId;
        public String skinId;
        public String rewardKey;
        public String voltageTier;
        public String delivery;
    }

    public static class RewardItem {
        public String modid;
        public String name;
        public int meta;
        public int count;
        public String displayName;
    }

    public static class EquipStats {
        public int attack;
        public int health;
        public int armor;
    }

    public static class PowerDef {
        public String id;
        public String nameZh;
        public String descriptionZh;
        public int nexusHp;
        public int startSpellMana;
        public int firstUnitAttack;
        public int firstUnitHealth;
        public int firstUnitArmor;

        public PowerDef(String id, String nameZh, String descriptionZh, int nexusHp, int spellMana, int attack,
            int health, int armor) {
            this.id = id;
            this.nameZh = nameZh;
            this.descriptionZh = descriptionZh;
            this.nexusHp = nexusHp;
            this.startSpellMana = spellMana;
            this.firstUnitAttack = attack;
            this.firstUnitHealth = health;
            this.firstUnitArmor = armor;
        }
    }

    public static final PowerDef[] POWERS = {
        new PowerDef("power_reinforced_nexus", "强化主机", "每场战斗 Nexus 最大生命与当前生命 +4。", 4, 0, 0, 0, 0),
        new PowerDef("power_spell_cache", "法术缓存", "每场战斗以 1 点法术法力开始。", 0, 1, 0, 0, 0),
        new PowerDef("power_precision_tools", "精密工具", "每场第一名非结构单位获得 +1/+1 与 1 点护甲。", 0, 0, 1, 1, 1) };

    public static class RunState {
        public String runId;
        public String ownerUuid;
        public int seed;
        public String voltage;
        public List<String> themes = new ArrayList<String>();
        public List<String> deck = new ArrayList<String>();
        public EquipStats equipment = new EquipStats();
        public int stageIndex;
        public List<StageDef> stages = new ArrayList<StageDef>();
        public List<String> availableStageIds = new ArrayList<String>();
        public List<String> completedStageIds = new ArrayList<String>();
        public String currentStageId;
        public List<String> powers = new ArrayList<String>();
        public List<String> unlockedSkinIds = new ArrayList<String>();
        public List<RewardChoice> pendingChoice;
        public boolean completed;
        public int victories;
    }

    public static class PendingEntry {
        public String id;
        public long createdAt;
        public String status;
        public List<RewardItem> items = new ArrayList<RewardItem>();
        public JsonObject source = new JsonObject();
    }

    public static RunState startRun(String ownerUuid, String playerName, List<String> themes, String voltage,
        List<String> equipmentIds, Integer seedOpt) {
        int max = CardBattleTypes.themeSlots(voltage);
        if (themes == null || themes.isEmpty()) throw new IllegalArgumentException("至少选择 1 个主题");
        if (themes.size() > max) throw new IllegalArgumentException("电压 " + voltage + " 最多 " + max + " 个主题");
        int seed = seedOpt != null ? seedOpt.intValue() : (int) (System.currentTimeMillis() % 1_000_000L);
        RunState run = new RunState();
        run.runId = UUID.randomUUID()
            .toString();
        run.ownerUuid = ownerUuid;
        run.seed = seed;
        run.voltage = voltage;
        run.themes = new ArrayList<String>(themes);
        run.deck = buildDeck(themes, voltage, seed);
        for (String eid : equipmentIds) {
            for (EquipDef eq : EQUIPMENT) {
                if (eq.id.equals(eid)) {
                    run.equipment.attack += eq.attack;
                    run.equipment.health += eq.health;
                    run.equipment.armor += eq.armor;
                }
            }
        }
        run.stages = generateStages(seed, voltage, 7);
        run.availableStageIds.add("stage_1a");
        run.availableStageIds.add("stage_1b");
        run.unlockedSkinIds.add("gt_factory");
        RUNS.put(run.runId, run);
        return run;
    }

    public static RunState getRun(String runId) {
        return RUNS.get(runId);
    }

    public static JsonObject beginStage(String runId, String ownerUuid, String playerName, String stageId) {
        RunState run = RUNS.get(runId);
        if (run == null || !ownerUuid.equals(run.ownerUuid)) throw new IllegalArgumentException("Run not found");
        if (run.completed) throw new IllegalArgumentException("Run completed");
        if (run.currentStageId != null) throw new IllegalArgumentException("Stage already active");
        String selectedStageId = stageId != null && stageId.length() > 0 ? stageId
            : (run.availableStageIds.isEmpty() ? null : run.availableStageIds.get(0));
        if (selectedStageId == null || !run.availableStageIds.contains(selectedStageId)) {
            throw new IllegalArgumentException("Stage is not available");
        }
        StageDef stage = findStage(run, selectedStageId);
        if (stage == null) throw new IllegalArgumentException("No stage");
        JsonObject opts = new JsonObject();
        opts.addProperty("seed", run.seed + run.stageIndex * 17);
        opts.addProperty("playerId", ownerUuid);
        opts.addProperty("playerName", playerName);
        opts.add("playerDeck", toArr(run.deck));
        opts.add("playerThemes", toArr(run.themes));
        opts.addProperty("playerVoltage", run.voltage);
        opts.add("aiDeck", toArr(buildDeck(stage.aiThemes, stage.aiVoltage, run.seed + run.stageIndex * 99)));
        opts.add("aiThemes", toArr(stage.aiThemes));
        opts.addProperty("aiVoltage", stage.aiVoltage);
        opts.addProperty("aiName", stage.nameZh);
        boolean dlb = "dlb_force".equals(stage.feature) || run.themes.contains("dlb");
        opts.addProperty("dlbForceEvery", dlb ? 5 : 0);
        BattleState match = BattleEngine.createMatch(opts);
        int powerAttack = 0;
        int powerHealth = 0;
        int powerArmor = 0;
        for (String powerId : run.powers) {
            PowerDef power = powerById(powerId);
            if (power == null) continue;
            match.players[0].maxNexusHp += power.nexusHp;
            match.players[0].nexusHp += power.nexusHp;
            match.players[0].spellMana = Math.min(3, match.players[0].spellMana + power.startSpellMana);
            powerAttack += power.firstUnitAttack;
            powerHealth += power.firstUnitHealth;
            powerArmor += power.firstUnitArmor;
        }
        match.pendingEquipAtk = run.equipment.attack + powerAttack;
        match.pendingEquipHp = run.equipment.health + powerHealth;
        match.pendingEquipArmor = run.equipment.armor + powerArmor;
        if (!"battle".equals(stage.kind)) {
            int bonus = "boss".equals(stage.kind) ? 10 : 5;
            match.players[1].nexusHp += bonus;
            match.players[1].maxNexusHp += bonus;
            match.players[1].maxMana += "boss".equals(stage.kind) ? 2 : 1;
            match.players[1].mana = match.players[1].maxMana;
        }
        MATCHES.put(match.matchId, match);
        MATCH_OWNER.put(match.matchId, ownerUuid);
        run.pendingChoice = null;
        run.currentStageId = stage.id;
        JsonObject out = new JsonObject();
        out.add("run", GSON.toJsonTree(run));
        out.addProperty("matchId", match.matchId);
        out.add("match", GSON.toJsonTree(BattleEngine.publicView(match)));
        return out;
    }

    private static StageDef findStage(RunState run, String id) {
        for (StageDef stage : run.stages) if (stage.id.equals(id)) return stage;
        return null;
    }

    private static PowerDef powerById(String id) {
        for (PowerDef power : POWERS) if (power.id.equals(id)) return power;
        return null;
    }

    public static BattleState getMatch(String matchId, String ownerUuid) {
        BattleState m = MATCHES.get(matchId);
        if (m == null) throw new IllegalArgumentException("Match not found");
        if (!ownerUuid.equals(MATCH_OWNER.get(matchId))) throw new IllegalArgumentException("Forbidden");
        BattleEngine.runAi(m);
        return BattleEngine.publicView(m);
    }

    public static JsonObject act(String matchId, String ownerUuid, JsonObject action, String runId) {
        BattleState m = MATCHES.get(matchId);
        if (m == null) throw new IllegalArgumentException("Match not found");
        if (!ownerUuid.equals(MATCH_OWNER.get(matchId))) throw new IllegalArgumentException("Forbidden");
        BattleEngine.applyAction(m, 0, action);
        RunState run = finishIfNeeded(matchId, runId);
        JsonObject out = new JsonObject();
        out.add("match", GSON.toJsonTree(BattleEngine.publicView(m)));
        out.add("run", run != null ? GSON.toJsonTree(run) : null);
        return out;
    }

    private static RunState finishIfNeeded(String matchId, String runId) {
        BattleState m = MATCHES.get(matchId);
        if (m == null || !"game_over".equals(m.phase) || runId == null) return RUNS.get(runId);
        RunState run = RUNS.get(runId);
        if (run == null || m.runSettled) return run;
        m.runSettled = true;
        if (m.winner != null && m.winner.intValue() == 0) {
            run.victories += 1;
            StageDef stage = findStage(run, run.currentStageId);
            if (stage == null) throw new IllegalStateException("Active stage not found");
            run.pendingChoice = rewardChoices(stage, run.voltage);
            run.completedStageIds.add(stage.id);
            run.stageIndex = run.completedStageIds.size();
            run.availableStageIds = new ArrayList<String>(stage.nextStageIds);
            run.currentStageId = null;
            if (stage.nextStageIds.isEmpty()) run.completed = true;
        } else {
            run.completed = true;
        }
        return run;
    }

    public static JsonObject claimReward(String runId, String ownerUuid, String choiceId) {
        RunState run = RUNS.get(runId);
        if (run == null || !ownerUuid.equals(run.ownerUuid)) throw new IllegalArgumentException("Run not found");
        if (run.pendingChoice == null) throw new IllegalArgumentException("No pending reward");
        RewardChoice choice = null;
        for (RewardChoice c : run.pendingChoice) if (c.id.equals(choiceId)) choice = c;
        if (choice == null) throw new IllegalArgumentException("Invalid choice");
        String stageId = run.completedStageIds.isEmpty() ? "unknown"
            : run.completedStageIds.get(run.completedStageIds.size() - 1);
        run.deck.addAll(choice.cardIds);
        if (choice.powerId != null && !run.powers.contains(choice.powerId)) run.powers.add(choice.powerId);
        List<String> unlocked = new ArrayList<String>();
        if (choice.skinId != null && !run.unlockedSkinIds.contains(choice.skinId)) {
            run.unlockedSkinIds.add(choice.skinId);
            unlocked.add(choice.skinId);
        }
        String entryId = null;
        if (!choice.items.isEmpty()) {
            entryId = enqueueReward(
                ownerUuid,
                choice.items,
                run.runId,
                stageId,
                choice.labelZh,
                choice.rewardKey,
                choice.voltageTier,
                choice.delivery);
        }
        run.pendingChoice = null;
        JsonObject out = new JsonObject();
        out.add("run", GSON.toJsonTree(run));
        if (entryId != null) out.addProperty("entryId", entryId);
        else out.add("entryId", com.google.gson.JsonNull.INSTANCE);
        out.add("unlockedSkinIds", GSON.toJsonTree(unlocked));
        return out;
    }

    public static List<String> buildDeck(List<String> themes, String voltage, int seed) {
        CardCatalog.ensureLoaded();
        int slots = CardBattleTypes.themeSlots(voltage);
        List<String> used = new ArrayList<String>();
        for (int i = 0; i < themes.size() && i < slots; i++) used.add(themes.get(i));
        Random rng = new Random(seed);
        List<String> deck = new ArrayList<String>();
        for (String th : used) {
            List<CardDef> cards = new ArrayList<CardDef>(CardCatalog.byTheme(th));
            CollectionsShuffle(cards, rng);
            int take = Math.min(40, cards.size());
            for (int i = 0; i < take; i++) {
                CardDef c = cards.get(i);
                deck.add(c.id);
                if ("genetics".equals(th) && "unit".equals(c.kind) && c.cost <= 1) deck.add(c.id);
            }
        }
        while (deck.size() < 40 && !used.isEmpty()) {
            String th = used.get(rng.nextInt(used.size()));
            List<CardDef> cards = CardCatalog.byTheme(th);
            if (cards.isEmpty()) break;
            deck.add(cards.get(rng.nextInt(cards.size())).id);
        }
        return deck;
    }

    private static void CollectionsShuffle(List<CardDef> list, Random rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            CardDef tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    public static List<StageDef> generateStages(int seed, String playerVoltage, int count) {
        Random rng = new Random(seed);
        int base = CardBattleTypes.voltageIndex(playerVoltage);
        List<StageDef> stages = new ArrayList<StageDef>();
        stages.add(makeStage(rng, base, "stage_1a", 0, 0, "battle", new String[] { "stage_2a", "stage_2b" }));
        stages.add(makeStage(rng, base, "stage_1b", 0, 1, "battle", new String[] { "stage_2a", "stage_2b" }));
        stages.add(makeStage(rng, base, "stage_2a", 1, 0, "battle", new String[] { "stage_3a" }));
        stages.add(makeStage(rng, base, "stage_2b", 1, 1, "battle", new String[] { "stage_3b" }));
        stages.add(makeStage(rng, base, "stage_3a", 2, 0, "elite", new String[] { "stage_boss" }));
        stages.add(makeStage(rng, base, "stage_3b", 2, 1, "elite", new String[] { "stage_boss" }));
        stages.add(makeStage(rng, base, "stage_boss", 3, 0, "boss", new String[0]));
        return stages;
    }

    private static StageDef makeStage(Random rng, int baseVoltage, String id, int column, int lane, String kind,
        String[] next) {
        String aiTheme = ALL_THEMES[rng.nextInt(ALL_THEMES.length)];
        int extraVoltage = column / 2 + ("boss".equals(kind) ? 1 : 0);
        int vi = Math.min(CardBattleTypes.VOLTAGE_ORDER.length - 1, baseVoltage + extraVoltage);
        StageDef stage = new StageDef();
        stage.id = id;
        stage.kind = kind;
        stage.column = column;
        stage.lane = lane;
        stage.nameZh = ("boss".equals(kind) ? "终局首领" : "elite".equals(kind) ? "精英节点" : "战斗节点") + " · "
            + aiTheme;
        stage.aiThemes.add(aiTheme);
        stage.aiVoltage = CardBattleTypes.VOLTAGE_ORDER[vi];
        stage.difficulty = 1 + column * 0.35 + ("elite".equals(kind) ? 0.4 : "boss".equals(kind) ? 0.9 : 0);
        if ("dlb".equals(aiTheme)) stage.feature = "dlb_force";
        if ("forestry".equals(aiTheme)) stage.feature = "hive";
        for (String nextId : next) stage.nextStageIds.add(nextId);
        if ("boss".equals(kind)) stage.skinRewardId = "overclocked_nexus";
        return stage;
    }

    public static List<RewardChoice> rewardChoices(StageDef stage, String voltage) {
        List<RewardChoice> list = new ArrayList<RewardChoice>();
        RewardChoice cards = new RewardChoice();
        cards.id = "cards";
        List<CardDef> themeCards = CardCatalog.byTheme(stage.aiThemes.get(0));
        int cardCount = "boss".equals(stage.kind) ? 3 : 2;
        cards.labelZh = "扩充卡组（" + cardCount + " 张 " + stage.aiThemes.get(0) + " 卡）";
        for (int i = 0; i < themeCards.size() && i < cardCount; i++) cards.cardIds.add(themeCards.get(i).id);
        cards.skinId = stage.skinRewardId;
        list.add(cards);

        RewardChoice power = new RewardChoice();
        int powerHash = 0;
        for (int i = 0; i < stage.id.length(); i++) powerHash += stage.id.charAt(i);
        PowerDef selectedPower = POWERS[powerHash % POWERS.length];
        power.id = "power";
        power.labelZh = "获得能力：" + selectedPower.nameZh;
        power.powerId = selectedPower.id;
        power.skinId = stage.skinRewardId;
        list.add(power);

        int count = "boss".equals(stage.kind) ? 64 : "elite".equals(stage.kind) ? 32 : 16;
        RewardChoice voltageReward = makeReward(
            "voltage_cache",
            voltage + " 电压奖励缓存（待游戏内桥接）",
            false,
            voltage,
            count);
        voltageReward.rewardKey = "textech.cardbattle.voltage." + voltage.toLowerCase(java.util.Locale.ROOT) + ".cache";
        voltageReward.voltageTier = voltage;
        voltageReward.delivery = "pending_bridge";
        voltageReward.skinId = stage.skinRewardId;
        list.add(voltageReward);
        return list;
    }

    private static RewardChoice makeReward(String id, String label, boolean hard, String voltage, int count) {
        RewardChoice c = new RewardChoice();
        c.id = id;
        c.labelZh = label;
        c.hard = hard;
        RewardItem item = new RewardItem();
        item.modid = "gregtech";
        item.name = "gt.metaitem.01";
        item.meta = 32610 + Math.max(0, CardBattleTypes.voltageIndex(voltage));
        item.count = count;
        item.displayName = voltage + " Pump x" + count;
        c.items.add(item);
        return c;
    }

    private static JsonArray toArr(List<String> list) {
        JsonArray a = new JsonArray();
        for (String s : list) a.add(new com.google.gson.JsonPrimitive(s));
        return a;
    }

    public static String enqueueReward(String ownerUuid, List<RewardItem> items, String runId, String stageId,
        String label, String rewardKey, String voltageTier, String delivery) {
        List<PendingEntry> entries = readPending(ownerUuid);
        PendingEntry e = new PendingEntry();
        e.id = UUID.randomUUID()
            .toString();
        e.createdAt = System.currentTimeMillis();
        e.status = "pending";
        e.items = items;
        e.source.addProperty("runId", runId);
        e.source.addProperty("stageId", stageId);
        e.source.addProperty("label", label);
        e.source.addProperty("schemaVersion", 2);
        if (rewardKey != null) e.source.addProperty("rewardKey", rewardKey);
        if (voltageTier != null) e.source.addProperty("voltageTier", voltageTier);
        if (delivery != null) e.source.addProperty("delivery", delivery);
        entries.add(e);
        writePending(ownerUuid, entries);
        return e.id;
    }

    public static List<PendingEntry> listPending(String ownerUuid) {
        List<PendingEntry> all = readPending(ownerUuid);
        List<PendingEntry> out = new ArrayList<PendingEntry>();
        for (PendingEntry e : all) if ("pending".equals(e.status)) out.add(e);
        return out;
    }

    public static PendingEntry markClaimed(String ownerUuid, String id) {
        List<PendingEntry> all = readPending(ownerUuid);
        for (PendingEntry e : all) {
            if (id.equals(e.id) && "pending".equals(e.status)) {
                e.status = "claimed";
                writePending(ownerUuid, all);
                return e;
            }
        }
        return null;
    }

    private static File rewardsFile(String ownerUuid) {
        File dir = new File(TeXTechDataDir.cardBattleRoot(), "pending-rewards");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, ownerUuid + ".json");
    }

    private static List<PendingEntry> readPending(String ownerUuid) {
        File f = rewardsFile(ownerUuid);
        if (!f.isFile()) return new ArrayList<PendingEntry>();
        try {
            InputStreamReader r = new InputStreamReader(new FileInputStream(f), Charset.forName("UTF-8"));
            try {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                if (root == null || !root.has("entries")) return new ArrayList<PendingEntry>();
                PendingEntry[] arr = GSON.fromJson(root.get("entries"), PendingEntry[].class);
                List<PendingEntry> list = new ArrayList<PendingEntry>();
                if (arr != null) for (PendingEntry e : arr) list.add(e);
                return list;
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return new ArrayList<PendingEntry>();
        }
    }

    private static void writePending(String ownerUuid, List<PendingEntry> entries) {
        File f = rewardsFile(ownerUuid);
        try {
            JsonObject root = new JsonObject();
            root.add("entries", GSON.toJsonTree(entries));
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8"));
            try {
                GSON.toJson(root, w);
            } finally {
                w.close();
            }
        } catch (Throwable ignored) {}
    }
}
