package com.imgood.textech.webae.cpu;

import java.util.ArrayList;
import java.util.List;

/** Read-only capacity-planning summary calculated from CPU history. */
public class CpuCapacityPlanDto {

    public boolean success = true;
    public int schemaVersion = 1;
    public int networkId;
    public String networkKey;
    public String window;
    public long from;
    public long to;

    public Integer currentCpuCount;
    public Integer peakConcurrent;
    public Long p50DurationMs;
    public Long p95DurationMs;
    public Long p95QueueMs;
    public Double busyRatio;
    public Double storagePressure;
    public Integer stuckCount;
    public Integer coProcessorObservedMax;
    public Integer requiredCpuCountEstimate;
    public List<String> bottlenecks = new ArrayList<String>();
    public List<String> recommendations = new ArrayList<String>();
}
