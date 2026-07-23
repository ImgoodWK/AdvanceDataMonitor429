package com.imgood.textech.cardbattle.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.textech.cardbattle.CardBattleTypes.AttackPair;
import com.imgood.textech.cardbattle.CardBattleTypes.BattleState;
import com.imgood.textech.cardbattle.CardBattleTypes.BoardUnit;
import com.imgood.textech.cardbattle.CardBattleTypes.CardDef;
import com.imgood.textech.cardbattle.CardBattleTypes.PlayerState;
import com.imgood.textech.cardbattle.CardBattleTypes.SpellEffect;
import com.imgood.textech.cardbattle.CardBattleTypes.SpellStackItem;
import com.imgood.textech.cardbattle.data.CardCatalog;

/**
 * LoR-style attack/block engine with GTNH theme hooks. Runs off the Forge tick
 * (HTTP worker threads only).
 */
public final class BattleEngine {

    private static final int BOARD = 6;
    private static final int START_NEXUS = 20;
    private static final int MAX_BANK = 6;
    private static final int MAX_HAND = 10;

    private BattleEngine() {}

    public static BattleState createMatch(JsonObject opts) {
        CardCatalog.ensureLoaded();
        int seed = opts.has("seed") ? opts.get("seed")
            .getAsInt() : (int) (System.currentTimeMillis() % 1_000_000L);
        Random rng = new Random(seed);
        PlayerState p0 = createPlayer(
            opts.get("playerId")
                .getAsString(),
            opts.get("playerName")
                .getAsString(),
            false,
            jsonStringList(opts.getAsJsonArray("playerDeck")),
            jsonStringList(opts.getAsJsonArray("playerThemes")),
            opts.get("playerVoltage")
                .getAsString(),
            rng);
        PlayerState p1 = createPlayer(
            "ai",
            opts.has("aiName") ? opts.get("aiName")
                .getAsString() : "PvE Opponent",
            true,
            jsonStringList(opts.getAsJsonArray("aiDeck")),
            jsonStringList(opts.getAsJsonArray("aiThemes")),
            opts.get("aiVoltage")
                .getAsString(),
            rng);
        BattleState s = new BattleState();
        s.matchId = UUID.randomUUID()
            .toString();
        s.seed = seed;
        s.turn = 1;
        s.phase = "mulligan";
        s.activePlayer = 0;
        s.attackTokenPlayer = 0;
        s.attackTokenAvailable = true;
        s.combatAttacker = -1;
        s.consecutivePasses = 0;
        s.responsePasses = 0;
        s.responseOriginPlayer = null;
        s.players[0] = p0;
        s.players[1] = p1;
        boolean dlb = p0.themes.contains("dlb") || p1.themes.contains("dlb");
        s.dlbForceEvery = opts.has("dlbForceEvery") ? opts.get("dlbForceEvery")
            .getAsInt() : (dlb ? 5 : 0);
        s.log.add("Match start seed=" + seed);
        s.aePool = CardCatalog.aeOffDeckPool(p0.deck);
        return s;
    }

    private static List<String> jsonStringList(JsonArray arr) {
        List<String> out = new ArrayList<String>();
        if (arr == null) return out;
        for (int i = 0; i < arr.size(); i++) out.add(arr.get(i)
            .getAsString());
        return out;
    }

    private static PlayerState createPlayer(String id, String name, boolean ai, List<String> deckIds,
        List<String> themes, String voltage, Random rng) {
        List<String> shuffled = new ArrayList<String>(deckIds);
        Collections.shuffle(shuffled, rng);
        PlayerState p = new PlayerState();
        p.id = id;
        p.name = name;
        p.isAi = ai;
        p.nexusHp = START_NEXUS;
        p.maxNexusHp = START_NEXUS;
        p.mana = 1;
        p.maxMana = 1;
        p.spellMana = 0;
        p.voltage = voltage;
        p.themes = new ArrayList<String>(themes);
        for (int i = 0; i < 4 && !shuffled.isEmpty(); i++) p.hand.add(shuffled.remove(0));
        p.deck = shuffled;
        return p;
    }

    private static BoardUnit makeUnit(CardDef def) {
        BoardUnit u = new BoardUnit();
        u.instanceId = UUID.randomUUID()
            .toString();
        u.cardId = def.id;
        u.attack = def.attack != null ? def.attack.intValue() : 0;
        u.health = def.health != null ? def.health.intValue() : 1;
        u.maxHealth = u.health;
        u.armor = def.armor != null ? def.armor.intValue() : 0;
        if (def.keywords != null) u.keywords.addAll(def.keywords);
        if (def.aspects != null) u.aspects.addAll(def.aspects);
        u.isStructure = "structure".equals(def.kind);
        u.untargetable = u.keywords.contains("untargetable") || u.keywords.contains("beehive");
        if (def.hiveCooldown != null) u.hiveTurnsLeft = def.hiveCooldown;
        return u;
    }

    private static int opp(int i) {
        return i == 0 ? 1 : 0;
    }

    private static int firstEmpty(BoardUnit[] board) {
        for (int i = 0; i < board.length; i++) if (board[i] == null) return i;
        return -1;
    }

