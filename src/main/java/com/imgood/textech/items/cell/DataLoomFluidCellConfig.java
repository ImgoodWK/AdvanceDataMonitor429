package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;
import appeng.items.contents.CellConfig;

/**
 * Fluid-channel Cell Workbench partition inventory (AE2 2.9 {@link CellConfig} / {@code IAEStackInventory}).
 * Refreshes {@link DataLoomCellTooltipCache} when markers change.
 */
public class DataLoomFluidCellConfig extends CellConfig {

    private final ItemStack cellStack;

    public DataLoomFluidCellConfig(ItemStack cellStack) {
        super(cellStack);
        this.cellStack = cellStack;
    }

    @Override
    public void putAEStackInSlot(final int n, final IAEStack aes) {
        if (aes != null && DataLoomCellUtil.isForbiddenFluidPartitionStack(aes)) {
            return;
        }
        super.putAEStackInSlot(n, aes);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        DataLoomCellTooltipCache.refresh(this.cellStack);
    }
}
