package com.imgood.advancedatamonitor.items.cell;

import java.util.Map;

/**
 * Storage limits matching GTNH Artificial Universe ME storage cells (AE2 byte/type rules).
 * <ul>
 * <li>Item channel: 576P bytes, 63 types, 8 items per byte</li>
 * <li>Fluid channel: 4.5P bytes, 5 types, 2048 mB per byte (AE2FC)</li>
 * </ul>
 * Type-byte overhead follows AE2 {@code (int)(totalBytes / 128)} — for universe-tier cells that
 * value overflows {@code int} to {@code 0}, so only stored item/fluid volume counts toward used bytes.
 */
public final class DataLoomCellCapacity {

    private static final int AE2_TYPES_DIVISOR = 128;

    /** Binary petabyte multiplier used by GTNH AE2 tiers. */
    private static final long ONE_P_BYTES = 1024L * 1024L * 1024L * 1024L * 1024L;

    /** GTNH Artificial Universe item cell. */
    public static final long ITEM_TOTAL_BYTES = 576L * ONE_P_BYTES;
    public static final int ITEM_MAX_TYPES = 63;
    public static final int ITEM_UNITS_PER_BYTE = 8;

    /** GTNH Artificial Universe fluid cell (standard data flow cell). */
    public static final long FLUID_TOTAL_BYTES = 9L * ONE_P_BYTES / 2L;
    public static final int FLUID_MAX_TYPES = 5;
    /** Extended fluid loom cell — same type cap as universe item cells. */
    public static final int FLUID_EXTENDED_MAX_TYPES = ITEM_MAX_TYPES;
    public static final int FLUID_MB_PER_BYTE = 2048;

    private static final long MAX_SAFE_ITEM_UNITS = Long.MAX_VALUE / ITEM_UNITS_PER_BYTE;
    private static final long MAX_SAFE_FLUID_MB = Long.MAX_VALUE / FLUID_MB_PER_BYTE;

    private DataLoomCellCapacity() {}

    /** Matches AE2 {@code ItemBasicStorageCell} / AE2FC fluid cell {@code (int)(totalBytes / 128)}. */
    public static int getItemBytesPerTypeForApi() {
        return (int) (ITEM_TOTAL_BYTES / AE2_TYPES_DIVISOR);
    }

    public static int getFluidBytesPerTypeForApi() {
        return (int) (FLUID_TOTAL_BYTES / AE2_TYPES_DIVISOR);
    }

    private static long effectiveItemBytesPerType() {
        long raw = ITEM_TOTAL_BYTES / AE2_TYPES_DIVISOR;
        if (raw <= 0L || raw > Integer.MAX_VALUE) {
            return 0L;
        }
        return raw;
    }

    private static long effectiveFluidBytesPerType() {
        long raw = FLUID_TOTAL_BYTES / AE2_TYPES_DIVISOR;
        if (raw <= 0L || raw > Integer.MAX_VALUE) {
            return 0L;
        }
        return raw;
    }

    public static long getStoredItemCount(Map<String, Long> amounts) {
        long total = 0L;
        for (Long amount : amounts.values()) {
            if (amount != null && amount > 0L) {
                total += amount;
            }
        }
        return total;
    }

    public static long getStoredTypeCount(Map<String, Long> amounts) {
        long types = 0L;
        for (Long amount : amounts.values()) {
            if (amount != null && amount > 0L) {
                types++;
            }
        }
        return types;
    }

    public static long getUsedItemBytes(long storedTypes, long storedCount) {
        long bytesForCount = (storedCount + getUnusedItemSlots(storedCount)) / ITEM_UNITS_PER_BYTE;
        return storedTypes * effectiveItemBytesPerType() + bytesForCount;
    }

    public static long getFreeItemBytes(long storedTypes, long storedCount) {
        long used = getUsedItemBytes(storedTypes, storedCount);
        long free = ITEM_TOTAL_BYTES - used;
        return free > 0L ? free : 0L;
    }

