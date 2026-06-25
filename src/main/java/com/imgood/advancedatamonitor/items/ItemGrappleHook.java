package com.imgood.advancedatamonitor.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Grapple Hook
 * - ZH: 挂索器
 * Lang keys: item.grappleHook.name, adm.title.grappleHook, adm.title.grappleHookConfig
 */
public class ItemGrappleHook extends Item {

    public static final String NBT_TRAVEL_SPEED = "grappleTravelSpeed";

    public static final String NBT_SHOW_NODE_NAME = "grappleShowNodeName";

    public static final String NBT_SHOW_NODE_DISTANCE = "grappleShowNodeDistance";

    public ItemGrappleHook() {

        this.setMaxStackSize(1);

        this.setUnlocalizedName("grappleHook");

        this.setTextureName(AdvanceDataMonitor.MODID + ":grapple_hook");

    }

    public static boolean isHoldingHook(EntityPlayer player) {

        if (player == null) {

            return false;

        }

        ItemStack held = player.getHeldItem();

        return held != null && held.getItem() instanceof ItemGrappleHook;

    }

    public static boolean hasHookAnywhere(EntityPlayer player) {

        if (player == null) {

            return false;

        }

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {

            ItemStack stack = player.inventory.getStackInSlot(i);

            if (stack != null && stack.getItem() instanceof ItemGrappleHook) {

                return true;

            }

        }

        return false;

    }

    public static NBTTagCompound getOrCreateNBT(ItemStack stack) {

        if (stack == null) {

            return new NBTTagCompound();

        }

        if (!stack.hasTagCompound()) {

            stack.setTagCompound(new NBTTagCompound());

        }

        return stack.getTagCompound();

    }

    public static double getTravelSpeed(ItemStack stack) {

        if (stack == null || !stack.hasTagCompound()) {

            return Config.grappleMoveSpeed;

        }

        NBTTagCompound tag = stack.getTagCompound();

        if (tag.hasKey(NBT_TRAVEL_SPEED)) {

            return tag.getDouble(NBT_TRAVEL_SPEED);

        }

        return Config.grappleMoveSpeed;

    }

    public static boolean getShowNodeName(ItemStack stack) {

        if (stack == null || !stack.hasTagCompound()) {

            return true;

        }

        NBTTagCompound tag = stack.getTagCompound();

        if (tag.hasKey(NBT_SHOW_NODE_NAME)) {

            return tag.getBoolean(NBT_SHOW_NODE_NAME);

        }

        return true;

    }

    public static boolean getShowNodeDistance(ItemStack stack) {

        if (stack == null || !stack.hasTagCompound()) {

            return true;

        }

        NBTTagCompound tag = stack.getTagCompound();

        if (tag.hasKey(NBT_SHOW_NODE_DISTANCE)) {

            return tag.getBoolean(NBT_SHOW_NODE_DISTANCE);

        }

        return true;

    }

    public static ItemStack findHookStack(EntityPlayer player) {

        if (player == null) {

            return null;

        }

        ItemStack held = player.getHeldItem();

        if (held != null && held.getItem() instanceof ItemGrappleHook) {

            return held;

        }

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {

            ItemStack stack = player.inventory.getStackInSlot(i);

            if (stack != null && stack.getItem() instanceof ItemGrappleHook) {

                return stack;

            }

        }

        return null;

    }

    public static double resolveTravelSpeed(EntityPlayer player) {

        return getTravelSpeed(findHookStack(player));

    }

    public static double getTravelSpeed(EntityPlayer player) {

        return resolveTravelSpeed(player);

    }

    public static void setTravelSpeed(ItemStack stack, double speed) {

        if (stack == null) {

            return;

        }

        getOrCreateNBT(stack).setDouble(NBT_TRAVEL_SPEED, speed);

    }

    public static void setShowNodeName(ItemStack stack, boolean show) {

        if (stack == null) {

            return;

        }

        getOrCreateNBT(stack).setBoolean(NBT_SHOW_NODE_NAME, show);

    }

    public static void setShowNodeDistance(ItemStack stack, boolean show) {

        if (stack == null) {

            return;

        }

        getOrCreateNBT(stack).setBoolean(NBT_SHOW_NODE_DISTANCE, show);

    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("adm.tooltip.grappleHook.story"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.grappleHook.usage"));
        list.add(
            StatCollector.translateToLocalFormatted(
                "adm.label.grapple.travel_speed",
                String.format("%.1f", getTravelSpeed(stack))));

        list.add(

            StatCollector.translateToLocalFormatted(

                "adm.label.grapple.show_node_name",

                StatCollector.translateToLocalFormatted(getShowNodeName(stack) ? "adm.label.on" : "adm.label.off")));

        list.add(

            StatCollector.translateToLocalFormatted(

                "adm.label.grapple.show_node_distance",

                StatCollector
                    .translateToLocalFormatted(getShowNodeDistance(stack) ? "adm.label.on" : "adm.label.off")));

    }

}
