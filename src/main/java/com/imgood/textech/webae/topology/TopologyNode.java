package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.List;

/**
 * A topology graph node — either a logical device-class aggregate or a spatial bin.
 */
public class TopologyNode {

    public String id;
    public String type;
    /** Fine-grained device subtype (e.g. bus_import, terminal_me). */
    public String subtype = "";
    public String displayName;
    public int count;
    public int channelCost;
    public String iconItemId;
    public String role;
    public double layoutX;
    public double layoutY;
    /** Layout sector: north | south | east | west | center | branch0..branch3 */
    public String layoutSector = "center";
    /** Smart branch index (0-3) for channel-consuming groups. */
    public int branchIndex = -1;
    /** Pattern count badge for drive/chest aggregated nodes. */
    public int patternCount;

    /** Simulated cable-bus grid coordinates (logical mode). */
    public double simGridX;
    public double simGridY;
    /** block | cable_h | cable_v | cable_corner | junction */
    public String simKind = "block";

    public List<CellSlotRecord> cellSlots = new ArrayList<CellSlotRecord>();
    /** Pattern-only cell slots (detail drawer; not graph leaves). */
    public List<PatternSlotRecord> patternSlots = new ArrayList<PatternSlotRecord>();
    public List<DeviceRecord> devices = new ArrayList<DeviceRecord>();

    /** Aggregated crafting CPU summary — only set for nodes of type {@code cpu}. */
    public CpuSummary cpuSummary;

    /** Optional spatial metadata (spatial mode only). */
    public int dim = Integer.MIN_VALUE;
    public int binX = Integer.MIN_VALUE;
    public int binZ = Integer.MIN_VALUE;

    public static class DeviceRecord {

        public String className;
        public String displayName;
        public String iconItemId;
        public int x;
        public int y;
        public int z;
        public int dim;
        public int channelCost;
    }

    public static class CellSlotRecord {

        public int slot;
        public boolean empty;
        public String displayName = "";
        public String itemId = "";
        public long itemBytes;
        public long fluidBytes;
    }

    public static class PatternSlotRecord {

        public int slot;
        public String displayName = "";
        public String itemId = "";
    }

    /** Aggregated stats for a crafting CPU cluster (one multi-block ICraftingCPU). */
    public static class CpuSummary {

        public String name = "";
        public int coProcessors;
        public long availableStorage;
        public long usedStorage;
        public boolean busy;
        public int unitCount = 1;
        public int storageUnits;
        public int acceleratorUnits;
        public int monitorUnits;
    }
}
