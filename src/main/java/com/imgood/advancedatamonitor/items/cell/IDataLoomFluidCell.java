package com.imgood.advancedatamonitor.items.cell;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.storage.IStorageFluidCell;

/**
 * Fluid-channel data loom cells (flow / source essentia).
 */
public interface IDataLoomFluidCell extends IStorageFluidCell {

    int getFluidRatePerSecond();

    List<FluidStack> getMarkedFluids(ItemStack cellStack);
}
