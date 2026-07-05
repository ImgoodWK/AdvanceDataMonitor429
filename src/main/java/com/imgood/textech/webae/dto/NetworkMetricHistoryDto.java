package com.imgood.textech.webae.dto;

import java.util.List;

/**
 * Rolling-window history of network-wide scalar metrics for the WebAE dashboard
 * trend charts. Returned by {@code GET /api/network/metrics?network=<id>}.
 *
 * <p>
 * All history arrays share the same length and align by index with
 * {@link #timestamps}. Empty when no samples have been collected yet.
 * </p>
 */
public class NetworkMetricHistoryDto {

    public int networkId;
    public List<Long> timestamps;
    public List<Integer> itemCountHistory;
    public List<Integer> fluidCountHistory;
    public List<Integer> essentiaCountHistory;
    public List<Long> bytesUsedHistory;
    public List<Long> bytesMaxHistory;
    public List<Double> bytesPercentHistory;
    public List<Long> itemTotalHistory;
    public List<Long> fluidTotalHistory;
    public List<Integer> activeCpuHistory;
    public List<Integer> busyCpuHistory;
    public List<Double> cpuBusyRatioHistory;
    public List<Integer> gtMachineCountHistory;
    public List<Integer> gtActiveCountHistory;
}
