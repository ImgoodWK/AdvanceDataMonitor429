package com.imgood.textech.compat.ae;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Fluid-channel Cell Workbench partition inventory factory. */
public interface AeFluidCellConfigFactory {

    IInventory createConfigInventory(ItemStack cellStack);
}