    public static int getUnusedItemSlots(long storedCount) {
        long remainder = storedCount % ITEM_UNITS_PER_BYTE;
        if (remainder == 0L) {
            return 0;
        }
        return (int) (ITEM_UNITS_PER_BYTE - remainder);
    }

    public static long getRemainingItemTypes(long storedTypes, long storedCount) {
        long byTypeCap = ITEM_MAX_TYPES - storedTypes;
        if (byTypeCap <= 0L) {
            return 0L;
        }
        long bytesPerType = effectiveItemBytesPerType();
        if (bytesPerType <= 0L) {
            return byTypeCap;
        }
        long byBytes = getFreeItemBytes(storedTypes, storedCount) / bytesPerType;
        return Math.min(byTypeCap, byBytes);
    }

    public static boolean canHoldNewItemType(long storedTypes, long storedCount) {
        if (storedTypes >= ITEM_MAX_TYPES) {
            return false;
        }
        long bytesPerType = effectiveItemBytesPerType();
        long freeBytes = getFreeItemBytes(storedTypes, storedCount);
        if (bytesPerType <= 0L) {
            return freeBytes > 0L || getRemainingItemUnits(storedTypes, storedCount) > 0L;
        }
        return getRemainingItemTypes(storedTypes, storedCount) > 0L
            && (freeBytes > bytesPerType || (freeBytes == bytesPerType && getUnusedItemSlots(storedCount) > 0));
    }

