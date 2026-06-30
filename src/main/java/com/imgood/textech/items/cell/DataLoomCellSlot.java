package com.imgood.textech.items.cell;

/**
 * Identifies a data loom cell sitting in an ME drive slot or ME chest.
 */
public final class DataLoomCellSlot {

    public enum HostKind {
        DRIVE,
        CHEST
    }

    public final int dimensionId;
    public final int x;
    public final int y;
    public final int z;
    public final int slot;
    public final HostKind hostKind;

    public DataLoomCellSlot(int dimensionId, int x, int y, int z, int slot, HostKind hostKind) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
        this.hostKind = hostKind;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DataLoomCellSlot)) {
            return false;
        }
        DataLoomCellSlot slotRef = (DataLoomCellSlot) other;
        return dimensionId == slotRef.dimensionId && x == slotRef.x
            && y == slotRef.y
            && z == slotRef.z
            && slot == slotRef.slot
            && hostKind == slotRef.hostKind;
    }

    @Override
    public int hashCode() {
        int hash = dimensionId;
        hash = 31 * hash + x;
        hash = 31 * hash + y;
        hash = 31 * hash + z;
        hash = 31 * hash + slot;
        hash = 31 * hash + hostKind.ordinal();
        return hash;
    }
}
