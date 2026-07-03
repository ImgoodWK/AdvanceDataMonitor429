package com.imgood.textech.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Space Upgrade Card
 * - ZH: 空间升级占
 * Lang keys: item.spaceUpgradeCard.name, adm.tooltip.pocket.space_card
 *
 * Stacks up to 64. Used to expand the Dimensional Pocket's per-page slot count.
 * 1 free slot by default; up to 62 effective upgrades (63 slots total per page).
 */
public class ItemSpaceUpgradeCard extends AbstractPocketUpgradeCard {

    public ItemSpaceUpgradeCard() {
        super(64);
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        addPocketCardTooltip(
            tooltip,
            "adm.tooltip.pocket.space_card.title",
            new String[] {
                "adm.tooltip.pocket.space_card.desc",
                "adm.tooltip.pocket.space_card.stackable",
                "adm.tooltip.pocket.space_card.page_unlock" },
            new EnumChatFormatting[] {
                EnumChatFormatting.GRAY,
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.DARK_AQUA });
        super.addInformation(stack, player, tooltip, advanced);
    }
}
