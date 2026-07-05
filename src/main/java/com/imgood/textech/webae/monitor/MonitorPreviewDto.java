package com.imgood.textech.webae.monitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only chart preview for one monitor slot (Phase 11).
 */
public final class MonitorPreviewDto {

    public int monitorDim;
    public int monitorX;
    public int monitorY;
    public int monitorZ;
    public int slotIndex;
    public String dataType = "";
    public String displayName = "";
    public boolean enabled;
    public List<Double> values = new ArrayList<Double>();
    public double yMin;
    public double yMax;
    public int dataLimit;
    public long timestamp;
}
