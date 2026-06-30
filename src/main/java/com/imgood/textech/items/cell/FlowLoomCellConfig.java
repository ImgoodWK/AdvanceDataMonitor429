package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

/** Cell workbench partition inventory for the Data Flow Cell —rejects this mod's items as markers. */
public class FlowLoomCellConfig extends DataLoomFluidCellConfig {

    public FlowLoomCellConfig(ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        return !DataLoomCellUtil.isModOwnItem(stack);
    }
}
