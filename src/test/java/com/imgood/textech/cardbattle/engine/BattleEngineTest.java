package com.imgood.textech.cardbattle.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.imgood.textech.cardbattle.CardBattleTypes.BattleState;
import com.imgood.textech.cardbattle.CardBattleTypes.BoardUnit;
import com.imgood.textech.cardbattle.CardBattleTypes.PlayerState;

public class BattleEngineTest {

    @Test
    public void illegalSpellTargetDoesNotMutateBattleState() {
        BattleState state = createMatch();
        confirmMulligan(state);
        PlayerState player = state.players[0];
        state.players[1].isAi = false;
        player.hand.clear();
        player.hand.add("van_smite");
        player.mana = 2;
        player.spellMana = 1;
        player.bankedMana = 3;

        int mana = player.mana;
        int spellMana = player.spellMana;
        int bankedMana = player.bankedMana;
        int logSize = state.log.size();
        int activePlayer = state.activePlayer;

        JsonObject action = new JsonObject();
        action.addProperty("type", "play_card");
        action.addProperty("handIndex", 0);
        action.addProperty("targetEnemySlot", 0);
        try {
            BattleEngine.applyAction(state, 0, action);
            fail("Expected an illegal target error");
        } catch (IllegalStateException expected) {
            assertEquals("Missing enemy target", expected.getMessage());
        }

        assertEquals(mana, player.mana);
        assertEquals(spellMana, player.spellMana);
        assertEquals(bankedMana, player.bankedMana);
        assertEquals(1, player.hand.size());
        assertEquals("van_smite", player.hand.get(0));
        assertEquals(0, player.discard.size());
        assertEquals(logSize, state.log.size());
        assertEquals(activePlayer, state.activePlayer);
    }

    @Test
    public void mulliganReplacesBeforeShufflingReturnedCards() {
        BattleState state = createMatch();
        PlayerState player = state.players[0];
        String returned = player.hand.get(0);
        String replacement = player.deck.get(0);

        JsonObject action = new JsonObject();
        action.addProperty("type", "confirm_mulligan");
        JsonArray indices = new JsonArray();
        indices.add(new JsonPrimitive(0));
        action.add("replaceIndices", indices);
        BattleEngine.applyAction(state, 0, action);

        assertEquals("main", state.phase);
        assertEquals(true, state.mulliganDone[0]);
        assertEquals(true, state.mulliganDone[1]);
        assertEquals(replacement, player.hand.get(0));
        assertEquals(5, player.hand.size());
        assertEquals(true, player.deck.contains(returned) || player.hand.subList(4, player.hand.size()).contains(returned));
    }

    @Test
    public void invalidMulliganDoesNotMutateState() {
        BattleState state = createMatch();
        PlayerState player = state.players[0];
        String first = player.hand.get(0);
        int deckSize = player.deck.size();
        int logSize = state.log.size();

        JsonObject action = new JsonObject();
        action.addProperty("type", "confirm_mulligan");
        JsonArray indices = new JsonArray();
        indices.add(new JsonPrimitive(0));
        indices.add(new JsonPrimitive(0));
        action.add("replaceIndices", indices);
        try {
            BattleEngine.applyAction(state, 0, action);
            fail("Expected an invalid mulligan index error");
        } catch (IllegalStateException expected) {
            assertEquals("Invalid mulligan index", expected.getMessage());
        }

        assertEquals("mulligan", state.phase);
        assertEquals(false, state.mulliganDone[0]);
        assertEquals(first, player.hand.get(0));
        assertEquals(deckSize, player.deck.size());
        assertEquals(logSize, state.log.size());
    }

