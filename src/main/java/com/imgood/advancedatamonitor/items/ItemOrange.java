package com.imgood.advancedatamonitor.items;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * Orange item — when held or carried in inventory, the item's custom display name
 * is rendered as a billboard nameplate above the player's head (visible to self and others).
 * Also spawns a companion drone that follows the player.
 * <p>
 * Obtainable via dungeon chests (0.1%) or rare mob drops (0.05%).
 */
public class ItemOrange extends Item {

    private static final String NBT_KEY_CUSTOM_NAME = "customDisplayName";

    public ItemOrange() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking() && world.isRemote) {
            NBTTagCompound nbt = getOrCreateNBT(stack);
            String currentName = nbt.getString(NBT_KEY_CUSTOM_NAME);
            String hint = I18n.format("adm.orange.current_name") + " " + (currentName.isEmpty()
                ? I18n.format("adm.orange.no_name")
                : currentName);
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + hint));
            String usageHint = I18n.format("adm.orange.rename_hint");
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + usageHint));
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GOLD + I18n.format("adm.orange.tooltip.nameplate"));
        tooltip.add(EnumChatFormatting.GRAY + I18n.format("adm.orange.tooltip.rename"));
        tooltip.add(EnumChatFormatting.GRAY + I18n.format("adm.orange.tooltip.check"));
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && nbt.hasKey(NBT_KEY_CUSTOM_NAME)) {
            String name = nbt.getString(NBT_KEY_CUSTOM_NAME);
            if (!name.isEmpty()) {
                tooltip.add(EnumChatFormatting.GOLD + I18n.format("adm.orange.nameplate") + ": " + name);
            }
        }
        super.addInformation(stack, player, tooltip, advanced);
    }

    /**
     * Get the custom display name for the nameplate. Falls back to the item stack's
     * anvil display name, then the NBT-stored custom name.
     */
    public static String getNameplateText(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemOrange)) return null;

        // Priority 1: Anvil-renamed display name
        if (stack.hasDisplayName()) {
            return stack.getDisplayName();
        }

        // Priority 2: NBT-stored custom name
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && nbt.hasKey(NBT_KEY_CUSTOM_NAME)) {
            String name = nbt.getString(NBT_KEY_CUSTOM_NAME);
            if (!name.isEmpty()) return name;
        }

        return null;
    }

    public static void setCustomName(ItemStack stack, String name) {
        if (stack == null || !(stack.getItem() instanceof ItemOrange)) return;
        NBTTagCompound nbt = getOrCreateNBT(stack);
        nbt.setString(NBT_KEY_CUSTOM_NAME, name != null ? name : "");
        stack.setTagCompound(nbt);
    }

    private static NBTTagCompound getOrCreateNBT(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        return nbt;
    }
}
