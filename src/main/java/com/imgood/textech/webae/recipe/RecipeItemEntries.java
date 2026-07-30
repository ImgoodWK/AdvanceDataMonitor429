package com.imgood.textech.webae.recipe;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.imgood.textech.utils.NBTJsonParser;
import com.imgood.textech.webae.dto.RecipeDto.ItemEntry;

/**
 * Shared item stack → recipe item entry conversion (icon id + registry + meta).
 */
public final class RecipeItemEntries {

    private RecipeItemEntries() {}

    public static ItemEntry fromStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null || registryName.isEmpty()) registryName = "unknown";
        int meta = stack.getItemDamage();
        if (meta == Short.MAX_VALUE) meta = 0;
        String itemId = buildItemId(registryName, meta);
        ItemEntry entry = new ItemEntry(itemId, stack.getDisplayName(), registryName, meta, stack.stackSize);
        entry.nonConsumable = stack.stackSize <= 0;
        if (stack.getTagCompound() != null) {
            try {
                entry.nbt = NBTJsonParser.parseNBTToJson(stack.getTagCompound())
                    .toString();
            } catch (Exception ignored) {}
        }
        return entry;
    }

    /** Icon cache key: {@code mod:id} or {@code mod:id:meta} when meta &gt; 0. */
    public static String buildItemId(String registryName, int meta) {
        if (registryName == null || registryName.isEmpty()) return "unknown";
        if (meta <= 0) return registryName;
        return registryName + ":" + meta;
    }
}
