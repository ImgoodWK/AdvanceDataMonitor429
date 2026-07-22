package com.imgood.textech.cardbattle;

import java.util.ArrayList;
import java.util.List;

/** Shared card-battle domain types (Java 7-friendly). */
public final class CardBattleTypes {

    private CardBattleTypes() {}

    public static final String[] VOLTAGE_ORDER = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV" };

    public static int themeSlots(String voltage) {
        int i = voltageIndex(voltage);
        if (i <= 1) return 1;
        if (i <= 3) return 2;
        if (i <= 5) return 3;
        if (i <= 7) return 4;
        return 5;
    }

    public static int voltageIndex(String v) {
        for (int i = 0; i < VOLTAGE_ORDER.length; i++) {
            if (VOLTAGE_ORDER[i].equals(v)) return i;
        }
        return 0;
    }

    public static class CardDef {
        public String id;
        public String name;
        public String nameZh;
        public String theme;
        public String kind;
        public int cost;
        public Integer attack;
        public Integer health;
        public Integer armor;
        public List<String> keywords;
        public List<String> aspects;
        public Integer manaPerTurn;
        public Integer hiveCooldown;
        public String textZh;
        public String art;
    }

    public static class BoardUnit {
        public String instanceId;
        public String cardId;
        public int attack;
        public int health;
        public int maxHealth;
        public int armor;
        public List<String> keywords = new ArrayList<String>();
        public List<String> aspects = new ArrayList<String>();
        public boolean isStructure;
        public boolean untargetable;
        public Integer hiveTurnsLeft;
        public List<String> equipment = new ArrayList<String>();
    }

    public static class PlayerState {
        public String id;
        public String name;
        public boolean isAi;
        public int nexusHp;
        public int maxNexusHp;
        public int mana;
        public int maxMana;
        public int bankedMana;
        public String voltage;
        public List<String> hand = new ArrayList<String>();
        public List<String> deck = new ArrayList<String>();
        public List<String> discard = new ArrayList<String>();
        public BoardUnit[] board = new BoardUnit[6];
        public List<String> themes = new ArrayList<String>();
        public int damageReductionPct;
        public boolean reflectToNexus;
        public int singularitiesPlayed;
        public boolean eternalReady;
        public boolean eternalActive;
        public boolean nextBeeMutate;
    }

    public static class AttackPair {
        public int attackerSlot;
        public int blockerSlot;

        public AttackPair() {}

        public AttackPair(int a, int b) {
            attackerSlot = a;
            blockerSlot = b;
        }
    }

    public static class BattleState {
        public String matchId;
        public int seed;
        public int turn;
        public String phase;
        public int activePlayer;
        public PlayerState[] players = new PlayerState[2];
        public List<Integer> attackOrder = new ArrayList<Integer>();
        public List<AttackPair> blockPairs = new ArrayList<AttackPair>();
        public boolean swapUsedThisCombat;
        public int dlbForceEvery;
        public int lastForcedSwapTurn;
        public Integer winner;
        public List<String> log = new ArrayList<String>();
        public List<String> aePool = new ArrayList<String>();
        public boolean runSettled;
        public int pendingEquipAtk;
        public int pendingEquipHp;
        public int pendingEquipArmor;
        public boolean equipApplied;
    }
}
