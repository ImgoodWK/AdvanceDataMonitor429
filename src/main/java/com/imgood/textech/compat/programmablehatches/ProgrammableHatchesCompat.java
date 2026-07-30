package com.imgood.textech.compat.programmablehatches;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.Loader;

/**
 * Soft compatibility bridge for reobf's Programmable Hatches community mod.
 *
 * <p>
 * The bridge intentionally has no compile-time dependency on the add-on. Its programming-circuit NBT mirrors
 * {@code ItemProgrammingCircuit.wrap}: the wrapper item is {@code programmablehatches:prog_circuit}, and the
 * one-count target stack is stored under {@code targetCircuit} with a stable {@code string_id} instead of the
 * runtime numeric item id.
 * </p>
 */
public final class ProgrammableHatchesCompat {

    public static final String MOD_ID = "programmablehatches";
    public static final String PROGRAMMING_CIRCUIT_ID = MOD_ID + ":prog_circuit";
    private static final String TARGET_TAG = "targetCircuit";
    private static final String STRING_ID_TAG = "string_id";

    private ProgrammableHatchesCompat() {}

    public static boolean isInstalled() {
        return Loader.isModLoaded(MOD_ID) && getProgrammingCircuitItem() != null;
    }

    public static Item getProgrammingCircuitItem() {
        Object item = Item.itemRegistry.getObject(PROGRAMMING_CIRCUIT_ID);
        return item instanceof Item ? (Item) item : null;
    }

    public static boolean isProgrammingCircuit(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return PROGRAMMING_CIRCUIT_ID.equals(name != null ? name.toString() : "");
    }

    /** Wrap one non-consumable target item using Programmable Hatches' current V3 NBT format. */
    public static ItemStack wrap(ItemStack target, int amount) {
        Item wrapperItem = getProgrammingCircuitItem();
        if (wrapperItem == null) {
            throw new IllegalStateException("Programmable Hatches is not installed");
        }
        ItemStack wrapper = new ItemStack(wrapperItem, Math.max(1, amount));
        if (target == null || target.getItem() == null) {
            return wrapper;
        }

        ItemStack normalized = target.copy();
        normalized.stackSize = 1;
        NBTTagCompound targetNbt = normalized.writeToNBT(new NBTTagCompound());
        Object registryName = Item.itemRegistry.getNameForObject(normalized.getItem());
        if (registryName == null) {
            throw new IllegalArgumentException("Target item has no registry name");
        }
        targetNbt.setString(STRING_ID_TAG, registryName.toString());
        targetNbt.removeTag("id");

        NBTTagCompound wrapperNbt = new NBTTagCompound();
        wrapperNbt.setTag(TARGET_TAG, targetNbt);
        wrapper.setTagCompound(wrapperNbt);
        return wrapper;
    }

    /** Return the wrapped target item, or {@code null} for reset/invalid programming circuits. */
    public static ItemStack unwrap(ItemStack wrapper) {
        if (!isProgrammingCircuit(wrapper) || wrapper.getTagCompound() == null
            || !wrapper.getTagCompound()
                .hasKey(TARGET_TAG)) {
            return null;
        }
        NBTTagCompound targetNbt = (NBTTagCompound) wrapper.getTagCompound()
            .getCompoundTag(TARGET_TAG)
            .copy();
        String registryName = targetNbt.getString(STRING_ID_TAG);
        if (registryName != null && !registryName.isEmpty()) {
            Object targetItem = Item.itemRegistry.getObject(registryName);
            if (targetItem instanceof Item) {
                targetNbt.setInteger("id", Item.itemRegistry.getIDForObject(targetItem));
            }
        }
        return ItemStack.loadItemStackFromNBT(targetNbt);
    }
}
