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
 * - EN: Page Upgrade Card
 * - ZH: 翻页升级卡
 * Lang keys: item.pageUpgradeCard.name, adm.tooltip.pocket.page_card
 *
 * Stacks up to 8. Each card adds one extra page to the Dimensional Pocket.
 * Requires space upgrades to be fully stacked (64) before any page upgrade takes effect.
 */
public class ItemPageUpgradeCard extends Item {

    public ItemPageUpgradeCard() {
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
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("adm.tooltip.pocket.page_card.title"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.pocket.page_card.desc"));
        tooltip.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("adm.tooltip.pocket.page_card.prereq"));
        super.addInformation(stack, player, tooltip, advanced);
    }
}
