package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

/**
 * Fluid Cell Workbench partition for AE2 native fluid stack type (GTNH 2.9+).
 * Same backing as {@link DataLoomFluidCellConfig}; retained as a named type for
 * {@link com.imgood.textech.compat.ae.native_.NativeAeFluidCellConfigFactory}.
 */
public class NativeDataLoomFluidCellConfig extends DataLoomFluidCellConfig {

    public NativeDataLoomFluidCellConfig(ItemStack cellStack) {
        super(cellStack);
    }

    /** Always true on GTNH 2.9.0-beta-2+ (native fluid cells / {@code FLUID_STACK_TYPE}). */
    public static boolean isSupported() {
        return true;
    }
}
