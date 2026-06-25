package com.imgood.advancedatamonitor.items.cell;

import net.minecraft.item.ItemStack;

import com.glodblock.github.inventory.FluidCellConfig;

/**
 * Fluid Cell Workbench partition for native AE2 fluid profile (GTNH 2.9.0 beta-1+).
 * Extends AE2FC {@link FluidCellConfig} until AE2 partition API is fully decoupled from ae2fc.
 */
public class NativeDataLoomFluidCellConfig extends FluidCellConfig {

    private static final String NATIVE_FLUID_CELL_CLASS = "appeng.items.storage.ItemBasicFluidStorageCell";

    private final ItemStack cellStack;

    public NativeDataLoomFluidCellConfig(ItemStack cellStack) {
        super(cellStack);
        this.cellStack = cellStack;
    }

    /** {@code true} when AE2 native fluid storage cells are present on the classpath. */
    public static boolean isSupported() {
        try {
            Class.forName(NATIVE_FLUID_CELL_CLASS, false, NativeDataLoomFluidCellConfig.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        DataLoomCellTooltipCache.refresh(this.cellStack);
    }
}
