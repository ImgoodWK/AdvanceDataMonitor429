package com.imgood.textech.compat.ae;

/** Mutable counters for network-wide item/fluid cell statistics. */
public final class AeStorageStatsAccumulator {

    public final long[] itemBytes = new long[2];
    public final int[] itemTypes = new int[2];
    public final long[] fluidBytes = new long[2];
    public final int[] fluidTypes = new int[2];
}
