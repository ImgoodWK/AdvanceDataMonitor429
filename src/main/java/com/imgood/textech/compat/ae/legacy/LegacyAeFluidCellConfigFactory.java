package com.imgood.textech.compat.ae.legacy;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.imgood.textech.compat.ae.AeFluidCellConfigFactory;
import com.imgood.textech.items.cell.DataLoomFluidCellConfig;

public final class LegacyAeFluidCellConfigFactory implements AeFluidCellConfigFactory {

    public static final LegacyAeFluidCellConfigFactory INSTANCE = new LegacyAeFluidCellConfigFactory();

    private LegacyAeFluidCellConfigFactory() {}

    @Override
    public IInventory createConfigInventory(ItemStack cellStack) {
        return new DataLoomFluidCellConfig(cellStack);
    }
}
