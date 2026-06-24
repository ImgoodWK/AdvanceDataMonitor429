package com.imgood.advancedatamonitor.items.cell;

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
 * - EN: Weave Amplifier Card
 * - ZH: 编织增幅卡
 * Lang keys: item.weaveAmplifier.name, adm.tooltip.weave_amplifier.title
 */
public class ItemWeaveAmplifier extends Item implements IWeaveAmplifierCard, IUpgradeModule {

    public ItemWeaveAmplifier() {
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public Upgrades getType(ItemStack itemstack) {
        return Upgrades.SPEED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
        double perCard = DataLoomAmplifierRates.NORMAL_MULTIPLIER;
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("adm.tooltip.weave_amplifier.title"));
        tooltip.add(
            EnumChatFormatting.GRAY + String.format(
                StatCollector.translateToLocal("adm.tooltip.weave_amplifier.rate"),
                formatMultiplier(perCard, nf)));
        tooltip.add(
            EnumChatFormatting.GRAY + String.format(
                StatCollector.translateToLocal("adm.tooltip.weave_amplifier.scaling"),
                formatStackedMultiplier(perCard, 1, nf),
                formatStackedMultiplier(perCard, 2, nf),
                formatStackedMultiplier(perCard, 4, nf),
                formatStackedMultiplier(perCard, 8, nf)));
        tooltip.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("adm.tooltip.weave_amplifier.max"));
    }

    static String formatStackedMultiplier(double perCard, int cardCount, NumberFormat nf) {
        double total = 1.0D;
        for (int i = 0; i < cardCount; i++) {
            total *= perCard;
        }
        return formatMultiplier(total, nf);
    }

    static String formatMultiplier(double value, NumberFormat nf) {
        if (value >= 1.0E9D) {
            return nf.format(value);
        }
        if (value == Math.rint(value)) {
            return nf.format((long) value);
        }
        return nf.format(value);
    }
}
