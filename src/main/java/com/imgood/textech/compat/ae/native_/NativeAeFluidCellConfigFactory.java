package com.imgood.textech.compat.ae.native_;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.imgood.textech.compat.ae.AeFluidCellConfigFactory;
import com.imgood.textech.compat.ae.legacy.LegacyAeFluidCellConfigFactory;
import com.imgood.textech.items.cell.NativeDataLoomFluidCellConfig;

/**
 * Native AE2 fluid Cell Workbench config. Falls back to AE2FC {@link LegacyAeFluidCellConfigFactory} when
 * {@link NativeDataLoomFluidCellConfig} cannot be used.
 */
public final class NativeAeFluidCellConfigFactory implements AeFluidCellConfigFactory {

    public static final NativeAeFluidCellConfigFactory INSTANCE = new NativeAeFluidCellConfigFactory();

    private NativeAeFluidCellConfigFactory() {}

    @Override
    public IInventory createConfigInventory(ItemStack cellStack) {
        if (NativeDataLoomFluidCellConfig.isSupported()) {
            return new NativeDataLoomFluidCellConfig(cellStack);
        }
        return LegacyAeFluidCellConfigFactory.INSTANCE.createConfigInventory(cellStack);
    }
}
