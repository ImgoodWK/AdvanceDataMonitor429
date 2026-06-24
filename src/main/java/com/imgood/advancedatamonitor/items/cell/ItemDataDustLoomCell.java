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
 * - EN: Data Dust Loom Cell
 * - ZH: 数据织尘元件
 * Lang keys: item.dataDustLoomCell.name
 *
 * Early-stage data weaving that can only reconstruct dusts.
 */
public class ItemDataDustLoomCell extends AbstractDataLoomItemCell {

    @Override
    public IInventory getConfigInventory(ItemStack is) {
        return new DustLoomCellConfig(is);
    }

    @Override
    public double getItemRatePerSecond() {
        return Config.dataDustLoomCellItemRatePerSecond;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("adm.tooltip.data_dust_loom.story"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_dust_loom.mark_limit"));
        tooltip.add("");
        addCommonTooltip(stack, tooltip);
    }
}
