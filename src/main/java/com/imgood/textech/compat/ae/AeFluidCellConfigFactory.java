package com.imgood.textech.compat.ae;

import net.minecraft.item.ItemStack;

import appeng.tile.inventory.IAEStackInventory;

/** Fluid-channel Cell Workbench partition inventory factory (AE2 2.9 {@link IAEStackInventory}). */
public interface AeFluidCellConfigFactory {

    IAEStackInventory createConfigInventory(ItemStack cellStack);
}
