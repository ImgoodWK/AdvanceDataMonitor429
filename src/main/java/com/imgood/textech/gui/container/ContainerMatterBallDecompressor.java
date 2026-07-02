package com.imgood.textech.gui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.imgood.textech.gui.MatterBallDecompressorGuiLayout;
import com.imgood.textech.tileentity.MatterBallDecompressorUpgrades;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;
import com.imgood.textech.utils.MatterBallClusterUtil;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;

public class ContainerMatterBallDecompressor extends Container {

    public static final int INPUT_COUNT = TileEntityMatterBallDecompressor.INPUT_SLOTS;
    public static final int BUFFER_COUNT = TileEntityMatterBallDecompressor.BUFFER_SLOTS;
    public static final int UPGRADE_COUNT = TileEntityMatterBallDecompressor.UPGRADE_SLOTS;
    public static final int TILE_SLOTS = INPUT_COUNT + BUFFER_COUNT + UPGRADE_COUNT;

    private final TileEntityMatterBallDecompressor tile;
    private final MatterBallDecompressorGuiLayout.Metrics metrics;

    public ContainerMatterBallDecompressor(InventoryPlayer playerInventory, TileEntityMatterBallDecompressor tile) {
        this.tile = tile;
        this.metrics = MatterBallDecompressorGuiLayout.forBufferSide(tile.getBufferSide());
        IInventory input = tile.getInputInventory();
        IInventory buffer = tile.getBufferInventory();
        IInventory upgrades = tile.getUpgradeInventory();

        for (int row = 0; row < MatterBallDecompressorGuiLayout.INPUT_ROWS; row++) {
            addSlotToContainer(new Slot(input, row, MatterBallDecompressorGuiLayout.INPUT_X,
                MatterBallDecompressorGuiLayout.CONTENT_START_Y + row * MatterBallDecompressorGuiLayout.CELL) {

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return MatterBallClusterUtil.isMatterCluster(stack);
                }
            });
        }

        for (int index = 0; index < BUFFER_COUNT; index++) {
            final int bufferIndex = index;
            final int slotX;
            final int slotY;
            int side = metrics.bufferSide;
            int active = side * side;
            if (bufferIndex < active) {
                int offset = (MatterBallDecompressorGuiLayout.MAX_BUFFER_SIDE - side) / 2;
                int row = bufferIndex / side;
                int col = bufferIndex % side;
                slotX = MatterBallDecompressorGuiLayout.BUFFER_REGION_X
                    + (offset + col) * MatterBallDecompressorGuiLayout.CELL;
                slotY = MatterBallDecompressorGuiLayout.CONTENT_START_Y
                    + (offset + row) * MatterBallDecompressorGuiLayout.CELL;
            } else {
                int row = bufferIndex / MatterBallDecompressorGuiLayout.MAX_BUFFER_SIDE;
                int col = bufferIndex % MatterBallDecompressorGuiLayout.MAX_BUFFER_SIDE;
                slotX = MatterBallDecompressorGuiLayout.BUFFER_REGION_X
                    + col * MatterBallDecompressorGuiLayout.CELL;
                slotY = MatterBallDecompressorGuiLayout.CONTENT_START_Y
                    + row * MatterBallDecompressorGuiLayout.CELL;
            }
            addSlotToContainer(new Slot(buffer, bufferIndex, slotX, slotY) {

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return bufferIndex < tile.getActiveBufferSlots();
                }

                @Override
                public boolean canTakeStack(EntityPlayer player) {
                    return getHasStack();
                }
            });
        }

        for (int i = 0; i < UPGRADE_COUNT; i++) {
            final int upgradeSlot = i;
            addSlotToContainer(new Slot(upgrades, upgradeSlot,
                metrics.upgradeStartX + i * MatterBallDecompressorGuiLayout.CELL,
                MatterBallDecompressorGuiLayout.TOP_ROW_Y) {

                @Override
                public int getSlotStackLimit() {
                    return 1;
                }

                @Override
                public boolean isItemValid(ItemStack stack) {
                    if (stack == null || !(stack.getItem() instanceof IUpgradeModule)) {
                        return false;
                    }
                    Upgrades type = ((IUpgradeModule) stack.getItem()).getType(stack);
                    return MatterBallDecompressorUpgrades.acceptsUpgradeInSlot(upgradeSlot, type);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9,
                    metrics.playerInvX + col * MatterBallDecompressorGuiLayout.CELL,
                    metrics.playerInvY + row * MatterBallDecompressorGuiLayout.CELL));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col,
                metrics.playerInvX + col * MatterBallDecompressorGuiLayout.CELL,
                metrics.playerInvY + 58));
        }
    }

    public TileEntityMatterBallDecompressor getTile() {
        return tile;
    }

    public MatterBallDecompressorGuiLayout.Metrics getMetrics() {
        return metrics;
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
        } else if (stack.getItem() instanceof IUpgradeModule) {
            Upgrades type = ((IUpgradeModule) stack.getItem()).getType(stack);
            if (!tryMergeUpgrade(stack, type, bufferEnd, upgradeEnd)) {
                if (!mergeItemStack(stack, inputEnd, bufferEnd, false)) {
                    return null;
                }
            }
        } else if (!mergeItemStack(stack, inputEnd, bufferEnd, false)) {
            return null;
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

    private boolean tryMergeUpgrade(ItemStack stack, Upgrades type, int upgradeStart, int upgradeEnd) {
        for (int i = upgradeStart; i < upgradeEnd; i++) {
            Slot upgradeSlot = (Slot) inventorySlots.get(i);
            int slotIndex = i - upgradeStart;
            if (!MatterBallDecompressorUpgrades.acceptsUpgradeInSlot(slotIndex, type)) {
                continue;
            }
            if (mergeItemStack(stack, i, i + 1, false)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }
}
