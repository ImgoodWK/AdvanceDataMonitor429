package com.imgood.textech.utils;

import appeng.api.config.Upgrades;
import appeng.parts.automation.UpgradeInventory;

/**
 * Buffer grid size from AE capacity cards (0 → 1 slot, 1 → 3×3, 2 → 9×9).
 */
public final class MatterBallDecompressorCapacityUtil {

    public static final int MAX_BUFFER_SIDE = 9;
    public static final int MAX_BUFFER_SLOTS = MAX_BUFFER_SIDE * MAX_BUFFER_SIDE;

    private MatterBallDecompressorCapacityUtil() {}

    public static int getBufferSide(UpgradeInventory upgrades) {
        int capacityCards = upgrades == null ? 0 : upgrades.getInstalledUpgrades(Upgrades.CAPACITY);
        if (capacityCards >= 2) {
            return 9;
        }
        if (capacityCards >= 1) {
            return 3;
        }
        return 1;
    }

    public static int getActiveBufferSlots(UpgradeInventory upgrades) {
        int side = getBufferSide(upgrades);
        return side * side;
    }
}
