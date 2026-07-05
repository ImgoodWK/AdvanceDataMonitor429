package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.imgood.textech.webae.topology.FakeChannelAllocator.ChannelProbeResult;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility;
import com.imgood.textech.webae.topology.NetworkStatusEnumerator.NetworkFacility.CellSlot;

/**
 * Builds a left-to-right tree pseudo-topology:
 * controller → (dense cable → smart cable → channel devices) | drives → cells | zero-cost devices.
 */
public final class TreeTopologyBuilder {

    private TreeTopologyBuilder() {}

    public static final class BuildResult {

        public List<TopologyNode> nodes = new ArrayList<TopologyNode>();
        public List<TopologyEdge> edges = new ArrayList<TopologyEdge>();
        public int channelDeviceCount;
        public int controllerCapacity;
    }

    public static BuildResult build(List<NetworkFacility> facilities, ChannelProbeResult probe) {
        BuildResult result = new BuildResult();
        if (facilities == null || facilities.isEmpty()) {
            return result;
        }

        List<NetworkFacility> controllers = filterType(facilities, TopologyNodeType.CONTROLLER);
        List<NetworkFacility> drives = filterType(facilities, TopologyNodeType.DRIVE);
        List<NetworkFacility> channelDevices = new ArrayList<NetworkFacility>();
        List<NetworkFacility> zeroCost = new ArrayList<NetworkFacility>();

        for (NetworkFacility facility : facilities) {
            if (facility.type == TopologyNodeType.CONTROLLER) {
                continue;
            }
            if (facility.type == TopologyNodeType.DRIVE) {
                continue;
            }
            if (facility.channelCost > 0) {
                channelDevices.add(facility);
            } else {
                zeroCost.add(facility);
            }
        }

        TopologyNode root = createControllerNode(controllers);
        result.nodes.add(root);
        result.controllerCapacity = estimateControllerCapacity(controllers, probe);

        int driveIndex = 0;
        for (NetworkFacility drive : drives) {
            TopologyNode driveNode = createDriveNode(drive, driveIndex++);
            result.nodes.add(driveNode);
            result.edges.add(edge(root.id, driveNode.id, 0, TopologyRules.CABLE_SMART_MAX, "smart"));

            int cellIndex = 0;
            if (drive.cells.isEmpty()) {
                TopologyNode empty = createCellNode(drive, null, cellIndex++);
                result.nodes.add(empty);
                result.edges.add(edge(driveNode.id, empty.id, 0, 0, "smart"));
            } else {
                for (CellSlot cell : drive.cells) {
                    TopologyNode cellNode = createCellNode(drive, cell, cellIndex++);
                    result.nodes.add(cellNode);
                    result.edges.add(edge(driveNode.id, cellNode.id, 0, 0, "smart"));
                }
            }
        }

        result.channelDeviceCount = channelDevices.size();
        appendChannelTree(result, root.id, channelDevices);

        int zeroIndex = 0;
        for (NetworkFacility facility : zeroCost) {
            TopologyNode node = createLeafDeviceNode(facility, "zero:" + zeroIndex++);
            result.nodes.add(node);
            result.edges.add(edge(root.id, node.id, 0, 0, "smart"));
        }

        assignTreeLayout(result.nodes, result.edges, root.id);
        return result;
    }

