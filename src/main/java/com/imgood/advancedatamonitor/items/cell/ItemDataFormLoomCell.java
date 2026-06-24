package com.imgood.advancedatamonitor.items.cell;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.imgood.advancedatamonitor.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Data Form Loom Cell
 * - ZH: 数据织形元件
 * Lang keys: item.dataFormLoomCell.name
 *
 * Mastered data weaving that can reconstruct any item.
 */
public class ItemDataFormLoomCell extends AbstractDataLoomItemCell {

    @Override
    public IInventory getConfigInventory(ItemStack is) {
        return new FormLoomCellConfig(is);
    }

    @Override
    public double getItemRatePerSecond() {
        return Config.dataFormLoomCellItemRatePerSecond;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("adm.tooltip.data_form_loom.story"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_form_loom.mark_limit"));
        tooltip.add("");
        addCommonTooltip(stack, tooltip);
    }
}
