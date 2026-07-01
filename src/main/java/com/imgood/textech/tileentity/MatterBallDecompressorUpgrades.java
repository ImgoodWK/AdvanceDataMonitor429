package com.imgood.textech.tileentity;

import appeng.api.config.Upgrades;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.IAEAppEngInventory;

/**
 * AE upgrade inventory for the matter ball decompressor (GTNH acceleration / hyper-acceleration cards).
 */
public final class MatterBallDecompressorUpgrades extends UpgradeInventory {

    public MatterBallDecompressorUpgrades(IAEAppEngInventory parent, int slots) {
        super(parent, slots);
    }

    @Override
    public int getMaxInstalled(Upgrades upgrades) {
        if (upgrades == Upgrades.SPEED || upgrades == Upgrades.SUPERSPEED) {
            return TileEntityMatterBallDecompressor.UPGRADE_SLOTS;
        }
        return 0;
    }
}
