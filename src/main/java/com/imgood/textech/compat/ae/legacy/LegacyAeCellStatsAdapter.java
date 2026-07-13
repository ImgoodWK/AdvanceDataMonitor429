package com.imgood.textech.compat.ae.legacy;

import net.minecraft.item.ItemStack;

import com.imgood.textech.compat.ae.AeCellStats;
import com.imgood.textech.compat.ae.AeCellStatsAdapter;
import com.imgood.textech.compat.ae.AeStorageStatsAccumulator;
import com.imgood.textech.compat.ae.native_.NativeAeCellStatsAdapter;

/**
 * Retained as a type for {@link com.imgood.textech.compat.ae.AeCompat} wiring.
 * On GTNH 2.9.0-beta-2+ this delegates to {@link NativeAeCellStatsAdapter}
 * (ae2fc {@code FluidCellInventoryHandler} was removed).
 */
public final class LegacyAeCellStatsAdapter implements AeCellStatsAdapter {

    public static final LegacyAeCellStatsAdapter INSTANCE = new LegacyAeCellStatsAdapter();

    private LegacyAeCellStatsAdapter() {}

    @Override
    public void accumulateStorageStack(ItemStack stack, AeStorageStatsAccumulator stats) {
        NativeAeCellStatsAdapter.INSTANCE.accumulateStorageStack(stack, stats);
    }

    @Override
    public void readItemCellStats(ItemStack stack, AeCellStats out) {
        NativeAeCellStatsAdapter.INSTANCE.readItemCellStats(stack, out);
    }

    @Override
    public void readFluidCellStats(ItemStack stack, AeCellStats out) {
        NativeAeCellStatsAdapter.INSTANCE.readFluidCellStats(stack, out);
    }
}
