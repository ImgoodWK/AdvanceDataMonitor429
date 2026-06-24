package com.imgood.advancedatamonitor.items.cell;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.advancedatamonitor.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Data Source Loom Cell
 * - ZH: 数据织源元件
 * Lang keys: item.dataSourceLoomCell.name
 *
 * Weaves network data into Thaumcraft essentia (stored as aspect fluids in GTNH).
 */
public class ItemDataSourceLoomCell extends AbstractDataLoomFluidCell {

    @Override
    public int getFluidRatePerSecond() {
        return Config.dataSourceLoomCellEssentiaRatePerSecond;
    }

    @Override
    public List<FluidStack> getMarkedFluids(ItemStack cellStack) {
        if (cellStack == null || !(cellStack.getItem() instanceof ItemDataSourceLoomCell)) {
            return DataLoomCellUtil.newFluidList();
        }

        Set<String> seen = new HashSet<>();
        List<FluidStack> fluids = DataLoomCellUtil.newFluidList();
        appendEssentiaFluids(fluids, seen, DataLoomCellUtil.readPartitionFluids(getConfigInventory(cellStack)));
        if (fluids.isEmpty()) {
            appendEssentiaFluids(fluids, seen, DataLoomCellUtil.readPartitionFluidsFromNbt(cellStack));
        }
        appendEssentiaFluids(fluids, seen, readEssentiaMarkerFluids(cellStack));
        return fluids;
    }

    private List<FluidStack> readEssentiaMarkerFluids(ItemStack cellStack) {
        List<FluidStack> fluids = DataLoomCellUtil.newFluidList();
        if (cellStack.getTagCompound() == null || !cellStack.getTagCompound()
            .hasKey("essentiaMarkers")) {
            return fluids;
        }

        NBTTagList essentiaList = cellStack.getTagCompound()
            .getTagList("essentiaMarkers", 10);
        for (int i = 0; i < essentiaList.tagCount(); i++) {
            NBTTagCompound entry = essentiaList.getCompoundTagAt(i);
            String aspectTag = entry.getString("aspect");
            if (aspectTag == null || aspectTag.isEmpty()) {
                continue;
            }
            Fluid fluid = FluidRegistry.getFluid(aspectTag);
            if (fluid == null) {
                continue;
            }
            fluids.add(new FluidStack(fluid, 1000));
        }
        return fluids;
    }

    private void appendEssentiaFluids(List<FluidStack> out, Set<String> seen, List<FluidStack> candidates) {
        for (FluidStack fluid : candidates) {
            if (fluid == null || fluid.getFluid() == null) {
                continue;
            }
            if (!DataLoomCellUtil.isEssentiaFluid(fluid.getFluid())) {
                continue;
            }
            String key = fluid.getFluid()
                .getName();
            if (!seen.add(key)) {
                continue;
            }
            out.add(new FluidStack(fluid.getFluid(), 1000));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip
            .add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("adm.tooltip.data_source_loom.story"));
        tooltip.add(
            EnumChatFormatting.GRAY + String.format(
                StatCollector.translateToLocal("adm.tooltip.data_source_loom.mark_limit"),
                Config.dataLoomCellSyncIntervalSeconds));
        tooltip.add("");
        addCommonFluidTooltip(stack, tooltip);
    }
}
