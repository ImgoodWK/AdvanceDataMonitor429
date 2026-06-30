package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

/** Cell workbench partition inventory that only accepts dust items (not this mod's items). */
public class DustLoomCellConfig extends DataLoomCellConfig {

    public DustLoomCellConfig(ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    protected boolean isMarkerItemAllowed(ItemStack stack) {
        return DataLoomCellUtil.isDustItem(stack);
    }
}