    /** Pack occupied slots left, nulls right (LoR-style bench). */
    private static void compactBoard(BoardUnit[] board) {
        List<BoardUnit> units = new ArrayList<BoardUnit>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] != null) units.add(board[i]);
        }
        for (int i = 0; i < board.length; i++) {
            board[i] = i < units.size() ? units.get(i) : null;
        }
    }

    private static boolean hasKw(BoardUnit u, String kw) {
        return u != null && u.keywords.contains(kw);
    }

    private static boolean hasOrdoAer(BoardUnit u) {
        return u != null && u.aspects.contains("ordo") && u.aspects.contains("aer");
    }

    private static void addToHand(PlayerState p, String cardId, BattleState s) {
        if (p.hand.size() >= MAX_HAND) {
            p.discard.add(cardId);
            s.log.add(p.name + " burns " + cardId + " (hand full)");
            return;
        }
        p.hand.add(cardId);
    }

    private static void draw(PlayerState p, int n, BattleState s) {
        for (int i = 0; i < n; i++) {
            if (p.deck.isEmpty()) {
                s.log.add(p.name + " loses: attempted to draw from an empty deck");
                p.nexusHp = 0;
                checkWinner(s);
                return;
            }
            addToHand(p, p.deck.remove(0), s);
        }
    }

    private static boolean spendMana(PlayerState p, int cost, boolean spell) {
        if (p.mana + p.bankedMana + (spell ? p.spellMana : 0) < cost) return false;
        int need = cost;
        int fromMana = Math.min(p.mana, need);
        p.mana -= fromMana;
        need -= fromMana;
        if (spell) {
            int fromSpell = Math.min(p.spellMana, need);
            p.spellMana -= fromSpell;
            need -= fromSpell;
        }
        if (need > 0) p.bankedMana -= need;
        return true;
    }

    private static void dealDmg(BoardUnit u, int raw) {
        int dmg = raw;
        if (u.armor > 0) {
            int abs = Math.min(u.armor, dmg);
            u.armor -= abs;
            dmg -= abs;
        }
        u.health -= dmg;
    }

    private static void removeDead(PlayerState p, BattleState s) {
        boolean changed = false;
        for (int i = 0; i < p.board.length; i++) {
            BoardUnit u = p.board[i];
            if (u != null && u.health <= 0) {
                s.log.add(p.name + " loses " + u.cardId + " at " + i);
                p.discard.add(u.cardId);
                p.board[i] = null;
                changed = true;
            }
        }
        if (changed) compactBoard(p.board);
    }

    private static void checkWinner(BattleState s) {
        PlayerState a = s.players[0];
        PlayerState b = s.players[1];
        if (a.nexusHp <= 0) {
            s.winner = 1;
            s.phase = "game_over";
        } else if (b.nexusHp <= 0) {
            s.winner = 0;
            s.phase = "game_over";
        }
    }

    public static void applyAction(BattleState s, int actor, JsonObject action) {
        if ("game_over".equals(s.phase)) throw new IllegalStateException("Game over");
        String type = action.get("type")
            .getAsString();
        Random rng = new Random(s.seed + s.turn * 13L + s.log.size());
        if ("confirm_mulligan".equals(type)) {
            confirmMulligan(s, actor, action, rng);
        } else if ("play_card".equals(type)) {
            playCard(s, actor, action, rng);
        } else if ("pass_priority".equals(type)) {
            passPriority(s, actor);
        } else if ("start_attack".equals(type)) {
            startAttack(s, actor);
        } else if ("end_main".equals(type)) {
            if (s.attackTokenAvailable && s.attackTokenPlayer == actor) startAttack(s, actor);
            else passPriority(s, actor);
        } else if ("declare_attacks".equals(type)) {
            declareAttacks(s, actor, action);
        } else if ("declare_blocks".equals(type)) {
            declareBlocks(s, actor, action);
        } else if ("pass_block".equals(type)) {
            JsonArray pairs = new JsonArray();
            for (Integer slot : s.attackOrder) {
                JsonObject p = new JsonObject();
                p.addProperty("attackerSlot", slot.intValue());
                p.addProperty("blockerSlot", -1);
                pairs.add(p);
            }
            JsonObject blk = new JsonObject();
            blk.addProperty("type", "declare_blocks");
            blk.add("pairs", pairs);
            declareBlocks(s, actor, blk);
        } else if ("swap_slots".equals(type)) {
            swapSlots(s, actor, action.get("a")
                .getAsInt(),
                action.get("b")
                    .getAsInt());
        } else if ("pass_swap".equals(type)) {
            passSwap(s, actor);
        } else if ("concede".equals(type)) {
            s.winner = opp(actor);
            s.phase = "game_over";
            s.log.add(s.players[actor].name + " conceded");
        } else {
            throw new IllegalArgumentException("Unknown action " + type);
        }
        maybeApplyEquip(s);
        runAi(s);
    }

    private static void maybeApplyEquip(BattleState s) {
        if (s.equipApplied) return;
        if (s.pendingEquipAtk == 0 && s.pendingEquipHp == 0 && s.pendingEquipArmor == 0) return;
        for (int i = 0; i < s.players[0].board.length; i++) {
            BoardUnit u = s.players[0].board[i];
            if (u != null && !u.isStructure) {
                u.attack += s.pendingEquipAtk;
                u.health += s.pendingEquipHp;
                u.maxHealth += s.pendingEquipHp;
                u.armor += s.pendingEquipArmor;
                s.equipApplied = true;
                s.log.add("Starter equipment applied");
                return;
            }
        }
    }

    private static void requirePhase(BattleState s, String phase) {
        if (!phase.equals(s.phase)) throw new IllegalStateException("Invalid phase " + s.phase);
    }

    private static void requireActive(BattleState s, int actor) {
        if (s.activePlayer != actor) throw new IllegalStateException("Not your turn");
    }

    private static boolean isResponsePhase(String phase) {
        return "spell_response".equals(phase) || "combat_response".equals(phase);
    }

    private static String spellSpeed(CardDef def) {
        return def.spellSpeed != null ? def.spellSpeed : "slow";
    }

    private static void confirmMulligan(BattleState s, int actor, JsonObject action, Random rng) {
        requirePhase(s, "mulligan");
        if (s.mulliganDone[actor]) throw new IllegalStateException("Mulligan already confirmed");
        PlayerState player = s.players[actor];
        JsonArray requested = action.has("replaceIndices") ? action.getAsJsonArray("replaceIndices") : new JsonArray();
        List<Integer> indices = new ArrayList<Integer>();
        HashSet<Integer> unique = new HashSet<Integer>();
        for (int i = 0; i < requested.size(); i++) {
            int index = requested.get(i)
                .getAsInt();
            if (index < 0 || index >= player.hand.size() || !unique.add(Integer.valueOf(index))) {
                throw new IllegalStateException("Invalid mulligan index");
            }
            indices.add(Integer.valueOf(index));
        }
        Collections.sort(indices);
        if (indices.size() > player.deck.size()) {
            throw new IllegalStateException("Not enough cards to replace mulligan");
        }

        List<String> returned = new ArrayList<String>();
        for (Integer boxed : indices) {
            int index = boxed.intValue();
            returned.add(player.hand.get(index));
            player.hand.set(index, player.deck.remove(0));
        }
        player.deck.addAll(returned);
        Collections.shuffle(player.deck, rng);
        s.mulliganDone[actor] = true;
        s.log.add(player.name + " confirms mulligan (" + indices.size() + ")");
        if (s.mulliganDone[0] && s.mulliganDone[1]) {
            draw(s.players[0], 1, s);
            draw(s.players[1], 1, s);
            if (s.winner != null) return;
            s.phase = "main";
            s.activePlayer = s.attackTokenPlayer;
            s.log.add("Mulligan complete");
        }
    }

    private static BoardUnit targetAt(BoardUnit[] board, int slot, String side) {
        if (slot < 0 || slot >= BOARD) throw new IllegalStateException("Invalid " + side + " target slot");
        BoardUnit target = board[slot];
        if (target == null) throw new IllegalStateException("Missing " + side + " target");
        return target;
    }

    private static void validateSpellTarget(BattleState s, int actor, CardDef def, JsonObject action) {
        PlayerState me = s.players[actor];
        PlayerState you = s.players[opp(actor)];
        int enemySlot = action.has("targetEnemySlot") ? action.get("targetEnemySlot")
            .getAsInt() : 0;
        int mySlot = action.has("targetSlot") ? action.get("targetSlot")
            .getAsInt() : 0;
        String id = def.id;

        if ("van_smite".equals(id) || "ae_annihilation".equals(id) || "th_ignis".equals(id)) {
            BoardUnit target = targetAt(you.board, enemySlot, "enemy");
            if (target.untargetable) throw new IllegalStateException("Enemy target is untargetable");
        } else if ("van_heal".equals(id) || "th_ordo_aer".equals(id) || "th_ward".equals(id)
            || "ge_mutate".equals(id) || "ge_clone".equals(id)) {
            BoardUnit target = targetAt(me.board, mySlot, "friendly");
            if (target.isStructure) throw new IllegalStateException("Friendly target must be a unit");
            if ("ge_clone".equals(id) && firstEmpty(me.board) < 0) {
                throw new IllegalStateException("No empty slot for clone");
            }
        } else if ("gt_wrench".equals(id)) {
            BoardUnit target = targetAt(you.board, enemySlot, "enemy");
            if (!target.isStructure || !hasKw(target, "machine")) {
                throw new IllegalStateException("Target must be an enemy machine structure");
            }
        } else if ("fo_smoke".equals(id)) {
            BoardUnit target = targetAt(you.board, enemySlot, "enemy");
            if (!hasKw(target, "stealth")) throw new IllegalStateException("Target must have stealth");
        } else if ("ee_watch".equals(id)) {
            BoardUnit target = targetAt(me.board, mySlot, "friendly");
            if (!target.isStructure || target.hiveTurnsLeft == null) {
                throw new IllegalStateException("Target must be a structure with cooldown");
            }
        } else if ("av_eternal".equals(id) && !me.eternalReady && me.singularitiesPlayed < 3) {
            throw new IllegalStateException("Eternal Singularity requires 3 singularities");
        }
    }

    private static int validatePlayCard(BattleState s, int actor, CardDef def, JsonObject action) {
        PlayerState me = s.players[actor];
        int available = me.mana + me.bankedMana + ("spell".equals(def.kind) ? me.spellMana : 0);
        if (available < def.cost) throw new IllegalStateException("Not enough mana");

        if ("unit".equals(def.kind) || "structure".equals(def.kind)) {
            int slot = action.has("targetSlot") ? action.get("targetSlot")
                .getAsInt() : firstEmpty(me.board);
            if (slot < 0 || slot >= BOARD || me.board[slot] != null) {
                slot = firstEmpty(me.board);
            }
            if (slot < 0 || slot >= BOARD || me.board[slot] != null) {
                throw new IllegalStateException("No empty slot");
            }
            return slot;
        }
        if ("spell".equals(def.kind)) validateSpellTarget(s, actor, def, action);
        return -1;
    }

    private static void playCard(BattleState s, int actor, JsonObject action, Random rng) {
        if (!"main".equals(s.phase) && !isResponsePhase(s.phase)) {
            throw new IllegalStateException("Invalid phase " + s.phase);
        }
        requireActive(s, actor);
        PlayerState me = s.players[actor];
        int handIndex = action.get("handIndex")
            .getAsInt();
        if (handIndex < 0 || handIndex >= me.hand.size()) throw new IllegalArgumentException("hand");
        String cardId = me.hand.get(handIndex);
        CardDef def = CardCatalog.get(cardId);
        if (def == null) throw new IllegalArgumentException("Unknown card");
        boolean response = isResponsePhase(s.phase);
        if (response && !"spell".equals(def.kind)) {
            throw new IllegalStateException("Only spells may be played in a response window");
        }
        String speed = "spell".equals(def.kind) ? spellSpeed(def) : null;
        if (response && "slow".equals(speed)) {
            throw new IllegalStateException("Slow spells cannot be played in a response window");
        }
        int unitSlot = validatePlayCard(s, actor, def, action);
        if (!spendMana(me, def.cost, "spell".equals(def.kind))) throw new IllegalStateException("Not enough mana");
        me.hand.remove(handIndex);
        s.log.add(me.name + " plays " + (def.nameZh != null ? def.nameZh : def.id));
        if ("unit".equals(def.kind) || "structure".equals(def.kind)) {
            me.board[unitSlot] = makeUnit(def);
        } else if ("burst".equals(speed)) {
            me.discard.add(cardId);
            applySpell(s, actor, def, action, rng);
        } else {
            SpellStackItem item = new SpellStackItem();
            item.stackId = s.nextStackId++;
            item.caster = actor;
            item.cardId = cardId;
            item.speed = speed;
            if (action.has("targetSlot")) item.targetSlot = Integer.valueOf(action.get("targetSlot")
                .getAsInt());
            if (action.has("targetEnemySlot")) item.targetEnemySlot = Integer.valueOf(action.get("targetEnemySlot")
                .getAsInt());
            s.spellStack.add(item);
            if (!response) {
                s.phase = "spell_response";
                s.responseOriginPlayer = actor;
            }
            s.responsePasses = 0;
            s.consecutivePasses = 0;
            s.activePlayer = opp(actor);
            s.log.add((def.nameZh != null ? def.nameZh : def.id) + " enters the spell stack");
            return;
        }
        removeDead(s.players[0], s);
        removeDead(s.players[1], s);
        checkWinner(s);
        if (s.winner == null) {
            s.consecutivePasses = 0;
            if (response) s.responsePasses = 0;
            if (!"burst".equals(speed)) s.activePlayer = opp(actor);
        }
    }

    private static JsonObject actionForStackItem(SpellStackItem item) {
        JsonObject action = new JsonObject();
        action.addProperty("type", "play_card");
        action.addProperty("handIndex", -1);
        if (item.targetSlot != null) action.addProperty("targetSlot", item.targetSlot.intValue());
        if (item.targetEnemySlot != null) action.addProperty("targetEnemySlot", item.targetEnemySlot.intValue());
        return action;
    }

    private static void discardUnresolvedStack(BattleState s) {
        while (!s.spellStack.isEmpty()) {
            SpellStackItem item = s.spellStack.remove(s.spellStack.size() - 1);
            s.players[item.caster].discard.add(item.cardId);
            s.log.add(item.cardId + " is cancelled because the match ended");
        }
    }

    private static void resolveSpellStack(BattleState s) {
        String responsePhase = s.phase;
        if (!isResponsePhase(responsePhase)) throw new IllegalStateException("No response window");
        Integer origin = s.responseOriginPlayer;
        s.phase = "resolve";
        while (!s.spellStack.isEmpty()) {
            SpellStackItem item = s.spellStack.remove(s.spellStack.size() - 1);
            CardDef def = CardCatalog.get(item.cardId);
            JsonObject action = actionForStackItem(item);
            boolean fizzled = def == null;
            if (def != null) {
                try {
                    validateSpellTarget(s, item.caster, def, action);
                } catch (RuntimeException ignored) {
                    fizzled = true;
                }
            }
            if (def != null && !fizzled) {
                applySpell(s, item.caster, def, action, new Random(s.seed + s.turn * 41L + item.stackId));
                s.log.add((def.nameZh != null ? def.nameZh : def.id) + " resolves from the spell stack");
            } else {
                s.log.add(item.cardId + " fizzles because its target is no longer legal");
            }
            s.players[item.caster].discard.add(item.cardId);
            removeDead(s.players[0], s);
            removeDead(s.players[1], s);
            checkWinner(s);
            if (s.winner != null) {
                discardUnresolvedStack(s);
                s.responsePasses = 0;
                s.responseOriginPlayer = null;
                return;
            }
        }
        s.responsePasses = 0;
        s.responseOriginPlayer = null;
        if ("combat_response".equals(responsePhase)) {
            resolveCombat(s);
        } else {
            s.phase = "main";
            if (origin != null) s.activePlayer = opp(origin.intValue());
        }
    }

    private static void openCombatResponse(BattleState s) {
        if (s.combatAttacker < 0) throw new IllegalStateException("No combat attacker");
        s.phase = "combat_response";
        s.activePlayer = s.combatAttacker;
        s.responsePasses = 0;
        s.responseOriginPlayer = null;
        s.spellStack.clear();
        s.log.add("Combat response window opens");
    }

    private static boolean applyDataEffect(BattleState s, int actor, CardDef def, int enemySlot, int mySlot,
        Random rng) {
        SpellEffect effect = def.effect;
        if (effect == null || effect.id == null) return false;

        PlayerState me = s.players[actor];
        PlayerState you = s.players[opp(actor)];
        BoardUnit friend = me.board[mySlot];
        BoardUnit enemy = you.board[enemySlot];
        int amount = effect.amount != null ? effect.amount.intValue() : 0;
        int amount2 = effect.amount2 != null ? effect.amount2.intValue() : 0;
        String eid = effect.id;

        if ("damage_unit".equals(eid)) {
            if (enemy != null && !enemy.untargetable) dealDmg(enemy, amount);
        } else if ("heal_unit".equals(eid)) {
            if (friend != null) friend.health = Math.min(friend.maxHealth, friend.health + amount);
        } else if ("buff_unit".equals(eid)) {
            if (friend != null && !friend.isStructure) {
                friend.attack += amount;
                friend.health += amount2;
                friend.maxHealth += amount2;
                if (effect.keywordsAdd != null) {
                    for (String kw : effect.keywordsAdd) {
                        if (!friend.keywords.contains(kw)) friend.keywords.add(kw);
                    }
                }
            }
        } else if ("buff_all".equals(eid)) {
            for (BoardUnit u : me.board) {
                if (u != null && !u.isStructure) {
                    u.attack += amount;
                    if (amount2 != 0) {
                        u.health += amount2;
                        u.maxHealth += amount2;
                    }
                }
            }
        } else if ("armor_unit".equals(eid)) {
            if (friend != null) friend.armor += amount;
        } else if ("draw".equals(eid)) {
            draw(me, Math.max(1, amount != 0 ? amount : 1), s);
        } else if ("gain_mana".equals(eid)) {
            me.mana += amount;
        } else if ("nexus_damage".equals(eid)) {
            int dmg = VoltageRules.applyNexusDamage(amount, me.voltage, you.voltage, you.damageReductionPct);
            you.nexusHp -= dmg;
            if (you.reflectToNexus) me.nexusHp -= Math.max(1, dmg / 2);
        } else if ("nexus_heal".equals(eid)) {
            me.nexusHp = Math.min(me.maxNexusHp, me.nexusHp + amount);
        } else if ("nexus_max_heal".equals(eid)) {
            me.maxNexusHp += amount;
            me.nexusHp = Math.min(me.maxNexusHp, me.nexusHp + amount);
        } else if ("summon_token".equals(eid) || "summon_tokens".equals(eid)) {
            String tokenId = effect.tokenCardId != null ? effect.tokenCardId : "ge_larva";
            int count = effect.tokenCount != null ? effect.tokenCount.intValue()
                : ("summon_token".equals(eid) ? 1 : 2);
            CardDef tokenDef = CardCatalog.get(tokenId);
            if (tokenDef != null) {
                for (int n = 0; n < count; n++) {
                    compactBoard(me.board);
                    int empty = firstEmpty(me.board);
                    if (empty >= 0) me.board[empty] = makeUnit(tokenDef);
                }
            }
        } else if ("strip_stealth".equals(eid)) {
            if (enemy != null) enemy.keywords.remove("stealth");
        } else if ("destroy_machine".equals(eid)) {
            if (enemy != null && hasKw(enemy, "machine")) {
                you.board[enemySlot] = null;
                you.discard.add(enemy.cardId);
                compactBoard(you.board);
                s.log.add("Machine dismantled");
            }
        } else if ("hive_cooldown".equals(eid)) {
            int reduce = Math.max(1, amount != 0 ? amount : 1);
            if ("friendly_cooldown".equals(effect.target) && friend != null && friend.hiveTurnsLeft != null) {
                friend.hiveTurnsLeft = Math.max(0, friend.hiveTurnsLeft.intValue() - reduce);
            } else {
                for (BoardUnit u : me.board) {
                    if (u != null && hasKw(u, "beehive") && u.hiveTurnsLeft != null) {
                        u.hiveTurnsLeft = Math.max(0, u.hiveTurnsLeft.intValue() - reduce);
                    }
                }
            }
        } else if ("add_aspects".equals(eid)) {
            if (friend != null && effect.aspects != null) {
                for (String a : effect.aspects) {
                    if (!friend.aspects.contains(a)) friend.aspects.add(a);
                }
            }
        } else if ("damage_and_aspect".equals(eid)) {
            if (enemy != null && !enemy.untargetable) {
                dealDmg(enemy, amount);
                if (effect.aspects != null) {
                    for (String a : effect.aspects) {
                        if (!enemy.aspects.contains(a)) enemy.aspects.add(a);
                    }
                }
            }
        } else if ("singularity".equals(eid)) {
            me.singularitiesPlayed += 1;
            if (me.singularitiesPlayed >= 3) me.eternalReady = true;
            s.log.add("Singularities " + me.singularitiesPlayed + "/3");
        } else if ("eternal".equals(eid)) {
            if (me.eternalReady || me.singularitiesPlayed >= 3) {
                me.eternalActive = true;
                s.log.add("Eternal Singularity ACTIVE — units instantly kill blockers");
            } else {
                s.log.add("Eternal Singularity fizzled — need 3 singularities");
            }
        } else if ("ae_generate".equals(eid)) {
            String pick = pickAe(s, rng);
            if (pick != null) addToHand(me, pick, s);
        } else if ("steal_attack_token".equals(eid)) {
            s.attackTokenPlayer = actor;
            s.attackTokenAvailable = true;
            s.log.add("DLB steals the attack token!");
            if (amount > 0) draw(me, amount, s);
        } else if ("enemy_lose_mana".equals(eid)) {
            you.mana = Math.max(0, you.mana - Math.max(1, amount != 0 ? amount : 1));
        } else if ("random_enemy_damage".equals(eid)) {
            List<Integer> slots = new ArrayList<Integer>();
            for (int i = 0; i < you.board.length; i++) {
                BoardUnit u = you.board[i];
                if (u != null && !u.untargetable) slots.add(Integer.valueOf(i));
            }
            if (!slots.isEmpty()) {
                int slot = slots.get(rng.nextInt(slots.size()))
                    .intValue();
                BoardUnit u = you.board[slot];
                if (u != null) dealDmg(u, Math.max(1, amount != 0 ? amount : 1));
            }
        } else if ("damage_reduction".equals(eid)) {
            me.damageReductionPct = Math.min(50, me.damageReductionPct + (amount != 0 ? amount : 10));
        } else if ("reflect".equals(eid)) {
            me.reflectToNexus = true;
        } else if ("clone_unit".equals(eid)) {
            BoardUnit src = friend;
            compactBoard(me.board);
            int empty = firstEmpty(me.board);
            if (src != null && empty >= 0) {
                BoardUnit clone = new BoardUnit();
                clone.instanceId = UUID.randomUUID()
                    .toString();
                clone.cardId = src.cardId;
                clone.attack = 1;
                clone.health = 1;
                clone.maxHealth = 1;
                clone.armor = src.armor;
                clone.keywords = new ArrayList<String>(src.keywords);
                clone.aspects = new ArrayList<String>(src.aspects);
                clone.isStructure = src.isStructure;
                clone.untargetable = src.untargetable;
                clone.hiveTurnsLeft = src.hiveTurnsLeft;
                me.board[empty] = clone;
            }
        } else if ("reduce_dlb_interval".equals(eid)) {
            if (s.dlbForceEvery > 3) s.dlbForceEvery -= Math.max(1, amount != 0 ? amount : 1);
        } else {
            return false;
        }
        return true;
    }

    private static void applySpell(BattleState s, int actor, CardDef def, JsonObject action, Random rng) {
        PlayerState me = s.players[actor];
        PlayerState you = s.players[opp(actor)];
        int enemySlot = action.has("targetEnemySlot") ? action.get("targetEnemySlot")
            .getAsInt() : 0;
        int mySlot = action.has("targetSlot") ? action.get("targetSlot")
            .getAsInt() : 0;
        String id = def.id;
        if ("fo_plugin_strong".equals(id)) {
            me.nextBeeMutate = true;
            return;
        }
        if (def.effect != null && def.effect.id != null && applyDataEffect(s, actor, def, enemySlot, mySlot, rng)) {
            return;
        }
        if ("van_smite".equals(id) || "ae_annihilation".equals(id)) {
            BoardUnit u = you.board[enemySlot];
            if (u != null && !u.untargetable) dealDmg(u, "van_smite".equals(id) ? 3 : 2);
        } else if ("van_heal".equals(id)) {
            BoardUnit u = me.board[mySlot];
            if (u != null) u.health = Math.min(u.maxHealth, u.health + 3);
        } else if ("van_rally".equals(id)) {
            for (BoardUnit u : me.board) if (u != null && !u.isStructure) u.attack += 1;
        } else if ("gt_overclock".equals(id) || "as_lens".equals(id)) {
            me.mana += "gt_overclock".equals(id) ? 2 : 1;
        } else if ("gt_wrench".equals(id)) {
            BoardUnit u = you.board[enemySlot];
            if (u != null && hasKw(u, "machine")) {
                you.discard.add(u.cardId);
                you.board[enemySlot] = null;
                compactBoard(you.board);
            }
        } else if ("th_ordo_aer".equals(id)) {
            BoardUnit u = me.board[mySlot];
            if (u != null) {
                if (!u.aspects.contains("ordo")) u.aspects.add("ordo");
                if (!u.aspects.contains("aer")) u.aspects.add("aer");
            }
        } else if ("th_ignis".equals(id)) {
            BoardUnit u = you.board[enemySlot];
            if (u != null && !u.untargetable) {
                dealDmg(u, 2);
                if (!u.aspects.contains("ignis")) u.aspects.add("ignis");
            }
        } else if ("th_ward".equals(id)) {
            BoardUnit u = me.board[mySlot];
            if (u != null) u.armor += 2;
        } else if ("fo_plugin_speed".equals(id)) {
            for (BoardUnit u : me.board) {
                if (u != null && u.hiveTurnsLeft != null) {
                    u.hiveTurnsLeft = Math.max(0, u.hiveTurnsLeft.intValue() - 1);
                }
            }
        } else if ("ee_watch".equals(id) || "ee_relay".equals(id)) {
            BoardUnit u = me.board[mySlot];
            if (u != null && u.hiveTurnsLeft != null) {
                int cut = "ee_watch".equals(id) ? 2 : 1;
                u.hiveTurnsLeft = Math.max(0, u.hiveTurnsLeft.intValue() - cut);
            }
        } else if ("fo_smoke".equals(id)) {
            BoardUnit u = you.board[enemySlot];
            if (u != null) u.keywords.remove("stealth");
        } else if ("as_shield".equals(id)) {
            me.damageReductionPct = Math.min(50, me.damageReductionPct + 10);
        } else if ("as_reflect".equals(id)) {
            me.reflectToNexus = true;
        } else if ("as_attune".equals(id) || "ae_p2p".equals(id) || "ee_trans".equals(id) || "av_catalyst".equals(id)
            || "ee_phil".equals(id)) {
            draw(me, 1, s);
        } else if ("as_ritual".equals(id)) {
            for (BoardUnit u : me.board) {
                if (u != null && !u.isStructure) {
                    u.attack += 1;
                    u.health += 1;
                    u.maxHealth += 1;
                }
            }
        } else if ("as_nova".equals(id) || "dlb_ignore".equals(id)) {
            int raw = "as_nova".equals(id) ? 2 : 3;
            int dmg = VoltageRules.applyNexusDamage(raw, me.voltage, you.voltage, you.damageReductionPct);
            you.nexusHp -= dmg;
            if (you.reflectToNexus) me.nexusHp -= Math.max(1, dmg / 2);
        } else if (id != null && id.startsWith("av_singularity")) {
            me.singularitiesPlayed += 1;
            if (me.singularitiesPlayed >= 3) me.eternalReady = true;
        } else if ("av_eternal".equals(id)) {
            if (me.eternalReady || me.singularitiesPlayed >= 3) {
                me.eternalActive = true;
                s.log.add("Eternal Singularity ACTIVE");
            }
        } else if ("av_armor".equals(id)) {
            me.maxNexusHp += 5;
            me.nexusHp = Math.min(me.maxNexusHp, me.nexusHp + 5);
        } else if ("ee_klein".equals(id)) {
            me.mana += 3;
        } else if ("ee_catalyst".equals(id)) {
            me.nexusHp = Math.min(me.maxNexusHp, me.nexusHp + 2);
        } else if ("ge_mutate".equals(id)) {
            BoardUnit u = me.board[mySlot];
            if (u != null) {
                u.attack += 1;
                u.health += 1;
                u.maxHealth += 1;
            }
        } else if ("ge_clone".equals(id)) {
            BoardUnit src = me.board[mySlot];
            int empty = firstEmpty(me.board);
            if (src != null && empty >= 0) {
                BoardUnit clone = makeUnit(CardCatalog.get(src.cardId));
                clone.attack = 1;
                clone.health = 1;
                clone.maxHealth = 1;
                clone.keywords = new ArrayList<String>(src.keywords);
                clone.aspects = new ArrayList<String>(src.aspects);
                clone.equipment.clear();
                me.board[empty] = clone;
            }
        } else if ("ge_swarm".equals(id)) {
            CardDef larva = CardCatalog.get("ge_larva");
            for (int n = 0; n < 2; n++) {
                int empty = firstEmpty(me.board);
                if (empty >= 0 && larva != null) me.board[empty] = makeUnit(larva);
            }
        } else if ("ge_split".equals(id) || "ae_cell".equals(id)) {
            draw(me, 2, s);
        } else if ("ae_craft".equals(id) || "ae_wireless".equals(id)) {
            String pick = pickAe(s, rng);
            if (pick != null) addToHand(me, pick, s);
        } else if ("ae_formation".equals(id)) {
            CardDef ball = CardCatalog.get("ae_matter");
            int empty = firstEmpty(me.board);
            if (empty >= 0 && ball != null) me.board[empty] = makeUnit(ball);
        } else if ("dlb_tantrum".equals(id) || "dlb_scream".equals(id)) {
            s.attackTokenPlayer = actor;
            s.attackTokenAvailable = true;
            s.log.add("DLB steals the attack token!");
            if ("dlb_scream".equals(id)) draw(me, 1, s);
        } else if ("dlb_mood".equals(id)) {
            List<Integer> slots = new ArrayList<Integer>();
            for (int i = 0; i < you.board.length; i++) if (you.board[i] != null) slots.add(i);
            if (!slots.isEmpty()) {
                int slot = slots.get(rng.nextInt(slots.size()))
                    .intValue();
                BoardUnit u = you.board[slot];
                if (u != null && !u.untargetable) dealDmg(u, 1);
            }
        } else if ("dlb_nap".equals(id)) {
            you.mana = Math.max(0, you.mana - 1);
        }
    }

    private static String pickAe(BattleState s, Random rng) {
        if (s.aePool.isEmpty()) return null;
        return s.aePool.get(rng.nextInt(s.aePool.size()));
    }

    private static void startAttack(BattleState s, int actor) {
        requirePhase(s, "main");
        requireActive(s, actor);
        if (!s.attackTokenAvailable || s.attackTokenPlayer != actor) {
            throw new IllegalStateException("No attack token");
        }
        s.phase = "attack_declare";
        s.combatAttacker = actor;
        s.consecutivePasses = 0;
        s.attackOrder.clear();
        s.attackOrderIds.clear();
        s.blockPairs.clear();
        s.swapUsedThisCombat = false;
    }

    private static void declareAttacks(BattleState s, int actor, JsonObject action) {
        requirePhase(s, "attack_declare");
        if (s.combatAttacker != actor) throw new IllegalStateException("Not attacker");
        PlayerState me = s.players[actor];
        JsonArray slots = action.getAsJsonArray("slots");
        HashSet<Integer> unique = new HashSet<Integer>();
        s.attackOrder.clear();
        s.attackOrderIds.clear();
        if (slots != null) {
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.get(i)
                    .getAsInt();
                if (!unique.add(slot)) continue;
                BoardUnit u = me.board[slot];
                if (u == null || u.isStructure || u.attack <= 0) throw new IllegalArgumentException("bad attacker");
                s.attackOrder.add(slot);
                s.attackOrderIds.add(u.instanceId);
            }
        }
        if (s.attackOrder.isEmpty()) {
            s.phase = "main";
            s.combatAttacker = -1;
            s.blockPairs.clear();
            s.log.add(me.name + " cancels the attack declaration");
            return;
        }
        s.phase = "block_declare";
        s.activePlayer = opp(actor);
    }

    private static boolean canBlock(BoardUnit atk, BoardUnit blk) {
        if (blk.isStructure) return false;
        if (hasKw(atk, "stealth") && !hasKw(blk, "stealth")) return false;
        return true;
    }

    private static void declareBlocks(BattleState s, int blocker, JsonObject action) {
        requirePhase(s, "block_declare");
        if (s.combatAttacker < 0 || blocker != opp(s.combatAttacker)) throw new IllegalStateException("Not defender");
        PlayerState atkP = s.players[s.combatAttacker];
        PlayerState defP = s.players[blocker];
        Map<Integer, Integer> mapped = new HashMap<Integer, Integer>();
        HashSet<Integer> used = new HashSet<Integer>();
        JsonArray pairs = action.getAsJsonArray("pairs");
        if (pairs != null) {
            for (int i = 0; i < pairs.size(); i++) {
                JsonObject p = pairs.get(i)
                    .getAsJsonObject();
                int aSlot = p.get("attackerSlot")
                    .getAsInt();
                int bSlot = p.get("blockerSlot")
                    .getAsInt();
                if (!s.attackOrder.contains(Integer.valueOf(aSlot))) throw new IllegalArgumentException("atk");
                if (bSlot >= 0) {
                    if (!used.add(bSlot)) throw new IllegalArgumentException("blocker reuse");
                    BoardUnit bu = defP.board[bSlot];
                    BoardUnit au = atkP.board[aSlot];
                    if (bu == null || au == null || bu.untargetable || !canBlock(au, bu)) {
                        throw new IllegalArgumentException("illegal block");
                    }
                    mapped.put(aSlot, bSlot);
                }
            }
        }
        s.blockPairs.clear();
        for (Integer a : s.attackOrder) {
            Integer b = mapped.get(a);
            s.blockPairs.add(new AttackPair(a.intValue(), b != null ? b.intValue() : -1));
        }
        boolean canSwap = false;
        for (BoardUnit u : defP.board) {
            if (hasOrdoAer(u)) {
                canSwap = true;
                break;
            }
        }
        if (canSwap && !s.swapUsedThisCombat) {
            s.phase = "swap_extra";
        } else {
            openCombatResponse(s);
        }
    }

    private static void swapSlots(BattleState s, int actor, int a, int b) {
        requirePhase(s, "swap_extra");
        if (s.combatAttacker < 0 || actor != opp(s.combatAttacker)) throw new IllegalStateException("Only defender");
        PlayerState p = s.players[actor];
        boolean ok = false;
        for (BoardUnit u : p.board) if (hasOrdoAer(u)) ok = true;
        if (!ok) throw new IllegalStateException("Need Ordo+Aer");
        BoardUnit tmp = p.board[a];
        p.board[a] = p.board[b];
        p.board[b] = tmp;
        s.swapUsedThisCombat = true;
        s.log.add(p.name + " swapped " + a + "<->" + b);
        openCombatResponse(s);
    }

    private static void passSwap(BattleState s, int actor) {
        requirePhase(s, "swap_extra");
        if (s.combatAttacker < 0 || actor != opp(s.combatAttacker)) {
            throw new IllegalStateException("Only defender may skip the swap");
        }
        s.log.add(s.players[actor].name + " skips the mystic swap");
        openCombatResponse(s);
    }

    private static void resolveCombat(BattleState s) {
        s.phase = "resolve";
        int atkIdx = s.combatAttacker;
        if (atkIdx < 0) throw new IllegalStateException("No combat attacker");
        int defIdx = opp(atkIdx);
        PlayerState atk = s.players[atkIdx];
        PlayerState def = s.players[defIdx];
        List<AttackPair> pairs = s.blockPairs;
        if (pairs.isEmpty()) {
            pairs = new ArrayList<AttackPair>();
            for (Integer a : s.attackOrder) pairs.add(new AttackPair(a.intValue(), -1));
        }
        for (AttackPair pair : pairs) {
            BoardUnit attacker = atk.board[pair.attackerSlot];
            if (attacker == null) continue;
            if (pair.blockerSlot < 0) {
                int dmg = VoltageRules.applyNexusDamage(attacker.attack, atk.voltage, def.voltage, def.damageReductionPct);
                if (atk.eternalActive) dmg = Math.max(dmg, 999);
                def.nexusHp -= dmg;
                s.log.add(attacker.cardId + " hits nexus for " + dmg);
                if (hasKw(attacker, "lifesteal")) atk.nexusHp = Math.min(atk.maxNexusHp, atk.nexusHp + dmg);
                if (def.reflectToNexus) atk.nexusHp -= Math.max(1, dmg / 2);
                continue;
            }
            BoardUnit blocker = def.board[pair.blockerSlot];
            if (blocker == null) {
                s.log.add(attacker.cardId + " remains blocked after its blocker left combat");
                continue;
            }
            if (atk.eternalActive) {
                blocker.health = 0;
                s.log.add("Eternal kill " + blocker.cardId);
            } else {
                dealDmg(blocker, attacker.attack);
                dealDmg(attacker, blocker.attack);
                if (hasKw(attacker, "lifesteal")) {
                    atk.nexusHp = Math.min(atk.maxNexusHp, atk.nexusHp + attacker.attack);
                }
                if (hasKw(attacker, "aoe")) {
                    for (int adj : new int[] { pair.blockerSlot - 1, pair.blockerSlot + 1 }) {
                        if (adj < 0 || adj >= BOARD) continue;
                        BoardUnit adjU = def.board[adj];
                        if (adjU != null && !adjU.untargetable) {
                            dealDmg(adjU, Math.max(1, attacker.attack / 2));
                        }
                    }
                }
            }
        }
        removeDead(atk, s);
        removeDead(def, s);
        checkWinner(s);
        if (s.winner != null) return;
        s.attackTokenAvailable = false;
        s.combatAttacker = -1;
        s.attackOrder.clear();
        s.attackOrderIds.clear();
        s.blockPairs.clear();
        s.consecutivePasses = 0;
        s.responsePasses = 0;
        s.responseOriginPlayer = null;
        s.spellStack.clear();
        s.activePlayer = defIdx;
        s.phase = "main";
    }

    private static void endRound(BattleState s) {
        s.phase = "turn_end";
        for (int i = 0; i < s.players.length; i++) {
            processStructures(s, s.players[i], new Random(s.seed + s.turn * 17L + i));
            bankRoundMana(s.players[i], s);
        }
        int nextToken = opp(s.attackTokenPlayer);
        if (s.dlbForceEvery > 0 && s.turn - s.lastForcedSwapTurn >= s.dlbForceEvery) {
            s.lastForcedSwapTurn = s.turn;
            nextToken = s.attackTokenPlayer;
            s.log.add("DLB schedule: previous attacker keeps the token");
        }
        s.turn += 1;
        s.attackTokenPlayer = nextToken;
        s.attackTokenAvailable = true;
        s.activePlayer = nextToken;
        s.combatAttacker = -1;
        s.consecutivePasses = 0;
        s.responsePasses = 0;
        s.responseOriginPlayer = null;
        s.spellStack.clear();
        for (PlayerState player : s.players) {
            player.maxMana = Math.min(10, player.maxMana + 1);
            player.mana = player.maxMana;
            for (BoardUnit unit : player.board) {
                if (unit != null) unit.health = unit.maxHealth;
            }
            draw(player, 1, s);
            if (s.winner != null) return;
        }
        boolean totem = false;
        for (PlayerState player : s.players) {
            for (BoardUnit u : player.board) if (u != null && "dlb_chaos".equals(u.cardId)) totem = true;
        }
        if (totem && s.dlbForceEvery > 3) s.dlbForceEvery -= 1;
        s.attackOrder.clear();
        s.attackOrderIds.clear();
        s.blockPairs.clear();
        s.phase = "main";
        checkWinner(s);
    }

    private static void passPriority(BattleState s, int actor) {
        if (!"main".equals(s.phase) && !isResponsePhase(s.phase)) {
            throw new IllegalStateException("Invalid phase " + s.phase);
        }
        requireActive(s, actor);
        if (isResponsePhase(s.phase)) {
            s.responsePasses += 1;
            s.log.add(s.players[actor].name + " passes response priority");
            if (s.responsePasses >= 2) {
                resolveSpellStack(s);
            } else {
                s.activePlayer = opp(actor);
            }
            return;
        }
        s.consecutivePasses += 1;
        s.log.add(s.players[actor].name + " passes priority");
        if (s.consecutivePasses >= 2) {
            endRound(s);
        } else {
            s.activePlayer = opp(actor);
        }
    }

    private static void processStructures(BattleState s, PlayerState p, Random rng) {
        for (int i = 0; i < p.board.length; i++) {
            BoardUnit u = p.board[i];
            if (u == null) continue;
            CardDef def = CardCatalog.get(u.cardId);
            if (def != null && def.manaPerTurn != null) p.mana += def.manaPerTurn.intValue();
            if ("as_crystal".equals(u.cardId)) p.nexusHp = Math.min(p.maxNexusHp, p.nexusHp + 1);
            if ("th_node".equals(u.cardId)) {
                List<BoardUnit> units = new ArrayList<BoardUnit>();
                for (BoardUnit x : p.board) if (x != null && !x.isStructure) units.add(x);
                if (!units.isEmpty()) {
                    BoardUnit t = units.get(rng.nextInt(units.size()));
                    if (!t.aspects.contains("ordo")) t.aspects.add("ordo");
                }
            }
            if ("ae_inscriber".equals(u.cardId) || "ae_controller".equals(u.cardId)) {
                String pick = pickAe(s, rng);
                if (pick != null) addToHand(p, pick, s);
            }
            if ("ee_relay".equals(u.cardId)) {
                for (BoardUnit candidate : p.board) {
                    if (candidate != null && hasKw(candidate, "beehive")
                        && candidate.hiveTurnsLeft != null && candidate.hiveTurnsLeft.intValue() > 0) {
                        candidate.hiveTurnsLeft = Integer.valueOf(Math.max(0, candidate.hiveTurnsLeft.intValue() - 1));
                        break;
                    }
                }
            }
            if (hasKw(u, "beehive") && u.hiveTurnsLeft != null) {
                u.hiveTurnsLeft = Integer.valueOf(u.hiveTurnsLeft.intValue() - 1);
                if (u.hiveTurnsLeft.intValue() <= 0) {
                    u.hiveTurnsLeft = def != null && def.hiveCooldown != null ? def.hiveCooldown : Integer.valueOf(3);
                    int existing = -1;
                    for (int bi = 0; bi < p.board.length; bi++) {
                        BoardUnit bee = p.board[bi];
                        if (bee != null && hasKw(bee, "bee") && bee.attack == 1 && bee.health == 1) {
                            existing = bi;
                            break;
                        }
                    }
                    if (existing >= 0) {
                        BoardUnit bee = p.board[existing];
                        bee.attack += 1;
                        bee.health += 1;
                        bee.maxHealth += 1;
                        s.log.add("Bees merged");
                    } else {
                        int empty = firstEmpty(p.board);
                        CardDef beeDef = CardCatalog.get("fo_bee");
                        if (empty >= 0 && beeDef != null) {
                            BoardUnit bee = makeUnit(beeDef);
                            if (p.nextBeeMutate) {
                                bee.keywords.add(rng.nextBoolean() ? "lifesteal" : "aoe");
                                p.nextBeeMutate = false;
                            }
                            p.board[empty] = bee;
                            s.log.add("Hive produced a bee");
                        }
                    }
                }
            }
        }
    }

    private static void bankRoundMana(PlayerState p, BattleState s) {
        int spellReserve = Math.min(3 - p.spellMana, p.mana);
        p.spellMana += spellReserve;
        p.mana -= spellReserve;
        boolean hasCap = false;
        boolean battery = false;
        for (BoardUnit u : p.board) {
            if (u != null && hasKw(u, "capacitor")) hasCap = true;
            if (u != null && "gt_battery".equals(u.cardId)) battery = true;
        }
        if (!hasCap) {
            p.mana = 0;
            return;
        }
        p.bankedMana += p.mana;
        p.mana = 0;
        int limit = battery ? 4 : MAX_BANK;
        if (p.bankedMana > limit) {
            int over = p.bankedMana - limit;
            p.bankedMana = limit / 2;
            p.nexusHp -= over * 2;
            for (int i = 0; i < p.board.length; i++) {
                if (p.board[i] != null && hasKw(p.board[i], "machine")) {
                    p.discard.add(p.board[i].cardId);
                    p.board[i] = null;
                    compactBoard(p.board);
                    s.log.add(p.name + " capacitor overload!");
                    break;
                }
            }
        }
    }

    public static void runAi(BattleState s) {
        for (int n = 0; n < 40; n++) {
            if ("game_over".equals(s.phase)) return;
            if ("mulligan".equals(s.phase)) {
                int actor = -1;
                for (int i = 0; i < s.players.length; i++) {
                    if (s.players[i].isAi && !s.mulliganDone[i]) {
                        actor = i;
                        break;
                    }
                }
                if (actor < 0) return;
                JsonArray indices = new JsonArray();
                PlayerState player = s.players[actor];
                for (int i = 0; i < player.hand.size(); i++) {
                    CardDef def = CardCatalog.get(player.hand.get(i));
                    if (def != null && def.cost >= 5) {
                        indices.add(new com.google.gson.JsonPrimitive(i));
                    }
                }
                JsonObject act = new JsonObject();
                act.addProperty("type", "confirm_mulligan");
                act.add("replaceIndices", indices);
                applyActionNoAi(s, actor, act);
                continue;
            }
            if ("attack_declare".equals(s.phase)) {
                int actor = s.combatAttacker;
                if (actor < 0 || !s.players[actor].isAi) return;
                PlayerState p = s.players[actor];
                JsonArray slots = new JsonArray();
                for (int i = 0; i < p.board.length; i++) {
                    BoardUnit u = p.board[i];
                    if (u != null && !u.isStructure && u.attack > 0) slots.add(new com.google.gson.JsonPrimitive(i));
                }
                JsonObject act = new JsonObject();
                act.addProperty("type", "declare_attacks");
                act.add("slots", slots);
                applyActionNoAi(s, actor, act);
                continue;
            }
            if ("block_declare".equals(s.phase)) {
                if (s.combatAttacker < 0) return;
                int defender = opp(s.combatAttacker);
                if (!s.players[defender].isAi) return;
                PlayerState defP = s.players[defender];
                JsonArray pairs = new JsonArray();
                HashSet<Integer> used = new HashSet<Integer>();
                for (Integer aSlot : s.attackOrder) {
                    BoardUnit attacker = s.players[s.combatAttacker].board[aSlot.intValue()];
                    int block = -1;
                    for (int i = 0; i < defP.board.length; i++) {
                        BoardUnit bu = defP.board[i];
                        if (bu == null || used.contains(i) || bu.isStructure || bu.untargetable) continue;
                        if (attacker != null && canBlock(attacker, bu)) {
                            block = i;
                            used.add(i);
                            break;
                        }
                    }
                    JsonObject pair = new JsonObject();
                    pair.addProperty("attackerSlot", aSlot.intValue());
                    pair.addProperty("blockerSlot", block);
                    pairs.add(pair);
                }
                JsonObject act = new JsonObject();
                act.addProperty("type", "declare_blocks");
                act.add("pairs", pairs);
                applyActionNoAi(s, defender, act);
                continue;
            }
            if ("swap_extra".equals(s.phase)) {
                if (s.combatAttacker < 0) return;
                int defender = opp(s.combatAttacker);
                if (s.players[defender].isAi) passSwap(s, defender);
                else return;
                continue;
            }
            if (isResponsePhase(s.phase)) {
                int actor = s.activePlayer;
                PlayerState p = s.players[actor];
                if (!p.isAi) return;
                boolean played = false;
                for (int i = 0; i < p.hand.size(); i++) {
                    CardDef def = CardCatalog.get(p.hand.get(i));
                    if (def == null || !"spell".equals(def.kind) || "slow".equals(spellSpeed(def))) continue;
                    int available = p.mana + p.bankedMana + p.spellMana;
                    if (available < def.cost) continue;
                    try {
                        JsonObject act = new JsonObject();
                        act.addProperty("type", "play_card");
                        act.addProperty("handIndex", i);
                        act.addProperty("targetSlot", 0);
                        act.addProperty("targetEnemySlot", 0);
                        applyActionNoAi(s, actor, act);
                        played = true;
                        break;
                    } catch (Throwable ignored) {}
                }
                if (!played) passPriority(s, actor);
                continue;
            }
            if (!"main".equals(s.phase)) return;

            int actor = s.activePlayer;
            PlayerState p = s.players[actor];
            if (!p.isAi) return;
            boolean hasAttacker = false;
            for (BoardUnit u : p.board) {
                if (u != null && !u.isStructure && u.attack > 0) {
                    hasAttacker = true;
                    break;
                }
            }
            if (s.attackTokenAvailable && s.attackTokenPlayer == actor && hasAttacker) {
                startAttack(s, actor);
                continue;
            }

            boolean played = false;
            for (int i = 0; i < p.hand.size(); i++) {
                CardDef def = CardCatalog.get(p.hand.get(i));
                int available = p.mana + p.bankedMana
                    + (def != null && "spell".equals(def.kind) ? p.spellMana : 0);
                if (def == null || available < def.cost) continue;
                try {
                    JsonObject act = new JsonObject();
                    act.addProperty("type", "play_card");
                    act.addProperty("handIndex", i);
                    if ("unit".equals(def.kind) || "structure".equals(def.kind)) {
                        int slot = firstEmpty(p.board);
                        if (slot < 0) continue;
                        act.addProperty("targetSlot", slot);
                    } else {
                        act.addProperty("targetSlot", 0);
                        act.addProperty("targetEnemySlot", 0);
                    }
                    applyActionNoAi(s, actor, act);
                    played = true;
                    break;
                } catch (Throwable ignored) {}
            }
            if (!played) {
                passPriority(s, actor);
            }
        }
    }

    /** Apply without recursive AI (used by AI itself). */
    private static void applyActionNoAi(BattleState s, int actor, JsonObject action) {
        String type = action.get("type")
            .getAsString();
        Random rng = new Random(s.seed + s.turn * 13L + s.log.size());
        if ("confirm_mulligan".equals(type)) confirmMulligan(s, actor, action, rng);
        else if ("play_card".equals(type)) playCard(s, actor, action, rng);
        else if ("pass_priority".equals(type)) passPriority(s, actor);
        else if ("start_attack".equals(type)) startAttack(s, actor);
        else if ("end_main".equals(type)) {
            if (s.attackTokenAvailable && s.attackTokenPlayer == actor) startAttack(s, actor);
            else passPriority(s, actor);
        } else if ("declare_attacks".equals(type)) declareAttacks(s, actor, action);
        else if ("declare_blocks".equals(type)) declareBlocks(s, actor, action);
        else if ("swap_slots".equals(type)) swapSlots(s, actor, action.get("a")
            .getAsInt(), action.get("b")
                .getAsInt());
        else if ("pass_swap".equals(type)) passSwap(s, actor);
        maybeApplyEquip(s);
    }

    public static BattleState publicView(BattleState s) {
        // Hide AI hand/deck contents for client.
        BattleState copy = deepCopy(s);
        PlayerState ai = copy.players[1];
        if (ai != null && ai.isAi) {
            List<String> hiddenHand = new ArrayList<String>();
            for (int i = 0; i < ai.hand.size(); i++) hiddenHand.add("?");
            ai.hand = hiddenHand;
            List<String> hiddenDeck = new ArrayList<String>();
            for (int i = 0; i < ai.deck.size(); i++) hiddenDeck.add("?");
            ai.deck = hiddenDeck;
        }
        return copy;
    }

    private static BattleState deepCopy(BattleState s) {
        return new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(s), BattleState.class);
    }
}
