package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;

/** Cell workbench partition inventory for flow/tide fluid loom cells. */
public class FlowLoomCellConfig extends DataLoomFluidCellConfig {

    public FlowLoomCellConfig(ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    public void putAEStackInSlot(final int n, final IAEStack aes) {
        if (aes != null && DataLoomCellUtil.isForbiddenFluidPartitionStack(aes)) {
            return;
        }
        super.putAEStackInSlot(n, aes);
    }
}
