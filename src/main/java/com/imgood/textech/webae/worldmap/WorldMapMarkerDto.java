package com.imgood.textech.webae.worldmap;

/**
 * Flattened AE device marker for the world map overlay (one row per physical block).
 */
public final class WorldMapMarkerDto {

    /** Unique key {@code dim:x:y:z}. */
    public String id;
    /** Parent {@link com.imgood.textech.webae.topology.TopologyNode#id}. */
    public String nodeId;
    public String type;
    public String subtype = "";
    public String displayName;
    public String iconItemId;
    public int x;
    public int y;
    public int z;
    public int dim;
    public int channelCost;
}
