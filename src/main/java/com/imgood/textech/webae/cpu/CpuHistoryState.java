package com.imgood.textech.webae.cpu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** JSON file state for one owner and stable AE network identity. */
public class CpuHistoryState {

    public int schemaVersion = 1;
    public String ownerUuid;
    public int networkId;
    public String networkKey;
    public List<CpuJobHistoryDto> jobs = new ArrayList<CpuJobHistoryDto>();
    public List<CpuSnapshotHistoryDto> snapshots = new ArrayList<CpuSnapshotHistoryDto>();

    /** Runtime-only dirty bit; never serialized. */
    public transient boolean dirty;

    /**
     * Runtime-only location from which this stable network identity was
     * restored. A network's display/runtime index can be reordered, so this
     * prevents the next save from overwriting a different stable identity
     * which currently occupies the new numeric slot.
     */
    public transient File backingFile;

    public CpuHistoryState() {}

    public CpuHistoryState(String ownerUuid, int networkId, String networkKey) {
        this.ownerUuid = ownerUuid;
        this.networkId = networkId;
        this.networkKey = networkKey;
    }
}