    @Test
    public void handLimitBurnsAndEmptyDeckDrawLoses() {
        BattleState state = createMatch();
        confirmMulligan(state);
        state.players[1].isAi = false;
        PlayerState player = state.players[0];
        player.hand.clear();
        player.hand.add("ge_split");
        for (int i = 0; i < 9; i++) player.hand.add("van_grunt");
        player.deck.clear();
        player.deck.add("van_scout");
        player.deck.add("van_wolf");
        player.discard.clear();
        player.mana = 10;

        JsonObject drawTwo = new JsonObject();
        drawTwo.addProperty("type", "play_card");
        drawTwo.addProperty("handIndex", 0);
        BattleEngine.applyAction(state, 0, drawTwo);
        assertEquals(10, player.hand.size());
        assertEquals(2, player.discard.size());
        assertEquals("ge_split", player.discard.get(0));
        assertEquals("van_wolf", player.discard.get(1));

        state.activePlayer = 0;
        player.hand.clear();
        player.hand.add("as_attune");
        player.deck.clear();
        player.discard.clear();
        player.nexusHp = 20;
        player.mana = 10;
        JsonObject emptyDraw = new JsonObject();
        emptyDraw.addProperty("type", "play_card");
        emptyDraw.addProperty("handIndex", 0);
        BattleEngine.applyAction(state, 0, emptyDraw);
        assertEquals("game_over", state.phase);
        assertEquals(Integer.valueOf(1), state.winner);
    }

    @Test
    public void fastResponsesResolveLastInFirstOut() {
        BattleState state = createMatch();
        confirmMulligan(state);
        state.players[1].isAi = false;
        state.players[0].hand.clear();
        state.players[0].hand.add("van_smite");
        state.players[1].hand.clear();
        state.players[1].hand.add("th_ward");
        state.players[0].mana = 10;
        state.players[1].mana = 10;
        state.players[1].board[0] = unit("th_zombie", 2, 2, 4);

        BattleEngine.applyAction(state, 0, play(0, null, Integer.valueOf(0)));
        assertEquals("spell_response", state.phase);
        assertEquals(1, state.spellStack.size());
        assertEquals(0, state.players[0].discard.size());

        BattleEngine.applyAction(state, 1, play(0, Integer.valueOf(0), null));
        assertEquals(2, state.spellStack.size());
        BattleEngine.applyAction(state, 0, action("pass_priority"));
        BattleEngine.applyAction(state, 1, action("pass_priority"));

        assertEquals("main", state.phase);
        assertEquals(1, state.activePlayer);
        assertEquals(0, state.consecutivePasses);
        assertEquals(0, state.responsePasses);
        assertEquals(0, state.spellStack.size());
        assertEquals(1, state.players[1].board[0].health);
        assertEquals(0, state.players[1].board[0].armor);
        assertEquals(true, state.players[0].discard.contains("van_smite"));
        assertEquals(true, state.players[1].discard.contains("th_ward"));
    }

    @Test
    public void slowResponseIsTransactionalAndBurstKeepsPriority() {
        BattleState state = createMatch();
        confirmMulligan(state);
        state.players[1].isAi = false;
        state.players[1].board[0] = unit("van_grunt", 2, 2, 2);
        state.players[0].hand.clear();
        state.players[0].hand.add("van_smite");
        state.players[1].hand.clear();
        state.players[1].hand.add("van_rally");
        state.players[0].mana = 10;
        state.players[1].mana = 10;
        BattleEngine.applyAction(state, 0, play(0, null, Integer.valueOf(0)));

        int mana = state.players[1].mana;
        int stackSize = state.spellStack.size();
        int logSize = state.log.size();
        try {
            BattleEngine.applyAction(state, 1, play(0, null, null));
            fail("Expected slow response rejection");
        } catch (IllegalStateException expected) {
            assertEquals("Slow spells cannot be played in a response window", expected.getMessage());
        }
        assertEquals(mana, state.players[1].mana);
        assertEquals(1, state.players[1].hand.size());
        assertEquals(stackSize, state.spellStack.size());
        assertEquals(logSize, state.log.size());

        BattleEngine.applyAction(state, 1, action("pass_priority"));
        BattleEngine.applyAction(state, 0, action("pass_priority"));
        state.activePlayer = 0;
        state.players[0].hand.clear();
        state.players[0].hand.add("gt_overclock");
        state.players[0].mana = 1;
        BattleEngine.applyAction(state, 0, play(0, null, null));
        assertEquals("main", state.phase);
        assertEquals(0, state.activePlayer);
        assertEquals(2, state.players[0].mana);
        assertEquals(0, state.spellStack.size());
    }

