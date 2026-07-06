package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.topology.FakeChannelAllocator.ChannelProbeResult;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility.CellSlot;
import com.imgood.textech.webae.topology.TopologyFacilityGrouper.AggregatedGroup;

/**
 * Builds a star pseudo-topology: controller → aggregated device-type groups (network-tool parity).
 * No synthetic cable nodes; channel tier is reflected on edges only.
 */
public final class TreeTopologyBuilder {

    private TreeTopologyBuilder() {}

    public static final class BuildResult {

        public List<TopologyNode> nodes = new ArrayList<TopologyNode>();
        public List<TopologyEdge> edges = new ArrayList<TopologyEdge>();
        public int channelDeviceCount;
        public int controllerCapacity;
        public int facilityCount;
    }

    public static BuildResult build(List<NetworkFacility> facilities, ChannelProbeResult probe) {
        return build(facilities, probe, null);
    }

    public static BuildResult build(List<NetworkFacility> facilities, ChannelProbeResult probe,
        List<CraftingCpuTopologyCollector.CpuClusterFacility> cpuClusters) {
        BuildResult result = new BuildResult();
        if (facilities == null && (cpuClusters == null || cpuClusters.isEmpty())) {
            return result;
        }
        if (facilities == null) {
            facilities = new ArrayList<NetworkFacility>();
        }

        result.facilityCount = facilities.size();

        List<NetworkFacility> controllers = filterType(facilities, TopologyNodeType.CONTROLLER);
        List<NetworkFacility> forGrouping = new ArrayList<NetworkFacility>();
        for (NetworkFacility facility : facilities) {
            if (facility.type == TopologyNodeType.CONTROLLER || facility.type == TopologyNodeType.CPU) {
                continue;
            }
            forGrouping.add(facility);
            if (facility.channelCost > 0) {
                result.channelDeviceCount++;
            }
        }

        TopologyNode root = createControllerNode(controllers);
        result.nodes.add(root);
        result.controllerCapacity = estimateControllerCapacity(controllers, probe);

        List<AggregatedGroup> groups = TopologyFacilityGrouper.group(forGrouping);
        int groupIndex = 0;
        for (AggregatedGroup group : groups) {
            TopologyNode node = createAggregatedNode(group, groupIndex++);
            result.nodes.add(node);
            String cableType = TopologyRules.cableTypeForLoad(group.channelCostSum);
            int edgeMax = group.channelCostSum > 0 ? Math.max(group.channelCostSum, TopologyRules.CABLE_SMART_MAX)
                : 0;
            result.edges.add(edge(root.id, node.id, group.channelCostSum, edgeMax, cableType));
        }

        appendCpuClusters(result, root.id, cpuClusters);
        assignStarLayout(result.nodes, root.id);
        return result;
    }

