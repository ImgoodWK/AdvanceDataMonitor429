package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.topology.TopologyNode;
import com.imgood.textech.webae.topology.TopologySnapshot;

/**
 * Flattens a logical topology snapshot into per-block world map markers.
 * Crafting CPU and ME controller multiblocks emit a single anchor marker each.
 */
public final class WorldMapMarkerBuilder {

    private WorldMapMarkerBuilder() {}

    public static List<WorldMapMarkerDto> fromLogicalSnapshot(TopologySnapshot snapshot) {
        List<WorldMapMarkerDto> out = new ArrayList<WorldMapMarkerDto>();
        if (snapshot == null || snapshot.nodes == null) {
            return out;
        }
        for (TopologyNode node : snapshot.nodes) {
            if (node == null || node.devices == null || node.devices.isEmpty()) {
                continue;
            }
            if (isCpuNode(node)) {
                TopologyNode.DeviceRecord anchor = pickCpuAnchor(node.devices);
                if (anchor != null) {
                    out.add(toMarker(node, anchor));
                }
                continue;
            }
            if (isControllerNode(node)) {
                TopologyNode.DeviceRecord anchor = pickControllerAnchor(node.devices);
                if (anchor != null) {
                    out.add(toMarker(node, anchor));
                }
                continue;
            }
            for (TopologyNode.DeviceRecord device : node.devices) {
                if (device == null) {
                    continue;
                }
                out.add(toMarker(node, device));
            }
        }
        return out;
    }

    public static String markerId(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }

    private static boolean isCpuNode(TopologyNode node) {
        if (node == null) {
            return false;
        }
        if ("cpu".equalsIgnoreCase(node.type)) {
            return true;
        }
        return node.subtype != null && "cpu".equalsIgnoreCase(node.subtype);
    }

    private static boolean isControllerNode(TopologyNode node) {
        if (node == null) {
            return false;
        }
        if ("controller".equalsIgnoreCase(node.type)) {
            return true;
        }
        return node.subtype != null && "controller".equalsIgnoreCase(node.subtype);
    }

    private static TopologyNode.DeviceRecord pickCpuAnchor(List<TopologyNode.DeviceRecord> devices) {
        TopologyNode.DeviceRecord best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (TopologyNode.DeviceRecord device : devices) {
            if (device == null) {
                continue;
            }
            int priority = cpuAnchorPriority(device);
            if (priority < bestPriority) {
                bestPriority = priority;
                best = device;
            }
        }
        return best;
    }

    /** Lowest Y, then smallest x/z for stable tie-break. */
    private static TopologyNode.DeviceRecord pickControllerAnchor(List<TopologyNode.DeviceRecord> devices) {
        TopologyNode.DeviceRecord best = null;
        for (TopologyNode.DeviceRecord device : devices) {
            if (device == null) {
                continue;
            }
            if (best == null || controllerAnchorCompare(device, best) < 0) {
                best = device;
            }
        }
        return best;
    }

    private static int controllerAnchorCompare(TopologyNode.DeviceRecord a, TopologyNode.DeviceRecord b) {
        if (a.y != b.y) {
            return Integer.compare(a.y, b.y);
        }
        if (a.x != b.x) {
            return Integer.compare(a.x, b.x);
        }
        return Integer.compare(a.z, b.z);
    }

    /** Lower = higher priority: monitor, coprocessor/accelerator, storage, other. */
    private static int cpuAnchorPriority(TopologyNode.DeviceRecord device) {
        String hay = deviceHaystack(device);
        if (containsIgnoreCase(hay, "monitor")) {
            return 0;
        }
        if (containsIgnoreCase(hay, "coprocessor") || containsIgnoreCase(hay, "accelerator")) {
            return 1;
        }
        if (containsIgnoreCase(hay, "storage")) {
            return 2;
        }
        return 3;
    }

    private static String deviceHaystack(TopologyNode.DeviceRecord device) {
        StringBuilder sb = new StringBuilder();
        if (device.displayName != null) {
            sb.append(device.displayName)
                .append(' ');
        }
        if (device.className != null) {
            sb.append(device.className)
                .append(' ');
        }
        if (device.iconItemId != null) {
            sb.append(device.iconItemId);
        }
        return sb.toString();
    }

    private static boolean containsIgnoreCase(String hay, String needle) {
        return hay != null && needle != null
            && hay.toLowerCase()
                .contains(needle.toLowerCase());
    }

    private static WorldMapMarkerDto toMarker(TopologyNode node, TopologyNode.DeviceRecord device) {
        WorldMapMarkerDto marker = new WorldMapMarkerDto();
        marker.id = markerId(device.dim, device.x, device.y, device.z);
        marker.nodeId = node.id != null ? node.id : "";
        marker.type = node.type != null ? node.type : "misc";
        marker.subtype = node.subtype != null ? node.subtype : "";
        marker.displayName = pickDisplayName(device, node);
        marker.iconItemId = pickIconId(device, node);
        marker.x = device.x;
        marker.y = device.y;
        marker.z = device.z;
        marker.dim = device.dim;
        marker.channelCost = device.channelCost;
        return marker;
    }

    private static String pickDisplayName(TopologyNode.DeviceRecord device, TopologyNode node) {
        if (device.displayName != null && !device.displayName.isEmpty()) {
            return device.displayName;
        }
        if (node.displayName != null && !node.displayName.isEmpty()) {
            return node.displayName;
        }
        return node.type != null ? node.type : "device";
    }

    private static String pickIconId(TopologyNode.DeviceRecord device, TopologyNode node) {
        if (device.iconItemId != null && !device.iconItemId.isEmpty()) {
            return device.iconItemId;
        }
        if (node.iconItemId != null && !node.iconItemId.isEmpty()) {
            return node.iconItemId;
        }
        return "";
    }
}
