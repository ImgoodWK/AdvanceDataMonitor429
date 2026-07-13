package com.imgood.textech.items.cell;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEFluidStackType;

/**
 * Fluid-channel data loom cells (flow / source essentia) for AE2 2.9 {@link IStorageCell}.
 */
public interface IDataLoomFluidCell extends IStorageCell {

    int getFluidRatePerSecond();

    List<FluidStack> getMarkedFluids(ItemStack cellStack);

    @Override
    default IAEStackType getStackType() {
        return AEFluidStackType.FLUID_STACK_TYPE;
    }
}
