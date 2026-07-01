package com.imgood.textech.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;
import com.imgood.textech.utils.MatterBallClusterUtil;

public class ContainerMatterBallDecompressor extends Container {

    public static final int INPUT_COUNT = TileEntityMatterBallDecompressor.INPUT_SLOTS;
    public static final int BUFFER_COUNT = TileEntityMatterBallDecompressor.BUFFER_SLOTS;
    public static final int UPGRADE_COUNT = TileEntityMatterBallDecompressor.UPGRADE_SLOTS;
    public static final int TILE_SLOTS = INPUT_COUNT + BUFFER_COUNT + UPGRADE_COUNT;

    private final TileEntityMatterBallDecompressor tile;

    public ContainerMatterBallDecompressor(InventoryPlayer playerInventory, TileEntityMatterBallDecompressor tile) {
        this.tile = tile;
        IInventory input = tile.getInputInventory();
        IInventory buffer = tile.getBufferInventory();
        IInventory upgrades = tile.getUpgradeInventory();

        for (int row = 0; row < 9; row++) {
            addSlotToContainer(new Slot(input, row, 8, 18 + row * 18) {

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return MatterBallClusterUtil.isMatterCluster(stack);
                }
            });
        }

        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                addSlotToContainer(new Slot(buffer, index, 62 + col * 18, 18 + row * 18));
            }
        }

        for (int i = 0; i < UPGRADE_COUNT; i++) {
            addSlotToContainer(new Slot(upgrades, i, 224 + i * 18, 18) {

                @Override
                public int getSlotStackLimit() {
                    return 1;
                }
            });
        }

        int playerInvY = 18 + 9 * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(
                    new Slot(playerInventory, col + row * 9 + 9, 26 + col * 18, playerInvY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col, 26 + col * 18, playerInvY + 58));
        }
    }

    public TileEntityMatterBallDecompressor getTile() {
        return tile;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        int inputEnd = INPUT_COUNT;
        int bufferEnd = inputEnd + BUFFER_COUNT;
        int upgradeEnd = bufferEnd + UPGRADE_COUNT;
        int playerStart = upgradeEnd;

        if (index < inputEnd) {
            if (!mergeItemStack(stack, bufferEnd, playerStart + 36, true)) {
                return null;
            }
        } else if (index < bufferEnd) {
            if (!mergeItemStack(stack, playerStart, playerStart + 36, true)) {
                return null;
            }
        } else if (index < upgradeEnd) {
            if (!mergeItemStack(stack, playerStart, playerStart + 36, true)) {
                return null;
            }
        } else if (MatterBallClusterUtil.isMatterCluster(stack)) {
            if (!mergeItemStack(stack, 0, inputEnd, false)) {
                return null;
            }
        } else if (!mergeItemStack(stack, inputEnd, bufferEnd, false)) {
            if (!mergeItemStack(stack, upgradeEnd, upgradeEnd + UPGRADE_COUNT, false)) {
                return null;
            }
        }

        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        if (stack.stackSize == original.stackSize) {
            return null;
        }
        slot.onPickupFromSlot(player, stack);
        return original;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }
}
