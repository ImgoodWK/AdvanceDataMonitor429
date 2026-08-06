package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.List;

/** Gson-friendly result of a pure current/previous snapshot comparison. */
public final class WorldMapSnapshotDiffDto {

    public boolean success;
    /** ok, unknown, or error. */
    public String status = "unknown";
    /** Explicit machine-readable outcome (no_versions/no_previous/same/invalid/not_retained). */
    public String code = "unknown";
    public int fromVersion;
    public int toVersion;
    public long fromTimestamp;
    public long toTimestamp;
    public Summary summary = new Summary();
    public List<MarkerChange> markerChanges = new ArrayList<MarkerChange>();
    public List<TileChange> tileChanges = new ArrayList<TileChange>();
    /** True only when both compared versions have a valid logical sidecar. */
    public boolean logicalAvailable;
    /** True when the 1000-entry combined detail budget removed rows. */
    public boolean truncated;

    public static final class Summary {

        public int markersAdded;
        public int markersRemoved;
        public int markersChanged;
        public int markersMoved;
        public int tilesAdded;
        public int tilesRemoved;
        public int tilesChanged;
        public int tilesUnchanged;
        public int markerTotal;
        public int tileTotal;
        public int total;
    }

    public static final class MarkerChange {

        public String id = "";
        /** added | removed | changed | moved */
        public String status = "";
        /** marker | placement; placements expose kind/className fields below. */
        public String source = "marker";
        public int fromDim;
        public int fromX;
        public int fromY;
        public int fromZ;
        public int toDim;
        public int toX;
        public int toY;
        public int toZ;
        public String fromKind = "";
        public String toKind = "";
        public String fromClassName = "";
        public String toClassName = "";
        public String fromIconItemId = "";
        public String toIconItemId = "";
        public WorldMapMarkerDto from;
        public WorldMapMarkerDto to;
        public WorldMapAePlacementRecord fromPlacement;
        public WorldMapAePlacementRecord toPlacement;
    }

    public static final class TileChange {

        public String key = "";
        /** added | removed | changed | unchanged */
        public String status = "";
        public String layer = "";
        public int dim;
        public int chunkX;
        public int chunkZ;
        public String fromSha256 = "";
        public String toSha256 = "";
        public long fromSize;
        public long toSize;
    }
}
