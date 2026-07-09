package com.imgood.textech.webae.worldmap;

/**
 * Response for snapshot status / manifest API.
 */
public final class WorldMapSnapshotStatusDto {

    public boolean success = true;
    public int networkId;
    public int currentVersion;
    public long timestamp;
    public String source = "";
    public int tilePx;
    public String captureState = "idle";
    public String requestId = "";
    public String acceptPlayerName = "";
    public int totalChunks;
    public int completedChunks;
    public int missingChunks;
    public long expiresAtMs;
    public String message = "";
    public WorldMapSnapshotManifest manifest;
}
