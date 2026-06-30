package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

import appeng.items.contents.CellConfig;

/**
 * Cell Workbench partition inventory shared by item-channel loom cells.
 * Refreshes {@link DataLoomCellTooltipCache} when markers change.
 */
public abstract class DataLoomCellConfig extends CellConfig {

    private final ItemStack cellStack;

    protected DataLoomCellConfig(ItemStack cellStack) {
        super(cellStack);
        this.cellStack = cellStack;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        if (DataLoomCellUtil.isModOwnItem(stack)) {
            return false;
        }
        return isMarkerItemAllowed(stack);
    }

    protected abstract boolean isMarkerItemAllowed(ItemStack stack);

    @Override
    public void markDirty() {
        super.markDirty();
        DataLoomCellTooltipCache.refresh(this.cellStack);
    }
}
