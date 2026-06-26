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
 * - EN: Space Upgrade Card
 * - ZH: 空间升级卡
 * Lang keys: item.spaceUpgradeCard.name, adm.tooltip.pocket.space_card
 *
 * Stacks up to 64. Used to expand the Dimensional Pocket's per-page slot count.
 * 1 free slot by default; up to 62 effective upgrades (63 slots total per page).
 */
public class ItemSpaceUpgradeCard extends Item {

    public ItemSpaceUpgradeCard() {
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("adm.tooltip.pocket.space_card.title"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.pocket.space_card.desc"));
        tooltip
            .add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("adm.tooltip.pocket.space_card.stackable"));
        super.addInformation(stack, player, tooltip, advanced);
    }
}
