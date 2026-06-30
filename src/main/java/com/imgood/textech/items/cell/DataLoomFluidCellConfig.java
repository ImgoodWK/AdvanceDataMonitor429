package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

import com.glodblock.github.inventory.FluidCellConfig;

/** Fluid-channel Cell Workbench partition inventory with tooltip cache refresh on marker changes. */
public class DataLoomFluidCellConfig extends FluidCellConfig {

    private final ItemStack cellStack;

    public DataLoomFluidCellConfig(ItemStack cellStack) {
        super(cellStack);
        this.cellStack = cellStack;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        DataLoomCellTooltipCache.refresh(this.cellStack);
    }
}
