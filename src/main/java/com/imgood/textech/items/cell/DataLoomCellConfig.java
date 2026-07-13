package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.items.contents.CellConfig;

/**
 * Cell Workbench partition inventory shared by item-channel loom cells (AE2 2.9 {@link CellConfig}).
 * Refreshes {@link DataLoomCellTooltipCache} when markers change.
 */
public abstract class DataLoomCellConfig extends CellConfig {

    private final ItemStack cellStack;

    protected DataLoomCellConfig(ItemStack cellStack) {
        super(cellStack);
        this.cellStack = cellStack;
    }

    @Override
    public void putAEStackInSlot(final int n, final IAEStack aes) {
        if (aes != null) {
            if (!(aes instanceof IAEItemStack)) {
                return;
            }
            ItemStack stack = ((IAEItemStack) aes).getItemStack();
            if (stack == null || stack.getItem() == null) {
                return;
            }
            if (DataLoomCellUtil.isModOwnItem(stack) || !isMarkerItemAllowed(stack)) {
                return;
            }
        }
        super.putAEStackInSlot(n, aes);
    }

    protected abstract boolean isMarkerItemAllowed(ItemStack stack);

    @Override
    public void markDirty() {
        super.markDirty();
        DataLoomCellTooltipCache.refresh(this.cellStack);
    }
}
