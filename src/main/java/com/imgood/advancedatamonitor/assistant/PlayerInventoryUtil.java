package com.imgood.advancedatamonitor.assistant;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public final class PlayerInventoryUtil {

    private PlayerInventoryUtil() {}

    public static long computeFitAmount(EntityPlayer player, ItemStack prototype, long maxAmount) {
        if (player == null || prototype == null || prototype.getItem() == null || maxAmount <= 0L) {
            return 0L;
        }
        int maxStack = prototype.getMaxStackSize();
        long totalFit = 0L;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack slot = player.inventory.getStackInSlot(i);
            if (slot == null || slot.stackSize <= 0) {
                totalFit += maxStack;
            } else if (areStacksMergeable(slot, prototype)) {
                long space = (long) maxStack - slot.stackSize;
                if (space > 0L) {
                    totalFit += space;
                }
            }
            if (totalFit >= maxAmount) {
                return maxAmount;
            }
        }
        return totalFit;
    }

    public static long insertIntoPlayerInventory(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.getItem() == null || stack.stackSize <= 0) {
            return 0L;
        }
        long remaining = stack.stackSize;
        for (int i = 0; i < player.inventory.getSizeInventory() && remaining > 0L; i++) {
            ItemStack slot = player.inventory.getStackInSlot(i);
            if (slot != null && areStacksMergeable(slot, stack) && slot.stackSize < slot.getMaxStackSize()) {
                int transfer = (int) Math.min(remaining, slot.getMaxStackSize() - slot.stackSize);
                slot.stackSize += transfer;
                remaining -= transfer;
            }
        }
        for (int i = 0; i < player.inventory.getSizeInventory() && remaining > 0L; i++) {
            ItemStack slot = player.inventory.getStackInSlot(i);
            if (slot == null) {
                int transfer = (int) Math.min(remaining, stack.getMaxStackSize());
                ItemStack placed = stack.copy();
                placed.stackSize = transfer;
                player.inventory.setInventorySlotContents(i, placed);
                remaining -= transfer;
            }
        }
        player.inventory.markDirty();
        return stack.stackSize - remaining;
    }

    private static boolean areStacksMergeable(ItemStack a, ItemStack b) {
        return a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }
}
