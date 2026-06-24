package com.imgood.advancedatamonitor.items.cell;

import com.imgood.advancedatamonitor.Config;

/**
 * Weave amplifier multipliers loaded from config once at startup (see {@link Config#load}).
 */
public final class DataLoomAmplifierRates {

    public static double NORMAL_MULTIPLIER = 4.0D;
    public static double SUPER_MULTIPLIER = 16.0D;

    private DataLoomAmplifierRates() {}

    public static void reloadFromConfig() {
        NORMAL_MULTIPLIER = Config.weaveAmplifierRateMultiplier;
        SUPER_MULTIPLIER = Config.superWeaveAmplifierRateMultiplier;
        if (NORMAL_MULTIPLIER < 1.0D) {
            NORMAL_MULTIPLIER = 1.0D;
        }
        if (SUPER_MULTIPLIER < 1.0D) {
            SUPER_MULTIPLIER = 1.0D;
        }
    }
}
