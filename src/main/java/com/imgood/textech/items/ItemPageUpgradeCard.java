package com.imgood.textech.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemPageUpgradeCard extends AbstractPocketUpgradeCard {

    public ItemPageUpgradeCard() {
        super(8);
    }

    @Override
    public int getItemStackLimit() {
        return 8;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        addPocketCardTooltip(
            tooltip,
            "adm.tooltip.pocket.page_card.title",
            new String[] { "adm.tooltip.pocket.page_card.desc", "adm.tooltip.pocket.page_card.prereq" },
            new EnumChatFormatting[] { EnumChatFormatting.GRAY, EnumChatFormatting.YELLOW });
        super.addInformation(stack, player, tooltip, advanced);
    }
}