    public static long getRemainingItemUnits(long storedTypes, long storedCount) {
        long freeBytes = getFreeItemBytes(storedTypes, storedCount);
        if (freeBytes <= 0L) {
            return 0L;
        }
        long fromBytes = scaleFreeBytesToItemUnits(freeBytes);
        if (fromBytes >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return fromBytes + getUnusedItemSlots(storedCount);
    }

    /**
     * AE2 partition rule when the workbench lists markers: total bytes are split across marker slots.
     */
    public static long getRemainingItemUnitsForMarker(int partitionMarkers, long globalStoredTypes,
        long globalStoredCount, long markerStoredCount) {
        long divisor = partitionMarkers > 0 ? partitionMarkers : ITEM_MAX_TYPES;
        long bytesPerType = effectiveItemBytesPerType();
        long perMarkerBytes = ITEM_TOTAL_BYTES / divisor;
        long markerBudgetBytes = perMarkerBytes > bytesPerType ? perMarkerBytes - bytesPerType : 0L;
        long markerBudgetUnits = scaleFreeBytesToItemUnits(markerBudgetBytes);
        if (markerBudgetUnits <= markerStoredCount) {
            return 0L;
        }
        long markerRemaining = markerBudgetUnits - markerStoredCount;
        return Math.min(markerRemaining, getRemainingItemUnits(globalStoredTypes, globalStoredCount));
    }

    public static long getUsedFluidBytes(long storedTypes, long storedMb) {
        long bytesForAmount = (storedMb + getUnusedFluidMb(storedMb)) / FLUID_MB_PER_BYTE;
        return storedTypes * effectiveFluidBytesPerType() + bytesForAmount;
    }

    public static long getFreeFluidBytes(long storedTypes, long storedMb) {
        long used = getUsedFluidBytes(storedTypes, storedMb);
        long free = FLUID_TOTAL_BYTES - used;
        return free > 0L ? free : 0L;
    }

    public static int getUnusedFluidMb(long storedMb) {
        long remainder = storedMb % FLUID_MB_PER_BYTE;
        if (remainder == 0L) {
            return 0;
        }
        return (int) (FLUID_MB_PER_BYTE - remainder);
    }

    public static long getRemainingFluidTypes(long storedTypes, long storedMb, int maxTypes) {
        long byTypeCap = maxTypes - storedTypes;
        if (byTypeCap <= 0L) {
            return 0L;
        }
        long bytesPerType = effectiveFluidBytesPerType();
        if (bytesPerType <= 0L) {
            return byTypeCap;
        }
        long byBytes = getFreeFluidBytes(storedTypes, storedMb) / bytesPerType;
        return Math.min(byTypeCap, byBytes);
    }

    public static boolean canHoldNewFluidType(long storedTypes, long storedMb, int maxTypes) {
        if (storedTypes >= maxTypes) {
            return false;
        }
        long bytesPerType = effectiveFluidBytesPerType();
        long freeBytes = getFreeFluidBytes(storedTypes, storedMb);
        if (bytesPerType <= 0L) {
            return freeBytes > 0L || getRemainingFluidMb(storedTypes, storedMb) > 0L;
        }
        return getRemainingFluidTypes(storedTypes, storedMb, maxTypes) > 0L
            && (freeBytes > bytesPerType || (freeBytes == bytesPerType && getUnusedFluidMb(storedMb) > 0));
    }

    public static long getRemainingFluidMb(long storedTypes, long storedMb) {
        long freeBytes = getFreeFluidBytes(storedTypes, storedMb);
        if (freeBytes <= 0L) {
            return 0L;
        }
        long fromBytes = scaleFreeBytesToFluidMb(freeBytes);
        if (fromBytes >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return fromBytes + getUnusedFluidMb(storedMb);
    }

    public static long getRemainingFluidMbForMarker(int partitionMarkers, long globalStoredTypes, long globalStoredMb,
        long markerStoredMb, int maxTypes) {
        long divisor = partitionMarkers > 0 ? partitionMarkers : maxTypes;
        long bytesPerType = effectiveFluidBytesPerType();
        long perMarkerBytes = FLUID_TOTAL_BYTES / divisor;
        long markerBudgetBytes = perMarkerBytes > bytesPerType ? perMarkerBytes - bytesPerType : 0L;
        long markerBudgetMb = scaleFreeBytesToFluidMb(markerBudgetBytes);
        if (markerBudgetMb <= markerStoredMb) {
            return 0L;
        }
        long markerRemaining = markerBudgetMb - markerStoredMb;
        long globalRemaining = getRemainingFluidMb(globalStoredTypes, globalStoredMb);
        return Math.min(markerRemaining, globalRemaining);
    }

    public static boolean hasItemCapacity(long storedTypes, long storedCount) {
        return getRemainingItemUnits(storedTypes, storedCount) > 0L;
    }

    public static boolean hasFluidCapacity(long storedTypes, long storedMb, int maxTypes) {
        return getRemainingFluidMb(storedTypes, storedMb) > 0L;
    }

    public static int getItemCellStatus(long storedTypes, long storedCount) {
        if (storedCount <= 0L) {
            return 1;
        }
        if (canHoldNewItemType(storedTypes, storedCount)) {
            return 2;
        }
        if (getRemainingItemUnits(storedTypes, storedCount) > 0L) {
            return 3;
        }
        return 4;
    }

    public static int getFluidCellStatus(long storedTypes, long storedMb, int maxTypes) {
        if (storedMb <= 0L) {
            return 1;
        }
        if (canHoldNewFluidType(storedTypes, storedMb, maxTypes)) {
            return 2;
        }
        if (getRemainingFluidMb(storedTypes, storedMb) > 0L) {
            return 3;
        }
        return 4;
    }

    private static long scaleFreeBytesToItemUnits(long freeBytes) {
        if (freeBytes <= 0L) {
            return 0L;
        }
        if (freeBytes >= MAX_SAFE_ITEM_UNITS) {
            return Long.MAX_VALUE;
        }
        return freeBytes * ITEM_UNITS_PER_BYTE;
    }

    private static long scaleFreeBytesToFluidMb(long freeBytes) {
        if (freeBytes <= 0L) {
            return 0L;
        }
        if (freeBytes >= MAX_SAFE_FLUID_MB) {
            return Long.MAX_VALUE;
        }
        return freeBytes * FLUID_MB_PER_BYTE;
    }
}
