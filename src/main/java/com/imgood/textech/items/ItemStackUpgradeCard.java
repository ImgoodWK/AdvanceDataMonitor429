package com.imgood.textech.items;

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
 * - EN: Stack Upgrade Card
 * - ZH: 堆叠升级�?
 *
 * Stacks up to 8. Each card doubles the per-slot stack limit (2^n).
 */
public class ItemStackUpgradeCard extends Item {

    public ItemStackUpgradeCard() {
        setMaxStackSize(8);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public int getItemStackLimit() {
        return 8;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("adm.tooltip.pocket.stack_card.title"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.pocket.stack_card.desc"));
        tooltip.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("adm.tooltip.pocket.stack_card.stackable"));
        tooltip.add(EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("adm.tooltip.pocket.stack_card.formula"));
        super.addInformation(stack, player, tooltip, advanced);
    }
}
