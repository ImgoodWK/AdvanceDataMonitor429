package com.imgood.advancedatamonitor.items;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Infinite Stack Upgrade Card
 * - ZH: 无尽堆叠升级卡
 *
 * Max 1. Sets per-slot stack limit to Integer.MAX_VALUE.
 */
public class ItemInfiniteStackUpgradeCard extends Item {

    public ItemInfiniteStackUpgradeCard() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public int getItemStackLimit() {
        return 1;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("adm.tooltip.pocket.infinite_stack_card.title"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.pocket.infinite_stack_card.desc"));
        tooltip.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("adm.tooltip.pocket.infinite_stack_card.max"));
        tooltip.add(EnumChatFormatting.DARK_RED + StatCollector.translateToLocal("adm.tooltip.pocket.infinite_stack_card.warning"));
        super.addInformation(stack, player, tooltip, advanced);
    }
}
