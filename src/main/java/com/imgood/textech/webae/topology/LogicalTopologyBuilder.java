package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.topology.ChannelBranchAllocator.Allocation;
import com.imgood.textech.webae.topology.ChannelBranchAllocator.Result;
import com.imgood.textech.webae.topology.FakeChannelAllocator.ChannelProbeResult;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility.CellSlot;
import com.imgood.textech.webae.topology.TopologyFacilityGrouper.AggregatedGroup;

/**
 * Builds logical AE topology graphs: tree layout with dense/smart branches, or double-ring star semantics.
 */
public final class LogicalTopologyBuilder {

    public static final String CONTROLLER_ID = "controller";
    public static final String DENSE_TRUNK_ID = "virtual:dense_trunk";

    private LogicalTopologyBuilder() {}

    public static final class BuildResult {

        public List<TopologyNode> nodes = new ArrayList<TopologyNode>();
        public List<TopologyEdge> edges = new ArrayList<TopologyEdge>();
        public int channelDeviceCount;
        public int controllerCapacity;
        public int facilityCount;
    }

    public static BuildResult build(List<NetworkFacility> facilities, ChannelProbeResult probe,
        List<CraftingCpuTopologyCollector.CpuClusterFacility> cpuClusters) {
        return buildTree(facilities, probe, cpuClusters);
    }

