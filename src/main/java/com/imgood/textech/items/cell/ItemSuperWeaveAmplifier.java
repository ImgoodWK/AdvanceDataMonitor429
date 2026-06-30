package com.imgood.textech.items.cell;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Super Weave Amplifier Card
 * - ZH: 超级编织增幅占
 * Lang keys: item.superWeaveAmplifier.name, adm.tooltip.super_weave_amplifier.title
 */
public class ItemSuperWeaveAmplifier extends Item implements IWeaveAmplifierCard, IUpgradeModule {

    public ItemSuperWeaveAmplifier() {
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public Upgrades getType(ItemStack itemstack) {
        return Upgrades.SUPERSPEED;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
        double perCard = DataLoomAmplifierRates.SUPER_MULTIPLIER;
        tooltip.add(
            EnumChatFormatting.LIGHT_PURPLE
                + StatCollector.translateToLocal("adm.tooltip.super_weave_amplifier.title"));
        tooltip.add(
            EnumChatFormatting.GRAY + String.format(
                StatCollector.translateToLocal("adm.tooltip.super_weave_amplifier.rate"),
                formatMultiplier(perCard, nf)));
        tooltip.add(
            EnumChatFormatting.GRAY + String.format(
                StatCollector.translateToLocal("adm.tooltip.weave_amplifier.scaling"),
                ItemWeaveAmplifier.formatStackedMultiplier(perCard, 1, nf),
                ItemWeaveAmplifier.formatStackedMultiplier(perCard, 2, nf),
                ItemWeaveAmplifier.formatStackedMultiplier(perCard, 4, nf),
                ItemWeaveAmplifier.formatStackedMultiplier(perCard, 8, nf)));
        tooltip.add(
            EnumChatFormatting.RED + StatCollector.translateToLocal("adm.tooltip.super_weave_amplifier.warning_title"));
        tooltip.add(
            EnumChatFormatting.DARK_RED
                + StatCollector.translateToLocal("adm.tooltip.super_weave_amplifier.warning_body"));
    }

    private static String formatMultiplier(double value, NumberFormat nf) {
        return ItemWeaveAmplifier.formatMultiplier(value, nf);
    }
}
