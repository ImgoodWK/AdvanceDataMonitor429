package com.imgood.textech.tileentity;

import appeng.api.config.Upgrades;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.IAEAppEngInventory;

/**
 * AE upgrade inventory for the matter ball decompressor.
 * Slots 0–3: speed / hyper / superluminal cards; slots 4–5: capacity cards only.
 */
public final class MatterBallDecompressorUpgrades extends UpgradeInventory {

    public static final int SPEED_UPGRADE_SLOTS = 4;
    public static final int CAPACITY_UPGRADE_SLOTS = 2;
    public static final int TOTAL_UPGRADE_SLOTS = SPEED_UPGRADE_SLOTS + CAPACITY_UPGRADE_SLOTS;

    public MatterBallDecompressorUpgrades(IAEAppEngInventory parent, int slots) {
        super(parent, slots);
    }

    @Override
    public int getMaxInstalled(Upgrades upgrades) {
        if (upgrades == Upgrades.SPEED
            || upgrades == Upgrades.SUPERSPEED
            || upgrades == Upgrades.SUPERLUMINALSPEED) {
            return SPEED_UPGRADE_SLOTS;
        }
        if (upgrades == Upgrades.CAPACITY) {
            return CAPACITY_UPGRADE_SLOTS;
        }
        return 0;
    }

    public static boolean isSpeedUpgradeSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < SPEED_UPGRADE_SLOTS;
    }

    public static boolean isCapacityUpgradeSlot(int slotIndex) {
        return slotIndex >= SPEED_UPGRADE_SLOTS && slotIndex < TOTAL_UPGRADE_SLOTS;
    }

    public static boolean acceptsUpgradeInSlot(int slotIndex, Upgrades upgrade) {
        if (upgrade == null) {
            return false;
        }
        if (isSpeedUpgradeSlot(slotIndex)) {
            return upgrade == Upgrades.SPEED
                || upgrade == Upgrades.SUPERSPEED
                || upgrade == Upgrades.SUPERLUMINALSPEED;
        }
        if (isCapacityUpgradeSlot(slotIndex)) {
            return upgrade == Upgrades.CAPACITY;
        }
        return false;
    }
}
