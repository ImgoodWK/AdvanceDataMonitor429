package com.imgood.textech.webae.topology;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.topology.FakeChannelAllocator.ChannelProbeResult;
import com.imgood.textech.webae.topology.LogicalTopologyBuilder.BuildResult;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility;
import com.imgood.textech.webae.worldmap.WorldMapAePlacementCollector;
import com.imgood.textech.webae.worldmap.WorldMapAePlacementRecord;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;

/**
 * Builds a complete topology graph snapshot for one owner network.
 * Logical mode uses {@link NetworkStatusEnumerator} (network tool data) + {@link LogicalTopologyBuilder}.
 * Must run on the server main thread.
 */
public final class TopologySnapshot {

    public int networkId;
    public String mode;
    public long timestamp;
    public Meta meta = new Meta();
    public List<TopologyNode> nodes = new ArrayList<TopologyNode>();
    public List<TopologyEdge> edges = new ArrayList<TopologyEdge>();
    /** AE device/cable/part placements for world-map overlay (logical snapshots). */
    public List<WorldMapAePlacementRecord> aePlacements = new ArrayList<WorldMapAePlacementRecord>();

    private TopologySnapshot() {}

    public static TopologySnapshot build(String ownerUuid, int networkId, String mode) {
        TopologySnapshot snapshot = new TopologySnapshot();
        snapshot.networkId = networkId;
        snapshot.mode = normalizeMode(mode);
        snapshot.timestamp = System.currentTimeMillis();

        snapshot.meta.hubGroup = TopologyRules.HUB_GROUP;
        snapshot.meta.spatialBinSize = TopologyRules.SPATIAL_BIN_SIZE;
        snapshot.meta.showOccupiedChannels = true;

        List<NetworkFacility> facilities = NetworkStatusEnumerator.enumerate(ownerUuid, networkId);
        ChannelProbeResult probe = probeRealChannels(ownerUuid, networkId);

        if ("spatial".equals(snapshot.mode)) {
            snapshot.meta.layout = TopologyRules.LAYOUT_STAR;
            snapshot.nodes = buildSpatialNodes(facilities);
            FakeChannelAllocator.assignSpatialLayout(snapshot.nodes);
            snapshot.edges = FakeChannelAllocator.allocateStar(snapshot.nodes);
            applyLegacyChannelTotals(snapshot.meta, snapshot.edges, probe, facilities);
        } else {
            snapshot.meta.layout = TopologyRules.LAYOUT_TREE;
            List<CraftingCpuTopologyCollector.CpuClusterFacility> cpuClusters = CraftingCpuTopologyCollector
                .collect(ownerUuid, networkId);
            BuildResult tree = LogicalTopologyBuilder.buildTree(facilities, probe, cpuClusters);
            snapshot.nodes = tree.nodes;
            snapshot.edges = tree.edges;
            if (Config.webTopologySimulatedEnabled) {
                SimulatedLayoutBuilder.apply(tree, TopologyRules.LAYOUT_TREE);
            }
            LogicalTopologyBuilder.applyChannelTotals(snapshot.meta, tree, probe);
            snapshot.meta.renderLayout = "channel_lanes";
            snapshot.meta.channelTierHint = "dense_32_to_4x_smart_8";
            snapshot.meta.layoutUnitPx = 72;
            snapshot.meta.facilityCount = tree.facilityCount;
            snapshot.meta.channelModel = TopologyRules.CHANNEL_MODEL_V2;
        }

        if ("logical".equals(snapshot.mode)) {
            snapshot.aePlacements = WorldMapAePlacementCollector.collect(ownerUuid, networkId);
        }

        return snapshot;
    }

    private static void applyLegacyChannelTotals(TopologySnapshot.Meta meta, List<TopologyEdge> edges,
        ChannelProbeResult probe, List<NetworkFacility> facilities) {
        int used = 0;
        for (NetworkFacility facility : facilities) {
            if (facility.channelCost > 0) {
                used += facility.channelCost;
            }
        }
        if (meta.channelsSimulated == null) {
            meta.channelsSimulated = new TopologyEdge.ChannelInfo();
        }
        meta.channelsSimulated.used = used;
        meta.channelsSimulated.max = probe != null && probe.available && probe.max > 0 ? probe.max
            : TopologyRules.CABLE_COVERED_MAX;
        meta.channelsSimulated.available = true;
        if (probe != null && probe.available) {
            if (meta.channelsReal == null) {
                meta.channelsReal = new TopologyEdge.ChannelInfo();
            }
            meta.channelsReal.used = probe.used;
            meta.channelsReal.max = probe.max;
            meta.channelsReal.available = true;
        } else if (meta.channelsReal == null) {
            meta.channelsReal = new TopologyEdge.ChannelInfo();
            meta.channelsReal.available = false;
        }
    }

