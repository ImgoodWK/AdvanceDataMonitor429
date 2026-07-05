package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

public class GtMachineListDto {

    public int networkId;
    public long timestamp;
    public List<GtMachineDto> machines = new ArrayList<GtMachineDto>();
}
