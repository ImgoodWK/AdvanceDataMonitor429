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
 * - EN: Data Flow Cell
 * - ZH: 数据涌流元件
 * Lang keys: item.dataFlowCell.name
 *
 * Weaves network data into fluids using AE2FC fluid-cell style markers.
 */
public class ItemDataFlowCell extends AbstractDataLoomFluidCell {

    @Override
    public IInventory getConfigInventory(ItemStack is) {
        return new FlowLoomCellConfig(is);
    }

    @Override
    public int getFluidRatePerSecond() {
        return Config.dataFlowCellFluidRatePerSecond;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("adm.tooltip.data_flow_cell.story"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_flow_cell.mark_limit"));
        tooltip.add("");
        addCommonFluidTooltip(stack, tooltip);
    }
}
