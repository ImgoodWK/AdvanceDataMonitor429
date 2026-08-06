package com.imgood.textech.webae.cpu;

import java.util.ArrayList;
import java.util.List;

/** Read-only response for {@code /api/network/cpu/history}. */
public class CpuHistoryResponseDto {

    public boolean success = true;
    public int schemaVersion = 1;
    public int networkId;
    public String networkKey;
    public long from;
    public long to;
    public boolean truncated;
    public List<CpuJobHistoryDto> jobs = new ArrayList<CpuJobHistoryDto>();
    public List<CpuSnapshotHistoryDto> snapshots = new ArrayList<CpuSnapshotHistoryDto>();
}
