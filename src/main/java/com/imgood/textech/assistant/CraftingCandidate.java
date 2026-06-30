package com.imgood.textech.assistant;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class CraftingCandidate {

    public final int index;
    public final String displayName;
    public final String registryName;
    public final int meta;
    public final long amount;
    public final NBTTagCompound itemNbt;

    public CraftingCandidate(int index, ItemStack stack, long amount) {
        this.index = index;
        this.displayName = stack == null ? "Unknown" : stack.getDisplayName();
        this.registryName = stack == null || stack.getItem() == null ? "" : ItemStackUtils.registryName(stack);
        this.meta = stack == null ? 0 : stack.getItemDamage();
        this.amount = amount;
        this.itemNbt = new NBTTagCompound();
        if (stack != null) {
            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            copy.writeToNBT(this.itemNbt);
        }
    }

    public ItemStack toItemStack() {
        return ItemStack.loadItemStackFromNBT(this.itemNbt);
    }
}
