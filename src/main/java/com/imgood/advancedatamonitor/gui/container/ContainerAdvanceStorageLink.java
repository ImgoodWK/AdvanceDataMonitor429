package com.imgood.advancedatamonitor.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;

public class ContainerAdvanceStorageLink extends Container {

    private static final int TILE_SLOTS_COUNT = 36;
    private final TileEntityAdvanceStorageLink tileEntity;

    public ContainerAdvanceStorageLink(InventoryPlayer playerInventory, TileEntityAdvanceStorageLink tileEntity) {
        this.tileEntity = tileEntity;

        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(tileEntity, col + row * 9, 8 + col * 18, 18 + row * 18) {

                    @Override
                    public boolean isItemValid(ItemStack stack) {
                        return tileEntity.isItemValidForSlot(this.getSlotIndex(), stack);
                    }

                    @Override
                    public int getSlotStackLimit() {
                        return 1;
                    }
                });
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 160));
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            if (index < TILE_SLOTS_COUNT) {
                if (!this.mergeItemStack(stack, TILE_SLOTS_COUNT, this.inventorySlots.size(), true)) return null;
            } else if (tileEntity.isItemValidForSlot(0, stack)) {
                if (!this.mergeItemStack(stack, 0, TILE_SLOTS_COUNT, false)) return null;
            } else {
                return null;
            }
            if (stack.stackSize == 0) slot.putStack(null);
            else slot.onSlotChanged();
        }
        return result;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tileEntity.isUseableByPlayer(player);
    }
}
