package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.topology.TopologyNode;
import com.imgood.textech.webae.topology.TopologySnapshot;
import com.imgood.textech.webae.topology.TopologySnapshotStore;

/**
 * Loads AE placements from persisted topology snapshots with device-record fallback.
 */
public final class WorldMapAePlacementSupport {

    private WorldMapAePlacementSupport() {}

    public static List<WorldMapAePlacementRecord> loadForNetwork(String ownerUuid, int networkId) {
        TopologySnapshot logical = TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, "logical");
        return placementsFromSnapshot(logical);
    }

    public static List<WorldMapAePlacementRecord> placementsFromSnapshot(TopologySnapshot snapshot) {
        if (snapshot == null) {
            return new ArrayList<WorldMapAePlacementRecord>();
        }
        if (snapshot.aePlacements != null && !snapshot.aePlacements.isEmpty()) {
            return snapshot.aePlacements;
        }
        return fallbackFromDevices(snapshot);
    }

    public static List<WorldMapAePlacementRecord> filterChunk(List<WorldMapAePlacementRecord> placements, int dim,
        int chunkX, int chunkZ) {
        List<WorldMapAePlacementRecord> out = new ArrayList<WorldMapAePlacementRecord>();
        if (placements == null || placements.isEmpty()) {
            return out;
        }
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        for (WorldMapAePlacementRecord placement : placements) {
            if (placement == null || placement.dim != dim) {
                continue;
            }
            if (placement.x >= minX && placement.x <= maxX && placement.z >= minZ && placement.z <= maxZ) {
                out.add(placement);
            }
        }
        return out;
    }

    private static List<WorldMapAePlacementRecord> fallbackFromDevices(TopologySnapshot snapshot) {
        List<WorldMapAePlacementRecord> out = new ArrayList<WorldMapAePlacementRecord>();
        if (snapshot.nodes == null) {
            return out;
        }
        for (TopologyNode node : snapshot.nodes) {
            if (node == null || node.devices == null) {
                continue;
            }
            for (TopologyNode.DeviceRecord device : node.devices) {
                if (device == null) {
                    continue;
                }
                WorldMapAePlacementRecord record = new WorldMapAePlacementRecord();
                record.x = device.x;
                record.y = device.y;
                record.z = device.z;
                record.dim = device.dim;
                record.kind = "block";
                record.className = device.className != null ? device.className : "";
                record.iconItemId = device.iconItemId != null ? device.iconItemId : "";
                record.displayName = device.displayName != null ? device.displayName : "";
                out.add(record);
            }
        }
        return out;
    }
}
