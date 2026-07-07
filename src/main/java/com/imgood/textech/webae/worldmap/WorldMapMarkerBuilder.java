package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.topology.TopologyNode;
import com.imgood.textech.webae.topology.TopologySnapshot;

/**
 * Flattens a logical topology snapshot into per-block world map markers.
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
            for (TopologyNode.DeviceRecord device : node.devices) {
                if (device == null) {
                    continue;
                }
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
                out.add(marker);
            }
        }
        return out;
    }

    public static String markerId(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
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
