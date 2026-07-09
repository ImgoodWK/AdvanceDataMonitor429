package com.imgood.textech.webae.worldmap;

/**
 * Points to the active snapshot version for an owner network.
 */
public final class WorldMapSnapshotCurrentPointer {

    public int version;
    /** Previous finalized version kept for tile fallback during refresh (0 = none). */
    public int previousVersion;
    public long timestamp;
    public String source = "";
    public int tilePx;
}
