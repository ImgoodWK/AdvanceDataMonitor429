package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.imgood.textech.webae.topology.ChannelBranchAllocator.Allocation;
import com.imgood.textech.webae.topology.ChannelBranchAllocator.Result;
import com.imgood.textech.webae.topology.FakeChannelAllocator.ChannelProbeResult;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility.CellSlot;
import com.imgood.textech.webae.topology.TopologyFacilityGrouper.AggregatedGroup;
import com.imgood.textech.webae.topology.TopologySnapshot.LaneInfo;

/**
 * Builds logical AE channel-budget topology (ae_budget_v2):
 * controller → dense trunk (32) → 4× smart lanes (8) → role pods → devices.
 * Zero-channel devices hang on a hub orbit. Not real AE cable routing.
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
        public int[] laneUsed = new int[ChannelBranchAllocator.BRANCH_COUNT];
        public boolean[] laneOverflow = new boolean[ChannelBranchAllocator.BRANCH_COUNT];
        public Map<String, Integer> orbitCounts = new HashMap<String, Integer>();
        public List<LaneInfo> laneInfos = new ArrayList<LaneInfo>();
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
        List<AggregatedGroup> channelGroups = new ArrayList<AggregatedGroup>();
        List<AggregatedGroup> zeroGroups = new ArrayList<AggregatedGroup>();

        for (AggregatedGroup group : allGroups) {
            if (group.channelCostSum <= 0 || TopologyRules.isZeroChannelSubtype(group.subtype)) {
                zeroGroups.add(group);
            } else {
                // Access (terminals) and all other channel devices share the 4×8 lane budget.
                channelGroups.add(group);
            }
        }

        appendEnergyGroups(result, root.id, energyFacilities);

        TopologyNode denseTrunk = createVirtualCableNode(
            DENSE_TRUNK_ID,
            TopologyNodeType.CABLE_DENSE,
            "Dense Trunk (32ch)",
            TopologyRules.LAYER_TRUNK,
            1,
            0,
            "south");
        result.nodes.add(denseTrunk);

        TopologyNode[] branches = new TopologyNode[ChannelBranchAllocator.BRANCH_COUNT];
        for (int i = 0; i < ChannelBranchAllocator.BRANCH_COUNT; i++) {
            branches[i] = createVirtualCableNode(
                branchId(i),
                TopologyNodeType.CABLE_SMART,
                "Smart Lane " + i + " (8ch)",
                TopologyRules.LAYER_LANE,
                2,
                i,
                "branch" + i);
            branches[i].branchIndex = i;
            result.nodes.add(branches[i]);
        }

        Result allocation = ChannelBranchAllocator.allocate(channelGroups);
        System.arraycopy(allocation.branchUsed, 0, result.laneUsed, 0, ChannelBranchAllocator.BRANCH_COUNT);
        System.arraycopy(allocation.branchOverflow, 0, result.laneOverflow, 0, ChannelBranchAllocator.BRANCH_COUNT);

        int trunkUsed = 0;
        for (int i = 0; i < ChannelBranchAllocator.BRANCH_COUNT; i++) {
            trunkUsed += allocation.branchUsed[i];
        }
        boolean trunkOverflow = trunkUsed > TopologyRules.CABLE_COVERED_MAX;

        result.edges.add(
            edge(
                root.id,
                denseTrunk.id,
                trunkUsed,
                TopologyRules.CABLE_COVERED_MAX,
                "dense",
                -1,
                false,
                trunkOverflow,
                TopologyRules.EDGE_CAPACITY_TRUNK));

        for (int i = 0; i < ChannelBranchAllocator.BRANCH_COUNT; i++) {
            result.edges.add(
                edge(
                    denseTrunk.id,
                    branches[i].id,
                    allocation.branchUsed[i],
                    ChannelBranchAllocator.CHANNELS_PER_BRANCH,
                    "smart",
                    i,
                    allocation.branchGroupCount[i] == 0,
                    allocation.branchOverflow[i],
                    TopologyRules.EDGE_CAPACITY_LANE));
        }

        // Group allocated devices by (lane, podKind) → compound pods.
        Map<String, List<AggregatedGroup>> podBuckets = new LinkedHashMap<String, List<AggregatedGroup>>();
        Map<String, Integer> podLane = new HashMap<String, Integer>();
        Map<String, String> podKindByKey = new HashMap<String, String>();

        for (AggregatedGroup group : channelGroups) {
            Allocation alloc = allocation.byGroupKey.get(group.groupKey);
            int branchIndex = alloc != null ? alloc.branchIndex : 0;
            String podKind = TopologyRules.podKindForSubtype(group.subtype);
            String podKey = branchIndex + "|" + podKind;
            List<AggregatedGroup> bucket = podBuckets.get(podKey);
            if (bucket == null) {
                bucket = new ArrayList<AggregatedGroup>();
                podBuckets.put(podKey, bucket);
                podLane.put(podKey, Integer.valueOf(branchIndex));
                podKindByKey.put(podKey, podKind);
            }
            bucket.add(group);
        }

        Map<Integer, List<String>> lanePodKinds = new HashMap<Integer, List<String>>();
        for (int i = 0; i < ChannelBranchAllocator.BRANCH_COUNT; i++) {
            lanePodKinds.put(Integer.valueOf(i), new ArrayList<String>());
        }

        int podSlot = 0;
        for (Map.Entry<String, List<AggregatedGroup>> entry : podBuckets.entrySet()) {
            String podKey = entry.getKey();
            int branchIndex = podLane.get(podKey)
                .intValue();
            String podKind = podKindByKey.get(podKey);
            List<AggregatedGroup> members = entry.getValue();

            String podId = "pod:" + branchIndex + ":" + podKind;
            TopologyNode pod = createPodNode(podId, podKind, branchIndex, podSlot++);
            result.nodes.add(pod);
            result.edges.add(
                edge(
                    branches[branchIndex].id,
                    pod.id,
                    sumChannelCost(members),
                    ChannelBranchAllocator.CHANNELS_PER_BRANCH,
                    "smart",
                    branchIndex,
                    false,
                    allocation.branchOverflow[branchIndex],
                    TopologyRules.EDGE_POD_UPLINK));

            List<String> kinds = lanePodKinds.get(Integer.valueOf(branchIndex));
            if (kinds != null && !kinds.contains(podKind)) {
                kinds.add(podKind);
            }

            int deviceSlot = 0;
            for (AggregatedGroup group : members) {
                Allocation alloc = allocation.byGroupKey.get(group.groupKey);
                int slotIndex = alloc != null ? alloc.slotIndex : deviceSlot;
                TopologyNode node = createAggregatedNode(group, branchIndex, slotIndex, podKind, podId);
                result.nodes.add(node);
                result.edges.add(
                    edge(
                        pod.id,
                        node.id,
                        group.channelCostSum,
                        ChannelBranchAllocator.CHANNELS_PER_BRANCH,
                        "smart",
                        branchIndex,
                        false,
                        alloc != null && alloc.overflow,
                        TopologyRules.EDGE_DEVICE_LINK));
                deviceSlot++;
            }
        }

        for (int i = 0; i < ChannelBranchAllocator.BRANCH_COUNT; i++) {
            LaneInfo info = new LaneInfo();
            info.index = i;
            info.used = allocation.branchUsed[i];
            info.max = ChannelBranchAllocator.CHANNELS_PER_BRANCH;
            info.overflow = allocation.branchOverflow[i];
            info.primaryPodKinds = lanePodKinds.get(Integer.valueOf(i));
            result.laneInfos.add(info);
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
        meta.channelModel = TopologyRules.CHANNEL_MODEL_V2;
        meta.lanes = tree.laneInfos;
        meta.orbitCounts = tree.orbitCounts;

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

    private static int sumChannelCost(List<AggregatedGroup> groups) {
        int sum = 0;
        for (AggregatedGroup group : groups) {
            if (group != null) {
                sum += group.channelCostSum;
            }
        }
        return sum;
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
            TopologyNode node = createAggregatedNode(group, -1, slot, TopologyRules.POD_POWER0, "");
            node.layoutSector = sector;
            node.role = "orbit";
            node.layer = TopologyRules.LAYER_ORBIT;
            result.nodes.add(node);
            bumpOrbit(result, TopologyRules.POD_POWER0, group.count);
            result.edges.add(edge(rootId, node.id, 0, 0, "smart", -1, false, false, TopologyRules.EDGE_ORBIT_LINK));
        }
    }

    private static void appendZeroChannelGroups(BuildResult result, String rootId, List<AggregatedGroup> zeroGroups) {
        int index = 0;
        for (AggregatedGroup group : zeroGroups) {
            String podKind = TopologyRules.podKindForSubtype(group.subtype);
            TopologyNode node = createAggregatedNode(group, -1, index++, podKind, "");
            node.layoutSector = "south";
            node.role = "orbit";
            node.layer = TopologyRules.LAYER_ORBIT;
            result.nodes.add(node);
            bumpOrbit(result, podKind, group.count);
            result.edges.add(edge(rootId, node.id, 0, 0, "smart", -1, false, false, TopologyRules.EDGE_ORBIT_LINK));
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
            node.role = "orbit";
            node.layer = TopologyRules.LAYER_ORBIT;
            node.podKind = TopologyRules.POD_CRAFT0;
            result.nodes.add(node);
            bumpOrbit(result, TopologyRules.POD_CRAFT0, cluster.unitCount);
            result.edges.add(edge(rootId, node.id, 0, 0, "smart", -1, false, false, TopologyRules.EDGE_ORBIT_LINK));
        }
    }

    private static void bumpOrbit(BuildResult result, String podKind, int count) {
        Integer prev = result.orbitCounts.get(podKind);
        result.orbitCounts.put(podKind, Integer.valueOf((prev == null ? 0 : prev.intValue()) + count));
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
        node.role = "orbit";
        node.layer = TopologyRules.LAYER_ORBIT;
        node.podKind = TopologyRules.POD_CRAFT0;
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
            record.iconItemId = unit.iconItemId != null && !unit.iconItemId.isEmpty() ? unit.iconItemId
                : TopologyRules.iconItemIdForCraftingComponent(unit.storage, unit.accelerator, unit.monitor);
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
        node.layer = TopologyRules.LAYER_HUB;
        node.layoutSector = "center";
        for (NetworkFacility controller : controllers) {
            node.devices.add(toRecord(controller));
        }
        return node;
    }

    private static TopologyNode createPodNode(String id, String podKind, int branchIndex, int slotIndex) {
        TopologyNode node = new TopologyNode();
        node.id = id;
        node.type = "pod";
        node.subtype = podKind;
        node.displayName = TopologyRules.displayNameForPodKind(podKind);
        node.count = 1;
        node.channelCost = 0;
        node.role = "pod";
        node.layer = TopologyRules.LAYER_POD;
        node.podKind = podKind;
        node.branchIndex = branchIndex;
        node.layoutX = 3;
        node.layoutY = slotIndex;
        node.layoutSector = "branch" + branchIndex;
        return node;
    }

    private static TopologyNode createVirtualCableNode(String id, TopologyNodeType type, String name, String layer,
        int depth, int sibling, String sector) {
        TopologyNode node = new TopologyNode();
        node.id = id;
        node.type = type.id;
        node.subtype = type.id;
        node.displayName = name;
        node.count = 1;
        node.channelCost = 0;
        node.iconItemId = TopologyRules.iconItemIdFor(type);
        node.role = TopologyRules.LAYER_TRUNK.equals(layer) ? "trunk" : "lane";
        node.layer = layer;
        node.layoutX = depth;
        node.layoutY = sibling;
        node.layoutSector = sector;
        node.simKind = "junction";
        return node;
    }

    private static TopologyNode createAggregatedNode(AggregatedGroup group, int branchIndex, int slotIndex,
        String podKind, String parentId) {
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
        node.layer = TopologyRules.LAYER_DEVICE;
        node.podKind = podKind == null ? TopologyRules.podKindForSubtype(group.subtype) : podKind;
        node.parentId = parentId == null ? "" : parentId;
        node.branchIndex = branchIndex;
        node.layoutX = branchIndex >= 0 ? 4 : 1;
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
        boolean emptyBranch, boolean overflow, String kind) {
        TopologyEdge edge = new TopologyEdge();
        edge.from = from;
        edge.to = to;
        edge.cableType = cableType;
        edge.branchIndex = branchIndex;
        edge.emptyBranch = emptyBranch;
        edge.overflow = overflow;
        edge.kind = kind == null ? "" : kind;
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
            if (TopologyRules.LAYER_TRUNK.equals(node.layer)) {
                node.layoutX = 1;
                node.layoutY = 0;
            } else if (TopologyRules.LAYER_LANE.equals(node.layer)) {
                node.layoutX = 2;
                node.layoutY = node.branchIndex >= 0 ? node.branchIndex : node.layoutY;
            } else if (TopologyRules.LAYER_POD.equals(node.layer)) {
                node.layoutX = 3;
            } else if (TopologyRules.LAYER_DEVICE.equals(node.layer) && node.branchIndex >= 0) {
                node.layoutX = 4;
            } else if ("west".equals(node.layoutSector)) {
                node.layoutX = 0;
                node.layoutY = -1 - Math.abs(node.layoutY);
            } else if ("east".equals(node.layoutSector)) {
                node.layoutX = 0;
                node.layoutY = Math.abs(node.layoutY) + 1;
            } else if (TopologyRules.LAYER_ORBIT.equals(node.layer)) {
                node.layoutX = 0.5;
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
            if (isVirtualCableNode(node) || TopologyRules.LAYER_POD.equals(node.layer)) {
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
            node.layoutSector = "south";
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