    public static void applyChannelTotals(TopologySnapshot.Meta meta, BuildResult tree, ChannelProbeResult probe) {
        if (meta.channelsSimulated == null) {
            meta.channelsSimulated = new TopologyEdge.ChannelInfo();
        }
        meta.channelsSimulated.used = tree.channelDeviceCount;
        meta.channelsSimulated.max = Math.max(tree.controllerCapacity, TopologyRules.CABLE_SMART_MAX);
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

    private static void appendChannelTree(BuildResult result, String rootId, List<NetworkFacility> channelDevices) {
        if (channelDevices.isEmpty()) {
            return;
        }
        List<List<NetworkFacility>> smartGroups = chunk(channelDevices, TopologyRules.CABLE_SMART_MAX);
        List<List<List<NetworkFacility>>> denseGroups = chunk(smartGroups, 4);

        int denseIndex = 0;
        for (List<List<NetworkFacility>> denseGroup : denseGroups) {
            int denseLoad = 0;
            for (List<NetworkFacility> smartGroup : denseGroup) {
                denseLoad += smartGroup.size();
            }
            int denseMax = Math.max(TopologyRules.CABLE_COVERED_MAX, denseLoad);
            String denseId = "cable-dense:" + denseIndex;
            TopologyNode denseNode = cableNode(
                denseId,
                TopologyNodeType.CABLE_DENSE,
                "Dense cable (" + denseLoad + "/" + denseMax + ")",
                denseLoad,
                denseMax);
            result.nodes.add(denseNode);
            result.edges.add(edge(rootId, denseId, denseLoad, denseMax, "dense"));

            int smartIndex = 0;
            for (List<NetworkFacility> smartGroup : denseGroup) {
                int smartLoad = smartGroup.size();
                String smartId = denseId + ":smart:" + smartIndex++;
                TopologyNode smartNode = cableNode(
                    smartId,
                    TopologyNodeType.CABLE_SMART,
                    "Smart cable (" + smartLoad + "/" + TopologyRules.CABLE_SMART_MAX + ")",
                    smartLoad,
                    TopologyRules.CABLE_SMART_MAX);
                result.nodes.add(smartNode);
                result.edges.add(edge(denseId, smartId, smartLoad, TopologyRules.CABLE_SMART_MAX, "smart"));

                int leafIndex = 0;
                for (NetworkFacility facility : smartGroup) {
                    TopologyNode leaf = createLeafDeviceNode(facility, smartId + ":dev:" + leafIndex++);
                    result.nodes.add(leaf);
                    result.edges.add(edge(smartId, leaf.id, facility.channelCost, TopologyRules.CABLE_SMART_MAX, "smart"));
                }
            }
            denseIndex++;
        }
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

    private static TopologyNode createDriveNode(NetworkFacility drive, int index) {
        TopologyNode node = new TopologyNode();
        node.id = "drive:" + drive.dim + ":" + drive.x + ":" + drive.y + ":" + drive.z + ":" + index;
        node.type = TopologyNodeType.DRIVE.id;
        node.displayName = drive.displayName + " @ " + drive.x + "," + drive.y + "," + drive.z;
        node.count = 1;
        node.channelCost = 0;
        node.iconItemId = drive.representationItemId.isEmpty()
            ? TopologyRules.iconItemIdFor(TopologyNodeType.DRIVE)
            : drive.representationItemId;
        node.role = "branch";
        node.devices.add(toRecord(drive));
        return node;
    }

    private static TopologyNode createCellNode(NetworkFacility drive, CellSlot cell, int index) {
        TopologyNode node = new TopologyNode();
        node.id = "cell:" + drive.x + ":" + drive.y + ":" + drive.z + ":" + index;
        node.type = TopologyNodeType.CELL.id;
        node.role = "leaf";
        node.channelCost = 0;
        node.count = 1;
        node.iconItemId = TopologyRules.iconItemIdFor(TopologyNodeType.CELL);
        if (cell == null || cell.empty) {
            node.displayName = "Empty cell slot";
        } else {
            node.displayName = cell.displayName;
            node.iconItemId = cell.itemId.isEmpty() ? node.iconItemId : cell.itemId;
        }
        TopologyNode.DeviceRecord record = new TopologyNode.DeviceRecord();
        record.className = drive.className;
        record.displayName = node.displayName;
        record.x = drive.x;
        record.y = drive.y;
        record.z = drive.z;
        record.dim = drive.dim;
        record.channelCost = 0;
        node.devices.add(record);
        return node;
    }

    private static TopologyNode createLeafDeviceNode(NetworkFacility facility, String id) {
        TopologyNode node = new TopologyNode();
        node.id = id;
        node.type = facility.type.id;
        node.displayName = facility.displayName;
        node.count = 1;
        node.channelCost = facility.channelCost;
        node.iconItemId = facility.representationItemId.isEmpty()
            ? TopologyRules.iconItemIdFor(facility.type)
            : facility.representationItemId;
        node.role = facility.channelCost > 0 ? "leaf" : "branch";
        node.devices.add(toRecord(facility));
        return node;
    }

    private static TopologyNode cableNode(String id, TopologyNodeType type, String label, int used, int max) {
        TopologyNode node = new TopologyNode();
        node.id = id;
        node.type = type.id;
        node.displayName = label;
        node.count = 1;
        node.channelCost = 0;
        node.role = "branch";
        node.iconItemId = TopologyRules.iconItemIdFor(type);
        TopologyNode.DeviceRecord record = new TopologyNode.DeviceRecord();
        record.displayName = label;
        record.channelCost = used;
        node.devices.add(record);
        return node;
    }

    private static TopologyNode.DeviceRecord toRecord(NetworkFacility facility) {
        TopologyNode.DeviceRecord record = new TopologyNode.DeviceRecord();
        record.className = facility.className;
        record.displayName = facility.displayName;
        record.x = facility.x;
        record.y = facility.y;
        record.z = facility.z;
        record.dim = facility.dim;
        record.channelCost = facility.channelCost;
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

    private static <T> List<List<T>> chunk(List<T> source, int size) {
        List<List<T>> groups = new ArrayList<List<T>>();
        if (source.isEmpty()) {
            return groups;
        }
        for (int i = 0; i < source.size(); i += size) {
            int end = Math.min(i + size, source.size());
            groups.add(new ArrayList<T>(source.subList(i, end)));
        }
        return groups;
    }

    /** Assign layoutX = depth, layoutY = sibling index within depth (left tree). */
    private static void assignTreeLayout(List<TopologyNode> nodes, List<TopologyEdge> edges, String rootId) {
        Map<String, List<String>> children = new HashMap<String, List<String>>();
        for (TopologyEdge edge : edges) {
            List<String> list = children.get(edge.from);
            if (list == null) {
                list = new ArrayList<String>();
                children.put(edge.from, list);
            }
            list.add(edge.to);
        }
        Map<String, TopologyNode> byId = new HashMap<String, TopologyNode>();
        for (TopologyNode node : nodes) {
            byId.put(node.id, node);
        }
        assignRecursive(rootId, 0, 0, children, byId, new int[] { 0 });
    }

    private static double assignRecursive(String nodeId, int depth, double desiredY, Map<String, List<String>> children,
        Map<String, TopologyNode> byId, int[] nextLeafY) {
        TopologyNode node = byId.get(nodeId);
        if (node == null) {
            return desiredY;
        }
        List<String> childIds = children.get(nodeId);
        if (childIds == null || childIds.isEmpty()) {
            double y = nextLeafY[0]++;
            node.layoutX = depth;
            node.layoutY = y;
            return y;
        }

        double firstY = -1;
        double lastY = 0;
        for (String childId : childIds) {
            double childY = assignRecursive(childId, depth + 1, nextLeafY[0], children, byId, nextLeafY);
            if (firstY < 0) {
                firstY = childY;
            }
            lastY = childY;
        }
        node.layoutX = depth;
        node.layoutY = (firstY + lastY) / 2.0;
        return node.layoutY;
    }
}