    @Test
    public void combatResponseResolvesBeforeCombatDamage() {
        BattleState state = createMatch();
        confirmMulligan(state);
        state.players[1].isAi = false;
        state.players[0].board[0] = unit("th_zombie", 3, 3, 3);
        state.players[1].board[0] = unit("van_grunt", 1, 3, 3);
        state.players[0].hand.clear();
        state.players[0].hand.add("th_ignis");
        state.players[0].mana = 10;

        BattleEngine.applyAction(state, 0, action("start_attack"));
        JsonObject attacks = action("declare_attacks");
        JsonArray slots = new JsonArray();
        slots.add(new JsonPrimitive(0));
        attacks.add("slots", slots);
        BattleEngine.applyAction(state, 0, attacks);
        JsonObject blocks = action("declare_blocks");
        JsonArray pairs = new JsonArray();
        JsonObject pair = new JsonObject();
        pair.addProperty("attackerSlot", 0);
        pair.addProperty("blockerSlot", 0);
        pairs.add(pair);
        blocks.add("pairs", pairs);
        BattleEngine.applyAction(state, 1, blocks);

        assertEquals("combat_response", state.phase);
        assertEquals(0, state.combatAttacker);
        assertEquals(true, state.attackTokenAvailable);
        BattleEngine.applyAction(state, 0, play(0, null, Integer.valueOf(0)));
        BattleEngine.applyAction(state, 1, action("pass_priority"));
        BattleEngine.applyAction(state, 0, action("pass_priority"));

        assertEquals("main", state.phase);
        assertEquals(-1, state.combatAttacker);
        assertEquals(false, state.attackTokenAvailable);
        assertEquals(null, state.players[1].board[0]);
        assertEquals(2, state.players[0].board[0].health);
    }

    private static BattleState createMatch() {
        JsonObject opts = new JsonObject();
        opts.addProperty("seed", 123);
        opts.addProperty("playerId", "player");
        opts.addProperty("playerName", "Player");
        opts.add("playerDeck", deck());
        opts.add("playerThemes", strings("vanilla"));
        opts.addProperty("playerVoltage", "LV");
        opts.add("aiDeck", deck());
        opts.add("aiThemes", strings("vanilla"));
        opts.addProperty("aiVoltage", "LV");
        return BattleEngine.createMatch(opts);
    }

    private static BoardUnit unit(String cardId, int attack, int health, int maxHealth) {
        BoardUnit unit = new BoardUnit();
        unit.instanceId = cardId + "-instance";
        unit.cardId = cardId;
        unit.attack = attack;
        unit.health = health;
        unit.maxHealth = maxHealth;
        return unit;
    }

    private static JsonObject action(String type) {
        JsonObject action = new JsonObject();
        action.addProperty("type", type);
        return action;
    }

    private static JsonObject play(int handIndex, Integer targetSlot, Integer targetEnemySlot) {
        JsonObject action = action("play_card");
        action.addProperty("handIndex", handIndex);
        if (targetSlot != null) action.addProperty("targetSlot", targetSlot.intValue());
        if (targetEnemySlot != null) action.addProperty("targetEnemySlot", targetEnemySlot.intValue());
        return action;
    }

    private static JsonArray deck() {
        return strings(
            "van_grunt",
            "van_knight",
            "van_golem",
            "van_archer",
            "van_smite",
            "van_heal",
            "van_rally",
            "van_titan",
            "van_scout",
            "van_wolf");
    }

    private static void confirmMulligan(BattleState state) {
        JsonObject action = new JsonObject();
        action.addProperty("type", "confirm_mulligan");
        action.add("replaceIndices", new JsonArray());
        BattleEngine.applyAction(state, 0, action);
    }

    private static JsonArray strings(String... values) {
        JsonArray out = new JsonArray();
        for (String value : values) out.add(new JsonPrimitive(value));
        return out;
    }
}
