package com.imgood.textech.compat.ae;

/** Safe narrowing for AE2 cell type counters that may be {@code long} on newer GTNH AE2 builds. */
public final class AeTypeCounts {

    private AeTypeCounts() {}

    public static int toInt(long count) {
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (count < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) count;
    }
}
