package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

/** Cell workbench partition inventory for the Data Form Loom Cell (any item except this mod). */
public class FormLoomCellConfig extends DataLoomCellConfig {

    public FormLoomCellConfig(ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    protected boolean isMarkerItemAllowed(ItemStack stack) {
        return true;
    }
}
