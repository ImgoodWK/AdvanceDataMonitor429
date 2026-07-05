package com.imgood.textech.items;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Base for dimensional pocket upgrade cards with shared tooltip formatting.
 */
public abstract class AbstractPocketUpgradeCard extends Item {

    protected AbstractPocketUpgradeCard(int maxStackSize) {
        setMaxStackSize(maxStackSize);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    protected void addTooltipLine(List tooltip, EnumChatFormatting color, String langKey) {
        tooltip.add(color + StatCollector.translateToLocal(langKey));
    }

    @SideOnly(Side.CLIENT)
    protected void addPocketCardTooltip(List tooltip, String titleKey, String[] bodyKeys,
        EnumChatFormatting[] bodyColors) {
        addTooltipLine(tooltip, EnumChatFormatting.GOLD, titleKey);
        for (int i = 0; i < bodyKeys.length; i++) {
            EnumChatFormatting color = i < bodyColors.length ? bodyColors[i] : EnumChatFormatting.GRAY;
            addTooltipLine(tooltip, color, bodyKeys[i]);
        }
    }
}
