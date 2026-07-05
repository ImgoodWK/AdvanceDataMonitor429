package com.imgood.textech.webae.monitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DTO for one Advance Data Monitor and its bindings.
 */
public final class MonitorBindingDto {

    public int monitorDim;
    public int monitorX;
    public int monitorY;
    public int monitorZ;
    public String owner = "";
    public List<MonitorDataBindingDto> dataBindings = new ArrayList<MonitorDataBindingDto>();
    public List<MonitorGtBindingDto> gtBindings = new ArrayList<MonitorGtBindingDto>();
}
