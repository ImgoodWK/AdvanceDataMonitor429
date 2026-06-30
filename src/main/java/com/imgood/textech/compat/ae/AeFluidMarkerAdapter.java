package com.imgood.textech.compat.ae;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Resolves Cell Workbench fluid partition markers to {@link FluidStack}. */
public interface AeFluidMarkerAdapter {

    FluidStack resolveMarkerFluid(ItemStack markerItem);
}
