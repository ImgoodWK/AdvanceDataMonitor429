package com.imgood.textech.webae.worldmap;

/**
 * One AE grid node position for world-map overlay tiles (devices, cables, parts).
 */
public final class WorldMapAePlacementRecord {

    public int x;
    public int y;
    public int z;
    public int dim;
    /** {@code block} | {@code cable} | {@code part} */
    public String kind = "block";
    public String className = "";
    public String iconItemId = "";
    public String displayName = "";

    public WorldMapAePlacementRecord() {}

    public static WorldMapAePlacementRecord copyOf(WorldMapAePlacementRecord other) {
        WorldMapAePlacementRecord copy = new WorldMapAePlacementRecord();
        if (other == null) {
            return copy;
        }
        copy.x = other.x;
        copy.y = other.y;
        copy.z = other.z;
        copy.dim = other.dim;
        copy.kind = other.kind;
        copy.className = other.className;
        copy.iconItemId = other.iconItemId;
        copy.displayName = other.displayName;
        return copy;
    }
}
