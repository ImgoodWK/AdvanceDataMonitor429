package com.imgood.textech.webae.monitor;

/**
 * Read-only DTO for one data monitor chart binding slot.
 */
public final class MonitorDataBindingDto {

    public int slotIndex;
    public String dataType = "";
    public String kind = "";
    public String sourceKind = "";
    public String metricKey = "";
    public String title = "";
    public String displayName = "";
    public String xyz = "";
    public int bindDim;
    public int bindX;
    public int bindY;
    public int bindZ;
    public boolean enabled;
    public boolean networkWide;
    public double targetValue;
    public int revision;
}
