package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.topology.TopologySnapshot;

/**
 * Bounded logical sidecar for one world-map snapshot version.
 *
 * <p>The terrain capture packets intentionally carry no topology data.  The
 * capture coordinator takes a defensive copy of this object while it is
 * preparing the capture job and writes it next to the terrain manifest after
 * the client has uploaded its tiles.</p>
 */
public final class WorldMapLogicalIndex {

    /** Maximum number of logical topology markers retained in a sidecar. */
    public static final int MAX_MARKERS = 100_000;
    /** Maximum number of physical AE placements retained in a sidecar. */
    public static final int MAX_AE_PLACEMENTS = 200_000;
    /** Maximum UTF-16 code units for a single sidecar string field. */
    public static final int MAX_STRING_LENGTH = 512;
    /** Maximum absolute block coordinate accepted by sidecar validation. */
    public static final int MAX_BLOCK_COORDINATE = WorldMapPacketAuthorization.MAX_CHUNK_COORDINATE * 16 + 15;

    public int version;
    public long timestamp;
    public boolean logicalAvailable;
    public List<WorldMapMarkerDto> markers = new ArrayList<WorldMapMarkerDto>();
    public List<WorldMapAePlacementRecord> aePlacements = new ArrayList<WorldMapAePlacementRecord>();

    public WorldMapLogicalIndex() {}

    /** Builds the sidecar payload from the exact topology object used by capture setup. */
    public static WorldMapLogicalIndex fromSnapshot(TopologySnapshot snapshot, int version) {
        WorldMapLogicalIndex index = new WorldMapLogicalIndex();
        index.version = version;
        if (snapshot == null) {
            index.logicalAvailable = false;
            return index;
        }
        index.logicalAvailable = true;
        index.timestamp = snapshot.timestamp;
        List<WorldMapMarkerDto> markerList = WorldMapMarkerBuilder.fromLogicalSnapshot(snapshot);
        if (markerList != null) {
            int limit = Math.min(MAX_MARKERS, markerList.size());
            for (int i = 0; i < limit; i++) {
                WorldMapMarkerDto marker = markerList.get(i);
                if (marker != null) {
                    index.markers.add(copyMarker(marker));
                }
            }
        }
        List<WorldMapAePlacementRecord> placementList = WorldMapAePlacementSupport.placementsFromSnapshot(snapshot);
        if (placementList != null) {
            int limit = Math.min(MAX_AE_PLACEMENTS, placementList.size());
            for (int i = 0; i < limit; i++) {
                WorldMapAePlacementRecord placement = placementList.get(i);
                if (placement != null) {
                    index.aePlacements.add(copyPlacement(placement));
                }
            }
        }
        return index;
    }

    /** Returns a bounded deep copy suitable for retaining in a pending/job object. */
    public static WorldMapLogicalIndex copyOf(WorldMapLogicalIndex source) {
        WorldMapLogicalIndex copy = new WorldMapLogicalIndex();
        if (source == null) {
            return copy;
        }
        copy.version = source.version;
        copy.timestamp = source.timestamp;
        copy.logicalAvailable = source.logicalAvailable;
        if (!copy.logicalAvailable) {
            return copy;
        }
        if (source.markers != null) {
            int limit = Math.min(MAX_MARKERS, source.markers.size());
            for (int i = 0; i < limit; i++) {
                WorldMapMarkerDto marker = source.markers.get(i);
                if (marker != null) {
                    copy.markers.add(copyMarker(marker));
                }
            }
        }
        if (source.aePlacements != null) {
            int limit = Math.min(MAX_AE_PLACEMENTS, source.aePlacements.size());
            for (int i = 0; i < limit; i++) {
                WorldMapAePlacementRecord placement = source.aePlacements.get(i);
                if (placement != null) {
                    copy.aePlacements.add(copyPlacement(placement));
                }
            }
        }
        return copy;
    }

    /** Returns a copy with the capture version assigned. */
    public WorldMapLogicalIndex withVersion(int newVersion) {
        WorldMapLogicalIndex copy = copyOf(this);
        copy.version = newVersion;
        return copy;
    }

    private static WorldMapMarkerDto copyMarker(WorldMapMarkerDto source) {
        WorldMapMarkerDto copy = new WorldMapMarkerDto();
        copy.id = WorldMapMarkerBuilder.markerId(source.dim, source.x, source.y, source.z);
        copy.nodeId = bounded(source.nodeId);
        copy.type = bounded(source.type);
        copy.subtype = bounded(source.subtype);
        copy.displayName = bounded(source.displayName);
        copy.iconItemId = bounded(source.iconItemId);
        copy.x = source.x;
        copy.y = source.y;
        copy.z = source.z;
        copy.dim = source.dim;
        copy.channelCost = source.channelCost;
        return copy;
    }

    private static String bounded(String value) {
        if (value == null || value.length() <= MAX_STRING_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_STRING_LENGTH);
    }

    private static WorldMapAePlacementRecord copyPlacement(WorldMapAePlacementRecord source) {
        WorldMapAePlacementRecord copy = new WorldMapAePlacementRecord();
        copy.x = source.x;
        copy.y = source.y;
        copy.z = source.z;
        copy.dim = source.dim;
        copy.kind = bounded(source.kind);
        copy.className = bounded(source.className);
        copy.iconItemId = bounded(source.iconItemId);
        copy.displayName = bounded(source.displayName);
        return copy;
    }
}
