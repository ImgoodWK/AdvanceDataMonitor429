package com.imgood.textech.utils;

import appeng.api.config.Upgrades;
import appeng.parts.automation.UpgradeInventory;

/**
 * Speed multiplier from GTNH AE2 acceleration cards ({@link Upgrades#SPEED} / {@link Upgrades#SUPERSPEED}).
 * Mirrors molecular-assembler speed tiers for normal cards; each hyper card multiplies by 8.
 */
public final class AeUpgradeSpeedUtil {

    private AeUpgradeSpeedUtil() {}

    public static double getSpeedMultiplier(UpgradeInventory upgrades) {
        if (upgrades == null) {
            return 1.0D;
        }
        int speed = upgrades.getInstalledUpgrades(Upgrades.SPEED);
        int superSpeed = upgrades.getInstalledUpgrades(Upgrades.SUPERSPEED);
        double mult = speedTierMultiplier(speed);
        for (int i = 0; i < superSpeed; i++) {
            mult *= 8.0D;
        }
        return mult;
    }

    private static double speedTierMultiplier(int speedCards) {
        switch (speedCards) {
            case 1:
                return 1.3D;
            case 2:
                return 1.7D;
            case 3:
                return 2.0D;
            case 4:
                return 2.5D;
            default:
                return 1.0D;
        }
    }
}
