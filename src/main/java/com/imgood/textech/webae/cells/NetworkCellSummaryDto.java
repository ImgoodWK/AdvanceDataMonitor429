package com.imgood.textech.webae.cells;

/**
 * Network storage cell / byte summary for WebAE (infinite cell detection).
 */
public final class NetworkCellSummaryDto {

    public int networkId;
    public long timestamp;
    public boolean hasInfiniteItemCells;
    public boolean hasInfiniteFluidCells;
    public long itemUsedBytes;
    public long itemTotalBytes;
    public long fluidUsedBytes;
    public long fluidTotalBytes;
    public long nonInfiniteItemUsed;
    public long nonInfiniteItemTotal;
    public long nonInfiniteFluidUsed;
    public long nonInfiniteFluidTotal;
    public double itemUsagePercent;
    public double fluidUsagePercent;
}
