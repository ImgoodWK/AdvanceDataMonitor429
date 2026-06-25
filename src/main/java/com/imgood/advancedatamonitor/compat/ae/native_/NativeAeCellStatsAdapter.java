package com.imgood.advancedatamonitor.compat.ae.native_;

import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.compat.ae.AeCellStats;
import com.imgood.advancedatamonitor.compat.ae.AeCellStatsAdapter;
import com.imgood.advancedatamonitor.compat.ae.AeStorageStatsAccumulator;
import com.imgood.advancedatamonitor.compat.ae.AeTypeCounts;
import com.imgood.advancedatamonitor.compat.ae.legacy.LegacyAeCellStatsAdapter;

import appeng.api.AEApi;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;

/**
 * GTNH 2.9.0 beta-1+ cell statistics using AE2 native fluid cells on {@link StorageChannel#FLUIDS}.
 * Fluid type counts use {@link ICellInventory#getTotalItemTypes()} / {@link ICellInventory#getStoredItemTypes()}.
 */
public final class NativeAeCellStatsAdapter implements AeCellStatsAdapter {

    public static final NativeAeCellStatsAdapter INSTANCE = new NativeAeCellStatsAdapter();

    private NativeAeCellStatsAdapter() {}

    @Override
    public void accumulateStorageStack(ItemStack stack, AeStorageStatsAccumulator stats) {
        if (stack == null || stats == null) {
            return;
        }

        IMEInventoryHandler itemInventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.ITEMS);
        if (itemInventory instanceof ICellInventoryHandler) {
            ICellInventory cell = ((ICellInventoryHandler) itemInventory).getCellInv();
            if (cell != null) {
                stats.itemBytes[0] += cell.getTotalBytes();
                stats.itemBytes[1] += cell.getUsedBytes();
                stats.itemTypes[0] += AeTypeCounts.toInt(cell.getTotalItemTypes());
                stats.itemTypes[1] += AeTypeCounts.toInt(cell.getStoredItemTypes());
            }
        }

        IMEInventoryHandler fluidInventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.FLUIDS);
        if (fluidInventory instanceof ICellInventoryHandler) {
            ICellInventory cell = ((ICellInventoryHandler) fluidInventory).getCellInv();
            if (cell != null) {
                stats.fluidBytes[0] += cell.getTotalBytes();
                stats.fluidBytes[1] += cell.getUsedBytes();
                stats.fluidTypes[0] += AeTypeCounts.toInt(cell.getTotalItemTypes());
                stats.fluidTypes[1] += AeTypeCounts.toInt(cell.getStoredItemTypes());
            }
        }
    }

    @Override
    public void readItemCellStats(ItemStack stack, AeCellStats out) {
        out.clear();
        if (stack == null) {
            return;
        }
        IMEInventoryHandler itemInv = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.ITEMS);
        if (!(itemInv instanceof ICellInventoryHandler)) {
            return;
        }
        ICellInventory cell = ((ICellInventoryHandler) itemInv).getCellInv();
        if (cell == null) {
            return;
        }
        fillFromCellInventory(cell, out);
    }

    @Override
    public void readFluidCellStats(ItemStack stack, AeCellStats out) {
        out.clear();
        if (stack == null) {
            return;
        }
        IMEInventoryHandler fluidInv = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.FLUIDS);
        if (!(fluidInv instanceof ICellInventoryHandler)) {
            return;
        }
        ICellInventory cell = ((ICellInventoryHandler) fluidInv).getCellInv();
        if (cell == null) {
            return;
        }
        fillFromCellInventory(cell, out);
    }

    private static void fillFromCellInventory(ICellInventory cell, AeCellStats out) {
        out.present = true;
        out.totalBytes = cell.getTotalBytes();
        out.usedBytes = cell.getUsedBytes();
        out.totalTypes = AeTypeCounts.toInt(cell.getTotalItemTypes());
        out.usedTypes = AeTypeCounts.toInt(cell.getStoredItemTypes());
        out.infinite = LegacyAeCellStatsAdapter.isInfiniteCell(cell);
    }
}
