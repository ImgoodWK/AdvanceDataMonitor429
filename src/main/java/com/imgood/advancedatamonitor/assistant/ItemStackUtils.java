package com.imgood.advancedatamonitor.assistant;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class ItemStackUtils {

    private ItemStackUtils() {}

    public static String registryName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name == null ? "" : name;
    }

    public static boolean fuzzyNameMatches(ItemStack stack, String query) {
        if (stack == null || query == null
            || query.trim()
                .isEmpty()) {
            return false;
        }
        String normalized = query.toLowerCase()
            .trim();
        return stack.getDisplayName()
            .toLowerCase()
            .contains(normalized)
            || registryName(stack).toLowerCase()
                .contains(normalized)
            || stack.getUnlocalizedName()
                .toLowerCase()
                .contains(normalized);
    }
}