    private static List<TopologyNode> buildSpatialNodes(List<NetworkFacility> facilities) {
        Map<String, TopologyNode> byBin = new HashMap<String, TopologyNode>();
        for (NetworkFacility device : facilities) {
            if (device == null) {
                continue;
            }
            int binX = TopologyRules.spatialBinIndex(device.x);
            int binZ = TopologyRules.spatialBinIndex(device.z);
            String id = TopologyRules.spatialBinId(device.dim, binX, binZ);
            TopologyNode node = byBin.get(id);
            if (node == null) {
                node = new TopologyNode();
                node.id = id;
                node.type = TopologyNodeType.SPATIAL_BIN.id;
                node.displayName = "Dim " + device.dim + " [" + binX + "," + binZ + "]";
                node.channelCost = 0;
                node.iconItemId = TopologyRules.iconItemIdFor(TopologyNodeType.MISC);
                node.role = "branch";
                node.dim = device.dim;
                node.binX = binX;
                node.binZ = binZ;
                byBin.put(id, node);
            }
            node.count++;
            node.devices.add(toRecord(device));
            node.channelCost += device.channelCost;
        }
        return new ArrayList<TopologyNode>(byBin.values());
    }

    private static TopologyNode.DeviceRecord toRecord(NetworkFacility device) {
        TopologyNode.DeviceRecord record = new TopologyNode.DeviceRecord();
        record.className = device.className;
        record.displayName = device.displayName;
        record.iconItemId = device.representationItemId.isEmpty() ? TopologyRules.iconItemIdFor(device.type)
            : device.representationItemId;
        record.x = device.x;
        record.y = device.y;
        record.z = device.z;
        record.dim = device.dim;
        record.channelCost = device.channelCost;
        return record;
    }

    private static ChannelProbeResult probeRealChannels(String ownerUuid, int networkId) {
        IGrid grid = com.imgood.textech.webae.context.WebAeOwnerContext.getGrid(ownerUuid, networkId);
        return probeRealChannels(grid);
    }

    /**
     * Probe only real AE channel counters for an already-resolved Grid.
     *
     * <p>
     * This is shared by topology and network-health sampling so both surfaces use the same
     * conservative reflection fallback. The result is unavailable unless both used and maximum
     * values can be verified; simulated topology capacity is never returned here.
     * </p>
     */
    public static ChannelProbeResult probeRealChannels(IGrid grid) {
        ChannelProbeResult result = new ChannelProbeResult();
        result.available = false;
        if (grid == null) {
            return result;
        }

        int used = probeGridInt(grid, "getChannelsUsed", "getUsedChannels", "getChannelUsed");
        int max = probeGridInt(grid, "getChannelCapacity", "getMaxChannels", "getChannelMax");

        if (used < 0) {
            used = sumActiveNodeChannels(grid, true);
        }
        if (max < 0) {
            max = probeControllerMax(grid);
        }

        if (used >= 0 && max > 0) {
            result.used = used;
            result.max = max;
            result.available = true;
        }
        return result;
    }

    private static int probeGridInt(IGrid grid, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = grid.getClass()
                    .getMethod(name);
                Object val = m.invoke(grid);
                if (val instanceof Integer) {
                    return (Integer) val;
                }
                if (val instanceof Number) {
                    return ((Number) val).intValue();
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    /** Sum per-node used channels without treating each node max as additive capacity. */
    private static int sumActiveNodeChannels(IGrid grid, boolean used) {
        int total = 0;
        boolean any = false;
        try {
            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                for (IGridNode node : grid.getMachines(clazz)) {
                    int v = probeNodeInt(node, used);
                    if (v > 0) {
                        total += v;
                        any = true;
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Topology channel node probe failed", e);
        }
        return any ? total : -1;
    }

    private static int probeNodeInt(IGridNode node, boolean used) {
        String[] names = used ? new String[] { "getUsedChannels", "getChannelsUsed", "getChannelUsed" }
            : new String[] { "getMaxChannels", "getChannelCapacity", "getChannelMax" };
        for (String name : names) {
            try {
                Method m = node.getClass()
                    .getMethod(name);
                Object val = m.invoke(node);
                if (val instanceof Integer) {
                    return (Integer) val;
                }
                if (val instanceof Number) {
                    return ((Number) val).intValue();
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private static int probeControllerMax(IGrid grid) {
        try {
            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                String cn = clazz.getName();
                if (cn == null || !cn.contains("Controller")) {
                    continue;
                }
                for (IGridNode node : grid.getMachines(clazz)) {
                    int max = probeNodeInt(node, false);
                    if (max > 0) {
                        return max;
                    }
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static String normalizeMode(String mode) {
        if (mode != null && "spatial".equalsIgnoreCase(mode.trim())) {
            return "spatial";
        }
        return "logical";
    }

    public static final class Meta {

        public String layout;
        public String hubGroup;
        public int spatialBinSize;
        public boolean showOccupiedChannels;
        public TopologyEdge.ChannelInfo channelsSimulated;
        public TopologyEdge.ChannelInfo channelsReal;
        public int facilityCount;
        public String renderLayout;
        public String channelTierHint;
        public int layoutUnitPx;
        /** Channel budget model id, e.g. ae_budget_v2. */
        public String channelModel = "";
        /** Per-lane planned channel usage (logical ae_budget_v2). */
        public List<LaneInfo> lanes = new ArrayList<LaneInfo>();
        /** Zero-channel orbit device counts by podKind. */
        public Map<String, Integer> orbitCounts = new HashMap<String, Integer>();
    }

    /** One smart-lane capacity row for channel-budget meta. */
    public static final class LaneInfo {

        public int index;
        public int used;
        public int max;
        public boolean overflow;
        public List<String> primaryPodKinds = new ArrayList<String>();
    }
}
