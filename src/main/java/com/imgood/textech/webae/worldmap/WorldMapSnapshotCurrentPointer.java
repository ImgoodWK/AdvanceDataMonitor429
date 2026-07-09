package com.imgood.textech.webae.worldmap;

/**
 * Points to the active snapshot version for an owner network.
 */
public final class WorldMapSnapshotCurrentPointer {

    public int version;
    public long timestamp;
    public String source = "";
    public int tilePx;
}
