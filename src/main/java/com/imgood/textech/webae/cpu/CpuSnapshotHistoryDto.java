package com.imgood.textech.webae.cpu;

/** One CPU observation copied from the existing cached storage snapshot. */
public class CpuSnapshotHistoryDto {

    public long timestamp;
    public String cpuName;
    public boolean busy;
    public long storageUsed;
    public long storageMax;
    /** 0..1 when supplied by AE2; 0 when the source did not expose progress. */
    public double progress;
    public int coProcessors;

    public CpuSnapshotHistoryDto copy() {
        CpuSnapshotHistoryDto copy = new CpuSnapshotHistoryDto();
        copy.timestamp = timestamp;
        copy.cpuName = cpuName;
        copy.busy = busy;
        copy.storageUsed = storageUsed;
        copy.storageMax = storageMax;
        copy.progress = progress;
        copy.coProcessors = coProcessors;
        return copy;
    }
}
