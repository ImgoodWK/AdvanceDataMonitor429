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
import com.imgood.textech.cardbattle.data.CardCatalog;

/**
 * LoR-style attack/block engine with GTNH theme hooks. Runs off the Forge tick
 * (HTTP worker threads only).
 */
public final class BattleEngine {

    private static final int BOARD = 6;
    private static final int START_NEXUS = 20;
    private static final int MAX_BANK = 6;

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
        s.phase = "main";
        s.activePlayer = 0;
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

    private static boolean hasKw(BoardUnit u, String kw) {
        return u != null && u.keywords.contains(kw);
    }

    private static boolean hasOrdoAer(BoardUnit u) {
        return u != null && u.aspects.contains("ordo") && u.aspects.contains("aer");
    }

    private static void draw(PlayerState p, int n, BattleState s) {
        for (int i = 0; i < n; i++) {
            if (p.deck.isEmpty()) {
                s.log.add(p.name + " deck empty");
                return;
            }
            p.hand.add(p.deck.remove(0));
        }
    }

    private static boolean spendMana(PlayerState p, int cost) {
        if (p.mana + p.bankedMana < cost) return false;
        int need = cost;
        int fromMana = Math.min(p.mana, need);
        p.mana -= fromMana;
        need -= fromMana;
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
        for (int i = 0; i < p.board.length; i++) {
            BoardUnit u = p.board[i];
            if (u != null && u.health <= 0) {
                s.log.add(p.name + " loses " + u.cardId + " at " + i);
                p.discard.add(u.cardId);
                p.board[i] = null;
            }
        }
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
        if ("play_card".equals(type)) {
            playCard(s, actor, action, rng);
        } else if ("end_main".equals(type)) {
            requirePhase(s, "main");
            requireActive(s, actor);
            s.phase = "attack_declare";
            s.attackOrder.clear();
            s.blockPairs.clear();
            s.swapUsedThisCombat = false;
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

    private static void playCard(BattleState s, int actor, JsonObject action, Random rng) {
        requirePhase(s, "main");
        requireActive(s, actor);
        PlayerState me = s.players[actor];
        int handIndex = action.get("handIndex")
            .getAsInt();
        if (handIndex < 0 || handIndex >= me.hand.size()) throw new IllegalArgumentException("hand");
        String cardId = me.hand.get(handIndex);
        CardDef def = CardCatalog.get(cardId);
        if (def == null) throw new IllegalArgumentException("Unknown card");
        if (!spendMana(me, def.cost)) throw new IllegalStateException("Not enough mana");
        me.hand.remove(handIndex);
        me.discard.add(cardId);
        s.log.add(me.name + " plays " + (def.nameZh != null ? def.nameZh : def.id));
        if ("unit".equals(def.kind) || "structure".equals(def.kind)) {
            int slot = action.has("targetSlot") ? action.get("targetSlot")
                .getAsInt() : firstEmpty(me.board);
            if (slot < 0 || slot >= BOARD || me.board[slot] != null) throw new IllegalStateException("No empty slot");
            me.board[slot] = makeUnit(def);
        } else {
            applySpell(s, actor, def, action, rng);
        }
        removeDead(s.players[0], s);
        removeDead(s.players[1], s);
        checkWinner(s);
    }

    private static void applySpell(BattleState s, int actor, CardDef def, JsonObject action, Random rng) {
        PlayerState me = s.players[actor];
        PlayerState you = s.players[opp(actor)];
        int enemySlot = action.has("targetEnemySlot") ? action.get("targetEnemySlot")
            .getAsInt() : 0;
        int mySlot = action.has("targetSlot") ? action.get("targetSlot")
            .getAsInt() : 0;
        String id = def.id;
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
        } else if ("fo_plugin_speed".equals(id) || "ee_watch".equals(id) || "ee_relay".equals(id)) {
            int cut = "ee_watch".equals(id) ? 2 : 1;
            for (BoardUnit u : me.board) {
                if (u != null && u.hiveTurnsLeft != null) {
                    u.hiveTurnsLeft = Math.max(0, u.hiveTurnsLeft.intValue() - cut);
                }
            }
            if ("ee_watch".equals(id) || "ee_relay".equals(id)) {
                BoardUnit u = me.board[mySlot];
                if (u != null && u.hiveTurnsLeft != null) {
                    u.hiveTurnsLeft = Math.max(0, u.hiveTurnsLeft.intValue() - cut);
                }
            }
        } else if ("fo_plugin_strong".equals(id)) {
            me.nextBeeMutate = true;
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
            if (pick != null) me.hand.add(pick);
        } else if ("ae_formation".equals(id)) {
            CardDef ball = CardCatalog.get("ae_matter");
            int empty = firstEmpty(me.board);
            if (empty >= 0 && ball != null) me.board[empty] = makeUnit(ball);
        } else if ("dlb_tantrum".equals(id) || "dlb_scream".equals(id)) {
            s.activePlayer = opp(s.activePlayer);
            s.phase = "main";
            s.log.add("DLB force role swap!");
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

    private static void declareAttacks(BattleState s, int actor, JsonObject action) {
        requirePhase(s, "attack_declare");
        requireActive(s, actor);
        PlayerState me = s.players[actor];
        JsonArray slots = action.getAsJsonArray("slots");
        HashSet<Integer> unique = new HashSet<Integer>();
        s.attackOrder.clear();
        if (slots != null) {
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.get(i)
                    .getAsInt();
                if (!unique.add(slot)) continue;
                BoardUnit u = me.board[slot];
                if (u == null || u.isStructure || u.attack <= 0) throw new IllegalArgumentException("bad attacker");
                s.attackOrder.add(slot);
            }
        }
        s.phase = "block_declare";
        if (s.attackOrder.isEmpty()) {
            s.blockPairs.clear();
            resolveCombat(s);
        }
    }

    private static boolean canBlock(BoardUnit atk, BoardUnit blk) {
        if (blk.isStructure) return false;
        if (hasKw(atk, "stealth") && !hasKw(blk, "stealth")) return false;
        return true;
    }

    private static void declareBlocks(BattleState s, int blocker, JsonObject action) {
        requirePhase(s, "block_declare");
        if (blocker != opp(s.activePlayer)) throw new IllegalStateException("Not defender");
        PlayerState atkP = s.players[s.activePlayer];
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
            resolveCombat(s);
        }
    }

    private static void swapSlots(BattleState s, int actor, int a, int b) {
        requirePhase(s, "swap_extra");
        if (actor != opp(s.activePlayer)) throw new IllegalStateException("Only defender");
        PlayerState p = s.players[actor];
        boolean ok = false;
        for (BoardUnit u : p.board) if (hasOrdoAer(u)) ok = true;
        if (!ok) throw new IllegalStateException("Need Ordo+Aer");
        BoardUnit tmp = p.board[a];
        p.board[a] = p.board[b];
        p.board[b] = tmp;
        s.swapUsedThisCombat = true;
        s.log.add(p.name + " swapped " + a + "<->" + b);
        resolveCombat(s);
    }

    private static void resolveCombat(BattleState s) {
        s.phase = "resolve";
        int atkIdx = s.activePlayer;
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
                int dmg = VoltageRules.applyNexusDamage(attacker.attack, atk.voltage, def.voltage, def.damageReductionPct);
                def.nexusHp -= dmg;
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
        endTurn(s);
    }

    private static void endTurn(BattleState s) {
        s.phase = "turn_end";
        int actor = s.activePlayer;
        PlayerState me = s.players[actor];
        Random rng = new Random(s.seed + s.turn * 17L + actor);
        processStructures(s, me, rng);
        bankMana(me, s);
        if (s.dlbForceEvery > 0 && s.turn - s.lastForcedSwapTurn >= s.dlbForceEvery) {
            s.lastForcedSwapTurn = s.turn;
            s.log.add("DLB schedule: keep initiative");
            s.activePlayer = actor;
            s.turn += 1;
        } else {
            s.activePlayer = opp(actor);
            if (s.activePlayer == 0) s.turn += 1;
        }
        PlayerState next = s.players[s.activePlayer];
        next.maxMana = Math.min(10, next.maxMana + 1);
        next.mana = next.maxMana;
        draw(next, 1, s);
        for (BoardUnit u : next.board) {
            if (u != null && "dlb_chaos".equals(u.cardId) && s.dlbForceEvery > 3) s.dlbForceEvery -= 1;
        }
        s.attackOrder.clear();
        s.blockPairs.clear();
        s.phase = "main";
        checkWinner(s);
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
                if (pick != null && p.hand.size() < 10) p.hand.add(pick);
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

    private static void bankMana(PlayerState p, BattleState s) {
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
                    s.log.add(p.name + " capacitor overload!");
                    break;
                }
            }
        }
    }

    public static void runAi(BattleState s) {
        for (int n = 0; n < 24; n++) {
            if ("game_over".equals(s.phase)) return;
            int actor = s.activePlayer;
            PlayerState p = s.players[actor];
            if ("main".equals(s.phase) && !p.isAi) return;
            if ("attack_declare".equals(s.phase) && !p.isAi) return;
            if ("block_declare".equals(s.phase) && !s.players[opp(actor)].isAi) return;
            if ("swap_extra".equals(s.phase) && !s.players[opp(actor)].isAi) return;

            if ("main".equals(s.phase) && p.isAi) {
                boolean played = false;
                for (int i = 0; i < p.hand.size(); i++) {
                    CardDef def = CardCatalog.get(p.hand.get(i));
                    if (def == null || p.mana + p.bankedMana < def.cost) continue;
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
                    JsonObject end = new JsonObject();
                    end.addProperty("type", "end_main");
                    applyActionNoAi(s, actor, end);
                }
                continue;
            }
            if ("attack_declare".equals(s.phase) && p.isAi) {
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
                int defender = opp(s.activePlayer);
                if (!s.players[defender].isAi) return;
                PlayerState defP = s.players[defender];
                JsonArray pairs = new JsonArray();
                HashSet<Integer> used = new HashSet<Integer>();
                for (Integer aSlot : s.attackOrder) {
                    BoardUnit attacker = s.players[s.activePlayer].board[aSlot.intValue()];
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
                int defender = opp(s.activePlayer);
                if (s.players[defender].isAi) resolveCombat(s);
                else return;
            }
        }
    }

    /** Apply without recursive AI (used by AI itself). */
    private static void applyActionNoAi(BattleState s, int actor, JsonObject action) {
        String type = action.get("type")
            .getAsString();
        Random rng = new Random(s.seed + s.turn * 13L + s.log.size());
        if ("play_card".equals(type)) playCard(s, actor, action, rng);
        else if ("end_main".equals(type)) {
            requirePhase(s, "main");
            requireActive(s, actor);
            s.phase = "attack_declare";
            s.attackOrder.clear();
            s.blockPairs.clear();
            s.swapUsedThisCombat = false;
        } else if ("declare_attacks".equals(type)) declareAttacks(s, actor, action);
        else if ("declare_blocks".equals(type)) declareBlocks(s, actor, action);
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
