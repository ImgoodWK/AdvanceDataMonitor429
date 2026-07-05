package com.imgood.textech.webae.monitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DTO for one data monitor chart binding slot.
 */
public final class MonitorDataBindingDto {

    public int slotIndex;
    public String dataType = "";
    public String displayName = "";
    public String xyz = "";
    public int bindDim;
    public int bindX;
    public int bindY;
    public int bindZ;
    public boolean enabled;
    public boolean networkWide;
}
