package com.imgood.textech.webae.worldmap;

/**
 * Terrain tile capture source identifiers for world map snapshots and SP direct serve.
 */
public enum WorldMapTerrainSourceId {

    DYNMAP("dynmap"),
    JOURNEYMAP("journeymap"),
    CLIENT_GL("client_gl");

    public final String id;

    WorldMapTerrainSourceId(String id) {
        this.id = id;
    }

    public static WorldMapTerrainSourceId fromId(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String id = raw.trim().toLowerCase();
        for (WorldMapTerrainSourceId source : values()) {
            if (source.id.equals(id)) {
                return source;
            }
        }
        return null;
    }
}
