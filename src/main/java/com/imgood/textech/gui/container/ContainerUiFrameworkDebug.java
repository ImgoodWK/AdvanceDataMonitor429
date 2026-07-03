package com.imgood.textech.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * Empty container for the UI framework debug showcase (no item slots).
 */
public class ContainerUiFrameworkDebug extends Container {

    public ContainerUiFrameworkDebug(EntityPlayer player) {
        // showcase only — no slots
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