    public static BuildResult buildTree(List<NetworkFacility> facilities, ChannelProbeResult probe,
        List<CraftingCpuTopologyCollector.CpuClusterFacility> cpuClusters) {
        BuildResult result = new BuildResult();
        if (facilities == null && (cpuClusters == null || cpuClusters.isEmpty())) {
            return result;
        }
        if (facilities == null) {
            facilities = new ArrayList<NetworkFacility>();
        }
        result.facilityCount = facilities.size();

        List<NetworkFacility> controllers = filterSubtype(facilities, TopologyRules.SUB_CONTROLLER);
        List<NetworkFacility> energyFacilities = filterEnergy(facilities);
        List<NetworkFacility> forGrouping = new ArrayList<NetworkFacility>();
        for (NetworkFacility facility : facilities) {
            if (facility == null) {
                continue;
            }
            if (TopologyRules.SUB_CONTROLLER.equals(facility.subtype) || TopologyRules.SUB_CPU.equals(facility.subtype)
                || TopologyRules.isEnergySubtype(facility.subtype)) {
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

        List<AggregatedGroup> allGroups = TopologyFacilityGrouper.group(forGrouping);
        List<AggregatedGroup> topGroups = new ArrayList<AggregatedGroup>();
        List<AggregatedGroup> channelGroups = new ArrayList<AggregatedGroup>();
        List<AggregatedGroup> zeroGroups = new ArrayList<AggregatedGroup>();

        for (AggregatedGroup group : allGroups) {
            if (group.channelCostSum <= 0 || TopologyRules.isZeroChannelSubtype(group.subtype)) {
                zeroGroups.add(group);
            } else if (TopologyRules.isTopTierSubtype(group.subtype)) {
                topGroups.add(group);
            } else {
                channelGroups.add(group);
            }
        }

        appendEnergyGroups(result, root.id, energyFacilities);
        appendTopTierGroups(result, root.id, topGroups);

        TopologyNode denseTrunk = createVirtualCableNode(
            DENSE_TRUNK_ID,
            TopologyNodeType.CABLE_DENSE,
            "Dense Trunk (32ch)",
            1,
            0,
            "south");
        result.nodes.add(denseTrunk);
        result.edges.add(
            edge(
                root.id,
                denseTrunk.id,
                result.channelDeviceCount,
                TopologyRules.CABLE_COVERED_MAX,
                "dense",
                -1,
                false));

        TopologyNode[] branches = new TopologyNode[ChannelBranchAllocator.BRANCH_COUNT];
        for (int i = 0; i < ChannelBranchAllocator.BRANCH_COUNT; i++) {
            branches[i] = createVirtualCableNode(
                branchId(i),
                TopologyNodeType.CABLE_SMART,
                "Smart Branch " + i + " (8ch)",
                2,
                i,
                "branch" + i);
            result.nodes.add(branches[i]);
            result.edges.add(
                edge(
                    denseTrunk.id,
                    branches[i].id,
                    ChannelBranchAllocator.CHANNELS_PER_BRANCH,
                    ChannelBranchAllocator.CHANNELS_PER_BRANCH,
                    "smart",
                    i,
                    false));
        }

        Result allocation = ChannelBranchAllocator.allocate(channelGroups);
        for (AggregatedGroup group : channelGroups) {
            Allocation alloc = allocation.byGroupKey.get(group.groupKey);
            int branchIndex = alloc != null ? alloc.branchIndex : 0;
            int slotIndex = alloc != null ? alloc.slotIndex : 0;
            TopologyNode node = createAggregatedNode(group, branchIndex, slotIndex);
            result.nodes.add(node);
            result.edges.add(
                edge(
                    branches[branchIndex].id,
                    node.id,
                    group.channelCostSum,
                    ChannelBranchAllocator.CHANNELS_PER_BRANCH,
                    "smart",
                    branchIndex,
                    false));
        }

        for (int i = 0; i < ChannelBranchAllocator.BRANCH_COUNT; i++) {
            if (allocation.branchGroupCount[i] == 0) {
                String emptyId = branchId(i) + ":empty";
                TopologyNode placeholder = createEmptyBranchPlaceholder(emptyId, i);
                result.nodes.add(placeholder);
                TopologyEdge emptyEdge = edge(
                    branches[i].id,
                    emptyId,
                    0,
                    ChannelBranchAllocator.CHANNELS_PER_BRANCH,
                    "smart",
                    i,
                    true);
                result.edges.add(emptyEdge);
            }
        }

        appendZeroChannelGroups(result, root.id, zeroGroups);
        appendCpuClusters(result, root.id, cpuClusters);
        assignTreeLayout(result.nodes, root.id);
        return result;
    }

    /** Assigns double-ring semantic coordinates (inner=channel devices, outer=zero-channel). */
    public static BuildResult buildStar(List<NetworkFacility> facilities, ChannelProbeResult probe,
        List<CraftingCpuTopologyCollector.CpuClusterFacility> cpuClusters) {
        BuildResult tree = buildTree(facilities, probe, cpuClusters);
        assignStarLayout(tree.nodes, CONTROLLER_ID);
        return tree;
    }

    public static void applyChannelTotals(TopologySnapshot.Meta meta, BuildResult tree, ChannelProbeResult probe) {
        if (meta.channelsSimulated == null) {
            meta.channelsSimulated = new TopologyEdge.ChannelInfo();
        }
        meta.channelsSimulated.used = tree.channelDeviceCount;
        meta.channelsSimulated.max = Math.max(tree.controllerCapacity, TopologyRules.CABLE_COVERED_MAX);
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

    private static void appendEnergyGroups(BuildResult result, String rootId, List<NetworkFacility> energyFacilities) {
        if (energyFacilities.isEmpty()) {
            return;
        }
        List<AggregatedGroup> groups = TopologyFacilityGrouper.group(energyFacilities);
        int westIndex = 0;
        int eastIndex = 0;
        for (AggregatedGroup group : groups) {
            String sector = westIndex <= eastIndex ? "west" : "east";
            int slot = "west".equals(sector) ? westIndex++ : eastIndex++;
            TopologyNode node = createAggregatedNode(group, -1, slot);
            node.layoutSector = sector;
            node.role = "hub";
            result.nodes.add(node);
            result.edges.add(edge(rootId, node.id, 0, 0, "smart", -1, false));
        }
    }

    private static void appendTopTierGroups(BuildResult result, String rootId, List<AggregatedGroup> topGroups) {
        int index = 0;
        for (AggregatedGroup group : topGroups) {
            TopologyNode node = createAggregatedNode(group, -1, index++);
            node.layoutSector = "north";
            result.nodes.add(node);
            result.edges
                .add(edge(rootId, node.id, group.channelCostSum, TopologyRules.CABLE_SMART_MAX, "smart", -1, false));
        }
    }

    private static void appendZeroChannelGroups(BuildResult result, String rootId, List<AggregatedGroup> zeroGroups) {
        int index = 0;
        for (AggregatedGroup group : zeroGroups) {
            TopologyNode node = createAggregatedNode(group, -1, index++);
            node.layoutSector = "south";
            result.nodes.add(node);
            result.edges.add(edge(rootId, node.id, 0, 0, "smart", -1, false));
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
            node.layoutSector = "south";
            result.nodes.add(node);
            result.edges.add(edge(rootId, node.id, 0, 0, "smart", -1, false));
        }
    }

    private static TopologyNode createCpuClusterNode(CraftingCpuTopologyCollector.CpuClusterFacility cluster,
        int index) {
        TopologyNode node = new TopologyNode();
        node.id = "cpu:" + index + ":" + cluster.dim + ":" + cluster.x + ":" + cluster.y + ":" + cluster.z;
        node.type = TopologyNodeType.CPU.id;
        node.subtype = TopologyRules.SUB_CPU;
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
        node.id = CONTROLLER_ID;
        node.type = TopologyNodeType.CONTROLLER.id;
        node.subtype = TopologyRules.SUB_CONTROLLER;
        node.displayName = controllers.isEmpty() ? "ME Controller" : "ME Controller x" + controllers.size();
        node.count = Math.max(1, controllers.size());
        node.channelCost = 0;
        node.iconItemId = TopologyRules.iconItemIdFor(TopologyNodeType.CONTROLLER);
        node.role = "hub";
        node.layoutSector = "center";
        for (NetworkFacility controller : controllers) {
            node.devices.add(toRecord(controller));
        }
        return node;
    }

    private static TopologyNode createEmptyBranchPlaceholder(String id, int branchIndex) {
        TopologyNode node = new TopologyNode();
        node.id = id;
        node.type = TopologyNodeType.MISC.id;
        node.subtype = "branch_empty";
        node.displayName = "Empty branch";
        node.count = 0;
        node.channelCost = 0;
        node.role = "empty_branch";
        node.branchIndex = branchIndex;
        node.layoutX = 3;
        node.layoutY = 0;
        node.layoutSector = "branch" + branchIndex;
        return node;
    }

    private static TopologyNode createVirtualCableNode(String id, TopologyNodeType type, String name, int depth,
        int sibling, String sector) {
        TopologyNode node = new TopologyNode();
        node.id = id;
        node.type = type.id;
        node.subtype = type.id;
        node.displayName = name;
        node.count = 1;
        node.channelCost = 0;
        node.iconItemId = TopologyRules.iconItemIdFor(type);
        node.role = "branch";
        node.layoutX = depth;
        node.layoutY = sibling;
        node.layoutSector = sector;
        node.simKind = "junction";
        return node;
    }

    private static TopologyNode createAggregatedNode(AggregatedGroup group, int branchIndex, int slotIndex) {
        TopologyNode node = new TopologyNode();
        String safeKey = group.groupKey.replace('|', '_')
            .replace(':', '_');
        node.id = "group:" + safeKey + ":" + branchIndex + ":" + slotIndex;
        node.type = group.type.id;
        node.subtype = group.subtype;
        node.displayName = group.count > 1 ? group.displayName + " x" + group.count : group.displayName;
        node.count = group.count;
        node.channelCost = group.channelCostSum;
        node.patternCount = group.patternCountSum;
        node.iconItemId = preferredIconItemId(group.type, group.iconItemId);
        node.role = "branch";
        node.branchIndex = branchIndex;
        node.layoutX = branchIndex >= 0 ? 3 : (TopologyRules.isTopTierSubtype(group.subtype) ? -1 : 1);
        node.layoutY = slotIndex;
        if (branchIndex >= 0) {
            node.layoutSector = "branch" + branchIndex;
        }

        for (NetworkFacility member : group.members) {
            node.devices.add(toRecord(member));
            if (member.type == TopologyNodeType.DRIVE || TopologyRules.SUB_CHEST.equals(member.subtype)
                || TopologyRules.SUB_DRIVE.equals(member.subtype)) {
                for (CellSlot cell : member.cells) {
                    if (cell.isPattern) {
                        TopologyNode.PatternSlotRecord pattern = new TopologyNode.PatternSlotRecord();
                        pattern.slot = cell.slot;
                        pattern.displayName = cell.displayName;
                        pattern.itemId = cell.itemId;
                        node.patternSlots.add(pattern);
                    } else {
                        node.cellSlots.add(toCellRecord(cell));
                    }
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

    private static TopologyEdge edge(String from, String to, int used, int max, String cableType, int branchIndex,
        boolean emptyBranch) {
        TopologyEdge edge = new TopologyEdge();
        edge.from = from;
        edge.to = to;
        edge.cableType = cableType;
        edge.branchIndex = branchIndex;
        edge.emptyBranch = emptyBranch;
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

    private static List<NetworkFacility> filterSubtype(List<NetworkFacility> facilities, String subtype) {
        List<NetworkFacility> out = new ArrayList<NetworkFacility>();
        for (NetworkFacility facility : facilities) {
            if (facility != null && subtype.equals(facility.subtype)) {
                out.add(facility);
            }
        }
        return out;
    }

    private static List<NetworkFacility> filterEnergy(List<NetworkFacility> facilities) {
        List<NetworkFacility> out = new ArrayList<NetworkFacility>();
        for (NetworkFacility facility : facilities) {
            if (facility != null && TopologyRules.isEnergySubtype(facility.subtype)) {
                out.add(facility);
            }
        }
        return out;
    }

    private static String branchId(int index) {
        return "virtual:branch:" + index;
    }

    private static void assignTreeLayout(List<TopologyNode> nodes, String rootId) {
        for (TopologyNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (rootId.equals(node.id)) {
                node.layoutX = 0;
                node.layoutY = 0;
                node.layoutSector = "center";
                continue;
            }
            if (TopologyNodeType.CELL.id.equals(node.type)) {
                continue;
            }
            if ("west".equals(node.layoutSector)) {
                node.layoutX = 0;
                node.layoutY = -1 - node.layoutY;
            } else if ("east".equals(node.layoutSector)) {
                node.layoutX = 0;
                node.layoutY = node.layoutY + 1;
            } else if ("north".equals(node.layoutSector)) {
                node.layoutX = -1;
                node.layoutY = node.layoutY;
            } else if ("south".equals(node.layoutSector) && node.branchIndex < 0 && node.layoutX <= 1) {
                node.layoutX = 1;
            }
        }
    }

    /**
     * Double-ring star semantics: inner ring (layoutX=1) for channel devices, outer (layoutX=2) for zero-channel.
     */
    public static void assignStarLayout(List<TopologyNode> nodes, String rootId) {
        List<TopologyNode> inner = new ArrayList<TopologyNode>();
        List<TopologyNode> outer = new ArrayList<TopologyNode>();
        TopologyNode root = null;

        for (TopologyNode node : nodes) {
            if (node == null || TopologyNodeType.CELL.id.equals(node.type)) {
                continue;
            }
            if (rootId.equals(node.id)) {
                root = node;
                continue;
            }
            if (isVirtualCableNode(node)) {
                continue;
            }
            if (node.channelCost > 0) {
                inner.add(node);
            } else {
                outer.add(node);
            }
        }

        if (root != null) {
            root.layoutX = 0;
            root.layoutY = 0;
            root.layoutSector = "center";
        }

        sortBySubtype(inner);
        sortBySubtype(outer);

        for (int i = 0; i < inner.size(); i++) {
            TopologyNode node = inner.get(i);
            node.layoutX = 1;
            node.layoutY = i;
            if (TopologyRules.isTopTierSubtype(node.subtype)) {
                node.layoutSector = "north";
            } else {
                node.layoutSector = "south";
            }
        }
        for (int i = 0; i < outer.size(); i++) {
            TopologyNode node = outer.get(i);
            node.layoutX = 2;
            node.layoutY = i;
            if (TopologyRules.isEnergySubtype(node.subtype)) {
                node.layoutSector = i % 2 == 0 ? "west" : "east";
            } else {
                node.layoutSector = "south";
            }
        }
    }

    private static void sortBySubtype(List<TopologyNode> nodes) {
        java.util.Collections.sort(nodes, new java.util.Comparator<TopologyNode>() {

            @Override
            public int compare(TopologyNode a, TopologyNode b) {
                int order = Integer
                    .compare(TopologyRules.branchOrderIndex(a.subtype), TopologyRules.branchOrderIndex(b.subtype));
                if (order != 0) {
                    return order;
                }
                String nameA = a.displayName == null ? "" : a.displayName;
                String nameB = b.displayName == null ? "" : b.displayName;
                return nameA.compareToIgnoreCase(nameB);
            }
        });
    }

    private static boolean isVirtualCableNode(TopologyNode node) {
        return node.id != null
            && (node.id.startsWith("virtual:") || TopologyRules.isCableFacility(TopologyNodeType.fromId(node.type)));
    }
}
