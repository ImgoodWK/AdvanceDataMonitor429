package com.imgood.advancedatamonitor.compat.ae;

import net.minecraft.item.ItemStack;

/** Unified ME storage cell byte/type accounting for item and fluid channels. */
public interface AeCellStatsAdapter {

    void accumulateStorageStack(ItemStack stack, AeStorageStatsAccumulator stats);

    void readItemCellStats(ItemStack stack, AeCellStats out);

    void readFluidCellStats(ItemStack stack, AeCellStats out);
}
