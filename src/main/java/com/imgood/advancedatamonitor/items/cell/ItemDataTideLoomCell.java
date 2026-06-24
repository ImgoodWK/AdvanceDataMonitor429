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
 * - EN: Data Tide Loom Cell
 * - ZH: 数据织潮元件
 * Lang keys: item.dataTideLoomCell.name
 *
 * Advanced upgrade of Data Flow Cell. Weaves network data into fluids with up to 63 stored types.
 */
public class ItemDataTideLoomCell extends AbstractDataLoomFluidCell {

    @Override
    public int getMaxFluidTypes() {
        return DataLoomCellCapacity.FLUID_EXTENDED_MAX_TYPES;
    }

    @Override
    public IInventory getConfigInventory(ItemStack is) {
        return new FlowLoomCellConfig(is);
    }

    @Override
    public int getFluidRatePerSecond() {
        return Config.dataFlowCellFluidRatePerSecond;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("adm.tooltip.data_tide_loom.story"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_tide_loom.mark_limit"));
        tooltip.add("");
        addCommonFluidTooltip(stack, tooltip);
    }
}
