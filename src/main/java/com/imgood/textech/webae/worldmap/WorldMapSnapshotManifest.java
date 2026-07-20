package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-disk manifest for a network world map snapshot version.
 */
public final class WorldMapSnapshotManifest {

    public int version;
    public long timestamp;
    public String ownerUuid = "";
    public int networkId;
    /** {@code dynmap}, {@code journeymap}, {@code client_gl}, or {@code mixed}. */
    public String source = "client_gl";
    public int tilePx = 128;
    /** Per-source chunk counts from last capture finalize. */
    public Map<String, Integer> sourceStats = new HashMap<String, Integer>();
    public List<String> layers = new ArrayList<String>();
    public List<DimensionEntry> dimensions = new ArrayList<DimensionEntry>();
    /** tileKey -> metadata; key format {@code layer:dim:cx:cz}. */
    public Map<String, TileEntry> tiles = new HashMap<String, TileEntry>();
    public List<String> missingChunks = new ArrayList<String>();

    public static final class DimensionEntry {

        public int dim;
        public List<String> chunks = new ArrayList<String>();
    }

    public static final class TileEntry {

        public long size;
        public String sha256 = "";
        /** Optional per-tile capture source for debugging. */
        public String source = "";
    }

    public static String tileKey(String layer, int dim, int chunkX, int chunkZ) {
        return WorldMapTileLayer.normalize(layer) + ":" + dim + ":" + chunkX + ":" + chunkZ;
    }

    public static String chunkKey(int dim, int chunkX, int chunkZ) {
        return dim + ":" + chunkX + ":" + chunkZ;
    }
}
