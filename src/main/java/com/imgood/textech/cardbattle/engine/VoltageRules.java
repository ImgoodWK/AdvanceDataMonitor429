package com.imgood.textech.cardbattle.engine;

import com.imgood.textech.cardbattle.CardBattleTypes;

public final class VoltageRules {

    private VoltageRules() {}

    public static double nexusDamageMultiplier(String defenderVoltage) {
        int tiers = CardBattleTypes.voltageIndex(defenderVoltage) + 1;
        double m = 1.0 - tiers * 0.05;
        return m < 0.25 ? 0.25 : m;
    }

    public static double offenseMultiplier(String attacker, String defender) {
        int gap = CardBattleTypes.voltageIndex(defender) - CardBattleTypes.voltageIndex(attacker);
        if (gap <= 0) return 1.0;
        double m = 1.0 - gap * 0.35;
        return m < 0.15 ? 0.15 : m;
    }

    public static int applyNexusDamage(int raw, String atkV, String defV, int defReductionPct) {
        double dmg = raw * offenseMultiplier(atkV, defV) * nexusDamageMultiplier(defV);
        dmg *= Math.max(0.0, 1.0 - defReductionPct / 100.0);
        int out = (int) Math.floor(dmg);
        return out < 1 ? 1 : out;
    }
}
