package com.imgood.textech.items.cell;

import net.minecraft.item.ItemStack;

import appeng.api.config.Upgrades;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.util.Platform;

/**
 * Upgrade inventory for Data Loom cells. Only {@link IWeaveAmplifierCard} items
 * may be inserted; AE2 acceleration and other upgrade cards are rejected.
 */
public class DataLoomCellUpgrades extends StackUpgradeInventory {

    private final ItemStack cellStack;

    public DataLoomCellUpgrades(ItemStack cellStack, int maxSlots) {
        super(cellStack, null, maxSlots);
        this.cellStack = cellStack;
        this.readFromNBT(Platform.openNbtData(cellStack), "upgrades");
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (!DataLoomCellUtil.isWeaveAmplifier(stack.getItem())) return false;

        ItemStack existing = this.getStackInSlot(slot);
        if (existing != null && DataLoomCellUtil.isWeaveAmplifier(existing.getItem())) {
            return true;
        }
        return countInstalledAmplifiers() < DataLoomCellUtil.MAX_WEAVE_AMPLIFIERS;
    }

    @Override
    public int getMaxInstalled(Upgrades upgrades) {
        if (upgrades == Upgrades.SPEED || upgrades == Upgrades.SUPERSPEED) {
            return DataLoomCellUtil.MAX_WEAVE_AMPLIFIERS;
        }
        return 0;
    }

    @Override
    public void markDirty() {
        this.writeToNBT(Platform.openNbtData(this.cellStack), "upgrades");
        DataLoomCellTooltipCache.refresh(this.cellStack);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        super.setInventorySlotContents(slot, stack);
        DataLoomCellTooltipCache.refresh(this.cellStack);
    }

    private int countInstalledAmplifiers() {
        int count = 0;
        for (int i = 0; i < this.getSizeInventory(); i++) {
            ItemStack stack = this.getStackInSlot(i);
            if (stack != null && DataLoomCellUtil.isWeaveAmplifier(stack.getItem())) {
                count++;
            }
        }
        return count;
    }
}
