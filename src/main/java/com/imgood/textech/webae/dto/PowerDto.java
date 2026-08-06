package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Wireless power / steam snapshot DTO for WebAE console JSON serialization.
 */
public class PowerDto {

    public int networkId;
    public long timestamp;
    public long euStored;
    public long euMax;
    public double euInRate;
    public double euOutRate;
    public long steamStored;
    public long steamMax;
    /** True when a wireless steam provider is installed and readable for this owner/team. */
    public boolean steamSupported;
    /** True only when steamMax is a real provider capacity, not a placeholder. */
    public boolean steamCapacityKnown;
    public double steamInRate;
    public double steamOutRate;
    public List<Double> euHistory;
    public List<Double> steamHistory;
    /** 与 euHistory 一一对应的时间戳（epoch ms）。 */
    public List<Long> euHistoryTimestamps;
    /** 与 steamHistory 一一对应的时间戳（epoch ms）。 */
    public List<Long> steamHistoryTimestamps;

    public PowerDto() {
        this.euHistory = new ArrayList<Double>();
        this.steamHistory = new ArrayList<Double>();
        this.euHistoryTimestamps = new ArrayList<Long>();
        this.steamHistoryTimestamps = new ArrayList<Long>();
    }
}
