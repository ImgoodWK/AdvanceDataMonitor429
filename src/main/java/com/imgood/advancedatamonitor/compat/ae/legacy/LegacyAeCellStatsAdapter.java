package com.imgood.advancedatamonitor.compat.ae.legacy;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;

import com.glodblock.github.common.storage.FluidCellInventoryHandler;
import com.glodblock.github.common.storage.IFluidCellInventory;
import com.imgood.advancedatamonitor.compat.ae.AeCellStats;
import com.imgood.advancedatamonitor.compat.ae.AeCellStatsAdapter;
import com.imgood.advancedatamonitor.compat.ae.AeStorageStatsAccumulator;
import com.imgood.advancedatamonitor.compat.ae.AeTypeCounts;

import appeng.api.AEApi;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;

/**
 * Pre-2.9.0 beta-1 cell statistics via AE2 {@link ICellInventoryHandler} and GlodBlock
 * {@link FluidCellInventoryHandler}. GlodBlock handlers take precedence to avoid double counting.
 */
public final class LegacyAeCellStatsAdapter implements AeCellStatsAdapter {

    public static final LegacyAeCellStatsAdapter INSTANCE = new LegacyAeCellStatsAdapter();

    private LegacyAeCellStatsAdapter() {}

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
        accumulateFluidInventory(fluidInventory, stats.fluidBytes, stats.fluidTypes);
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
        readFluidInventoryStats(fluidInv, out);
    }

    static void accumulateFluidInventory(IMEInventoryHandler fluidInventory, long[] fluidBytes, int[] fluidTypes) {
        if (fluidInventory == null) {
            return;
        }
        if (fluidInventory instanceof FluidCellInventoryHandler) {
            FluidCellInventoryHandler handler = (FluidCellInventoryHandler) fluidInventory;
            IFluidCellInventory cell = handler.getCellInv();
            if (cell != null) {
                fluidBytes[0] += cell.getTotalBytes();
                fluidBytes[1] += cell.getUsedBytes();
                fluidTypes[0] += AeTypeCounts.toInt(cell.getTotalFluidTypes());
                fluidTypes[1] += AeTypeCounts.toInt(cell.getStoredFluidTypes());
            }
            return;
        }
        if (fluidInventory instanceof ICellInventoryHandler) {
            ICellInventory cell = ((ICellInventoryHandler) fluidInventory).getCellInv();
            if (cell != null) {
                fluidBytes[0] += cell.getTotalBytes();
                fluidBytes[1] += cell.getUsedBytes();
                fluidTypes[0] += AeTypeCounts.toInt(cell.getTotalItemTypes());
                fluidTypes[1] += AeTypeCounts.toInt(cell.getStoredItemTypes());
            }
            return;
        }
        readGlodBlockHandlerReflect(fluidInventory, fluidBytes, null);
    }

    private static void readFluidInventoryStats(IMEInventoryHandler fluidInv, AeCellStats out) {
        if (fluidInv == null) {
            return;
        }
        if (fluidInv instanceof FluidCellInventoryHandler) {
            IFluidCellInventory cell = ((FluidCellInventoryHandler) fluidInv).getCellInv();
            if (cell != null) {
                fillFromGlodBlockCell(cell, out);
            }
            return;
        }
        if (fluidInv instanceof ICellInventoryHandler) {
            ICellInventory cell = ((ICellInventoryHandler) fluidInv).getCellInv();
            if (cell != null) {
                fillFromCellInventory(cell, out);
            }
            return;
        }
        long[] bytes = new long[2];
        readGlodBlockHandlerReflect(fluidInv, bytes, out);
        if (out.present) {
            out.totalBytes = bytes[0];
            out.usedBytes = bytes[1];
        }
    }

    private static void readGlodBlockHandlerReflect(IMEInventoryHandler fluidInv, long[] fluidBytes, AeCellStats out) {
        try {
            Class<?> glodHandlerClass = Class.forName("com.glodblock.github.common.storage.FluidCellInventoryHandler");
            if (!glodHandlerClass.isInstance(fluidInv)) {
                return;
            }
            Object handler = glodHandlerClass.cast(fluidInv);
            Method getCellInv = glodHandlerClass.getMethod("getCellInv");
            Object cellObj = getCellInv.invoke(handler);
            if (cellObj == null) {
                return;
            }
            long totalBytes = ((Long) cellObj.getClass()
                .getMethod("getTotalBytes")
                .invoke(cellObj)).longValue();
            long usedBytes = ((Long) cellObj.getClass()
                .getMethod("getUsedBytes")
                .invoke(cellObj)).longValue();
            if (fluidBytes != null) {
                fluidBytes[0] += totalBytes;
                fluidBytes[1] += usedBytes;
            }
            if (out != null) {
                out.present = true;
                out.totalBytes = totalBytes;
                out.usedBytes = usedBytes;
                out.infinite = isInfiniteByClassOrBytes(
                    cellObj.getClass()
                        .getName(),
                    totalBytes);
            }
        } catch (Throwable ignored) {
            // GlodBlock not available
        }
    }

    private static void fillFromCellInventory(ICellInventory cell, AeCellStats out) {
        out.present = true;
        out.totalBytes = cell.getTotalBytes();
        out.usedBytes = cell.getUsedBytes();
        out.totalTypes = AeTypeCounts.toInt(cell.getTotalItemTypes());
        out.usedTypes = AeTypeCounts.toInt(cell.getStoredItemTypes());
        out.infinite = isInfiniteCell(cell);
    }

    private static void fillFromGlodBlockCell(IFluidCellInventory cell, AeCellStats out) {
        out.present = true;
        out.totalBytes = cell.getTotalBytes();
        out.usedBytes = cell.getUsedBytes();
        out.totalTypes = AeTypeCounts.toInt(cell.getTotalFluidTypes());
        out.usedTypes = AeTypeCounts.toInt(cell.getStoredFluidTypes());
        out.infinite = isInfiniteByClassOrBytes(
            cell.getClass()
                .getName(),
            out.totalBytes);
    }

    public static boolean isInfiniteCell(ICellInventory cell) {
        if (cell == null) {
            return false;
        }
        return isInfiniteByClassOrBytes(
            cell.getClass()
                .getName(),
            cell.getTotalBytes());
    }

    static boolean isInfiniteByClassOrBytes(String className, long totalBytes) {
        String lower = className == null ? "" : className.toLowerCase();
        if (lower.contains("infinity") || lower.contains("infinite") || lower.contains("creative")) {
            return true;
        }
        if (totalBytes > 10_000_000_000_000L) {
            return true;
        }
        return totalBytes == Long.MAX_VALUE || totalBytes >= Long.MAX_VALUE / 2L;
    }
}
