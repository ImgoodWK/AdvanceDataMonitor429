package com.imgood.textech.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemInfiniteStackUpgradeCard extends AbstractPocketUpgradeCard {

    public ItemInfiniteStackUpgradeCard() {
        super(1);
    }

    @Override
    public int getItemStackLimit() {
        return 1;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        addPocketCardTooltip(
            tooltip,
            "adm.tooltip.pocket.infinite_stack_card.title",
            new String[] {
                "adm.tooltip.pocket.infinite_stack_card.desc",
                "adm.tooltip.pocket.infinite_stack_card.max",
                "adm.tooltip.pocket.infinite_stack_card.warning" },
            new EnumChatFormatting[] {
                EnumChatFormatting.GRAY,
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.DARK_RED });
        super.addInformation(stack, player, tooltip, advanced);
    }
}
