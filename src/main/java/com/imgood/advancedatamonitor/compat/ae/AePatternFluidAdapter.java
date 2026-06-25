package com.imgood.advancedatamonitor.compat.ae;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/** Reads fluid inputs/outputs from AE crafting patterns. */
public interface AePatternFluidAdapter {

    boolean hasFluidInputs(ICraftingPatternDetails pattern);

    boolean hasFluidOutputs(ICraftingPatternDetails pattern);

    void appendFluidInputs(StringBuilder builder, ICraftingPatternDetails pattern);

    void appendFluidOutputs(StringBuilder builder, ICraftingPatternDetails pattern);
}