    public static void applyChannelTotals(TopologySnapshot.Meta meta, BuildResult tree, ChannelProbeResult probe) {
        if (meta.channelsSimulated == null) {
            meta.channelsSimulated = new TopologyEdge.ChannelInfo();
        }
        meta.channelsSimulated.used = tree.channelDeviceCount;
        meta.channelsSimulated.max = Math.max(tree.controllerCapacity, TopologyRules.CABLE_SMART_MAX);
        meta.channelsSimulated.available = true;
        meta.facilityCount = tree.facilityCount;

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

    private static void appendCpuClusters(BuildResult result, String rootId,
        List<CraftingCpuTopologyCollector.CpuClusterFacility> cpuClusters) {
        if (cpuClusters == null || cpuClusters.isEmpty()) {
            return;
        }
        int index = 0;
        for (CraftingCpuTopologyCollector.CpuClusterFacility cluster : cpuClusters) {
            TopologyNode node = createCpuClusterNode(cluster, index++);
            result.nodes.add(node);
            result.edges.add(edge(rootId, node.id, 0, 0, "smart"));
        }
    }

    private static TopologyNode createCpuClusterNode(CraftingCpuTopologyCollector.CpuClusterFacility cluster, int index) {
        TopologyNode node = new TopologyNode();
        node.id = "cpu:" + index + ":" + cluster.dim + ":" + cluster.x + ":" + cluster.y + ":" + cluster.z;
        node.type = TopologyNodeType.CPU.id;
        node.displayName = cluster.displayName;
        node.count = cluster.unitCount;
        node.channelCost = 0;
        node.iconItemId = TopologyRules.iconItemIdFor(TopologyNodeType.CPU);
        node.role = "branch";
        node.dim = cluster.dim;
        node.binX = cluster.x;
        node.binZ = cluster.z;

        TopologyNode.CpuSummary summary = new TopologyNode.CpuSummary();
        summary.name = cluster.name;
        summary.coProcessors = cluster.coProcessors;
        summary.availableStorage = cluster.availableStorage;
        summary.usedStorage = cluster.usedStorage;
        summary.busy = cluster.busy;
        summary.unitCount = cluster.unitCount;
        summary.storageUnits = cluster.storageUnits;
        summary.acceleratorUnits = cluster.acceleratorUnits;
        summary.monitorUnits = cluster.monitorUnits;
        node.cpuSummary = summary;

        for (CraftingCpuTopologyCollector.CpuClusterFacility.Unit unit : cluster.units) {
            TopologyNode.DeviceRecord record = new TopologyNode.DeviceRecord();
            record.className = "appeng.tile.crafting.TileCraftingTile";
            record.displayName = unit.accelerator ? "Crafting Co-Processor"
                : unit.storage ? "Crafting Storage Unit" : unit.monitor ? "Crafting Monitor" : "Crafting Unit";
            record.iconItemId = TopologyRules.iconItemIdFor(TopologyNodeType.CPU);
            record.x = unit.x;
            record.y = unit.y;
            record.z = unit.z;
            record.dim = unit.dim;
            record.channelCost = 0;
            node.devices.add(record);
        }
        return node;
    }

    private static TopologyNode createControllerNode(List<NetworkFacility> controllers) {
        TopologyNode node = new TopologyNode();
        node.id = "controller";
        node.type = TopologyNodeType.CONTROLLER.id;
        node.displayName = controllers.isEmpty() ? "ME Controller" : "ME Controller x" + controllers.size();
        node.count = Math.max(1, controllers.size());
        node.channelCost = 0;
        node.iconItemId = TopologyRules.iconItemIdFor(TopologyNodeType.CONTROLLER);
        node.role = "hub";
        for (NetworkFacility controller : controllers) {
            node.devices.add(toRecord(controller));
        }
        return node;
    }

    private static TopologyNode createAggregatedNode(AggregatedGroup group, int index) {
        TopologyNode node = new TopologyNode();
        String safeKey = group.groupKey.replace('|', '_')
            .replace(':', '_');
        node.id = "group:" + safeKey + ":" + index;
        node.type = group.type.id;
        node.displayName = group.count > 1 ? group.displayName + " x" + group.count : group.displayName;
        node.count = group.count;
        node.channelCost = group.channelCostSum;
        node.iconItemId = preferredIconItemId(group.type, group.iconItemId);
        node.role = "branch";

        for (NetworkFacility member : group.members) {
            node.devices.add(toRecord(member));
            if (member.type == TopologyNodeType.DRIVE) {
                for (CellSlot cell : member.cells) {
                    node.cellSlots.add(toCellRecord(cell));
                }
            }
        }
        return node;
    }

    private static TopologyNode.DeviceRecord toRecord(NetworkFacility facility) {
        TopologyNode.DeviceRecord record = new TopologyNode.DeviceRecord();
        record.className = facility.className;
        record.displayName = facility.displayName;
        record.iconItemId = preferredIconItemId(facility.type, facility.representationItemId);
        record.x = facility.x;
        record.y = facility.y;
        record.z = facility.z;
        record.dim = facility.dim;
        record.channelCost = facility.channelCost;
        return record;
    }

    private static String preferredIconItemId(TopologyNodeType type, String representationItemId) {
        if (representationItemId != null && !representationItemId.isEmpty()) {
            return representationItemId;
        }
        String tileIcon = TopologyRules.iconItemIdFor(type);
        return tileIcon == null ? "" : tileIcon;
    }

    private static TopologyNode.CellSlotRecord toCellRecord(CellSlot cell) {
        TopologyNode.CellSlotRecord record = new TopologyNode.CellSlotRecord();
        record.slot = cell.slot;
        record.empty = cell.empty;
        record.displayName = cell.displayName;
        record.itemId = cell.itemId;
        record.itemBytes = cell.itemBytes;
        record.fluidBytes = cell.fluidBytes;
        return record;
    }

    private static TopologyEdge edge(String from, String to, int used, int max, String cableType) {
        TopologyEdge edge = new TopologyEdge();
        edge.from = from;
        edge.to = to;
        edge.cableType = cableType;
        edge.channelsSimulated.used = used;
        edge.channelsSimulated.max = max;
        edge.channelsSimulated.available = max > 0;
        edge.channelsReal.available = false;
        return edge;
    }

    private static int estimateControllerCapacity(List<NetworkFacility> controllers, ChannelProbeResult probe) {
        if (probe != null && probe.available && probe.max > 0) {
            return probe.max;
        }
        int blocks = Math.max(1, controllers.size());
        return blocks * TopologyRules.CABLE_COVERED_MAX;
    }

    private static List<NetworkFacility> filterType(List<NetworkFacility> facilities, TopologyNodeType type) {
        List<NetworkFacility> out = new ArrayList<NetworkFacility>();
        for (NetworkFacility facility : facilities) {
            if (facility.type == type) {
                out.add(facility);
            }
        }
        return out;
    }

    /**
     * Star layout: hub at (0,0); satellites at depth 1 with evenly spaced sibling indices.
     */
    private static void assignStarLayout(List<TopologyNode> nodes, String rootId) {
        TopologyNode root = null;
        List<TopologyNode> satellites = new ArrayList<TopologyNode>();
        for (TopologyNode node : nodes) {
            if (rootId.equals(node.id)) {
                root = node;
            } else if (!TopologyNodeType.CELL.id.equals(node.type)) {
                satellites.add(node);
            }
        }
        if (root != null) {
            root.layoutX = 0;
            root.layoutY = 0;
        }
        int index = 0;
        for (TopologyNode satellite : satellites) {
            satellite.layoutX = 1;
            satellite.layoutY = index++;
        }
    }
}
