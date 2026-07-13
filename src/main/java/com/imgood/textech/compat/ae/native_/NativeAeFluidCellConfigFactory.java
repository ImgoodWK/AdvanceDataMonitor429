package com.imgood.textech.compat.ae.native_;

import net.minecraft.item.ItemStack;

import com.imgood.textech.compat.ae.AeFluidCellConfigFactory;
import com.imgood.textech.items.cell.NativeDataLoomFluidCellConfig;

import appeng.tile.inventory.IAEStackInventory;

/**
 * Native AE2 fluid Cell Workbench config (GTNH 2.9+).
 */
public final class NativeAeFluidCellConfigFactory implements AeFluidCellConfigFactory {

    public static final NativeAeFluidCellConfigFactory INSTANCE = new NativeAeFluidCellConfigFactory();

    private NativeAeFluidCellConfigFactory() {}

    @Override
    public IAEStackInventory createConfigInventory(ItemStack cellStack) {
        return new NativeDataLoomFluidCellConfig(cellStack);
    }
}
