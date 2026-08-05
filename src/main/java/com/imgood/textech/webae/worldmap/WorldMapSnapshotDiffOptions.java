package com.imgood.textech.webae.worldmap;

/** Optional filters and detail switches for a snapshot diff request. */
public final class WorldMapSnapshotDiffOptions {

    public Integer dimension;
    public Integer minX;
    public Integer maxX;
    public Integer minZ;
    public Integer maxZ;
    public boolean includeTiles = true;
    public boolean includeMarkers = true;

    public WorldMapSnapshotDiffOptions() {}

    public WorldMapSnapshotDiffOptions copy() {
        WorldMapSnapshotDiffOptions copy = new WorldMapSnapshotDiffOptions();
        copy.dimension = dimension;
        copy.minX = minX;
        copy.maxX = maxX;
        copy.minZ = minZ;
        copy.maxZ = maxZ;
        copy.includeTiles = includeTiles;
        copy.includeMarkers = includeMarkers;
        return copy;
    }
}
