package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.dto.StorageDto.CpuEntry;
import com.imgood.textech.webae.dto.StorageDto.EssentiaEntry;
import com.imgood.textech.webae.dto.StorageDto.FluidEntry;
import com.imgood.textech.webae.dto.StorageDto.ItemEntry;

/**
 * Paginated slice of a cached {@link StorageDto} for WebAE storage tabs.
 */
public class StoragePagedDto {

    public boolean success;
    public List<ItemEntry> items;
    public List<FluidEntry> fluids;
    public List<EssentiaEntry> essentia;
    public String nextCursor;
    public int totalEstimate;
    public boolean fromCache;
    public long cacheAgeMs;
    public long snapshotVersion;
    /** Summary fields from the parent snapshot (every page). */
    public int networkId;
    public long bytesUsed;
    public long bytesMax;
    public List<CpuEntry> cpus;
    /** Sum of amounts in the filtered item list (items endpoint only). */
    public long totalAmountSum;

    public StoragePagedDto() {
        this.items = new ArrayList<ItemEntry>();
        this.fluids = new ArrayList<FluidEntry>();
        this.essentia = new ArrayList<EssentiaEntry>();
        this.cpus = new ArrayList<CpuEntry>();
    }
}
