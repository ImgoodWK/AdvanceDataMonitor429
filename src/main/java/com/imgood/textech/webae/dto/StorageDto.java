package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * AE storage snapshot DTO for WebAE console JSON serialization.
 */
public class StorageDto {

    public int networkId;
    public long timestamp;
    public List<ItemEntry> items;
    public List<FluidEntry> fluids;
    public List<EssentiaEntry> essentia;
    public long bytesUsed;
    public long bytesMax;
    public List<CpuEntry> cpus;

    public StorageDto() {
        this.items = new ArrayList<ItemEntry>();
        this.fluids = new ArrayList<FluidEntry>();
        this.essentia = new ArrayList<EssentiaEntry>();
        this.cpus = new ArrayList<CpuEntry>();
    }

    public static class ItemEntry {

        public String itemId;
        public String displayName;
        public String registryName;
        public int meta;
        public long amount;
        public String nbtHash;

        public ItemEntry() {}

        public ItemEntry(String itemId, String displayName, String registryName, int meta, long amount,
            String nbtHash) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.registryName = registryName;
            this.meta = meta;
            this.amount = amount;
            this.nbtHash = nbtHash;
        }
    }

    public static class FluidEntry {

        public String fluidName;
        public long amount;

        public FluidEntry() {}

        public FluidEntry(String fluidName, long amount) {
            this.fluidName = fluidName;
            this.amount = amount;
        }
    }

    public static class EssentiaEntry {

        public String aspect;
        public long amount;

        public EssentiaEntry() {}

        public EssentiaEntry(String aspect, long amount) {
            this.aspect = aspect;
            this.amount = amount;
        }
    }

    public static class CpuEntry {

        public String name;
        public long storedItems;
        public long maxItems;
        public double craftingProgress;
        public boolean isBusy;
        public long availableStorage;
        public long usedStorage;
        public int coProcessors;
        public String finalOutputName;
        public long finalOutputAmount;
        public long elapsedTime;
        /** Crafting link block position (dimension from link world). */
        public int x;
        public int y;
        public int z;
        public int dim;
        /** Bound data monitor position for this network group. */
        public int monitorX;
        public int monitorY;
        public int monitorZ;
        public int monitorDim;
        public long remainingItems;
        public long startItems;

        public CpuEntry() {}

        public CpuEntry(String name, long storedItems, long maxItems, double craftingProgress, boolean isBusy,
            long availableStorage, long usedStorage, int coProcessors, String finalOutputName, long finalOutputAmount,
            long elapsedTime, int x, int y, int z, int dim, int monitorX, int monitorY, int monitorZ, int monitorDim,
            long remainingItems, long startItems) {
            this.name = name;
            this.storedItems = storedItems;
            this.maxItems = maxItems;
            this.craftingProgress = craftingProgress;
            this.isBusy = isBusy;
            this.availableStorage = availableStorage;
            this.usedStorage = usedStorage;
            this.coProcessors = coProcessors;
            this.finalOutputName = finalOutputName;
            this.finalOutputAmount = finalOutputAmount;
            this.elapsedTime = elapsedTime;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.monitorX = monitorX;
            this.monitorY = monitorY;
            this.monitorZ = monitorZ;
            this.monitorDim = monitorDim;
            this.remainingItems = remainingItems;
            this.startItems = startItems;
        }
    }
}
