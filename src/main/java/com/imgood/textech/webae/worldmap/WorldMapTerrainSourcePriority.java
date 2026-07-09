package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.imgood.textech.Config;

/**
 * Parses and validates {@link Config#worldMapSnapshotSourcePriority} (comma-separated source ids).
 */
public final class WorldMapTerrainSourcePriority {

    private static final String DEFAULT = "dynmap,journeymap,client_gl";

    private WorldMapTerrainSourcePriority() {}

    public static List<WorldMapTerrainSourceId> resolved() {
        return parse(Config.worldMapSnapshotSourcePriority);
    }

    public static List<String> resolvedIds() {
        List<WorldMapTerrainSourceId> ids = resolved();
        List<String> out = new ArrayList<String>();
        for (WorldMapTerrainSourceId id : ids) {
            out.add(id.id);
        }
        return out;
    }

    public static List<WorldMapTerrainSourceId> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            raw = DEFAULT;
        }
        String[] parts = raw.split(",");
        List<WorldMapTerrainSourceId> out = new ArrayList<WorldMapTerrainSourceId>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            WorldMapTerrainSourceId id = WorldMapTerrainSourceId.fromId(part);
            if (id == null) {
                continue;
            }
            if (!isEnabled(id)) {
                continue;
            }
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        if (out.isEmpty()) {
            out.add(WorldMapTerrainSourceId.CLIENT_GL);
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean isEnabled(WorldMapTerrainSourceId id) {
        if (id == WorldMapTerrainSourceId.DYNMAP) {
            return Config.worldMapDynmapCaptureEnabled;
        }
        if (id == WorldMapTerrainSourceId.JOURNEYMAP) {
            return Config.worldMapJourneyMapCaptureEnabled;
        }
        if (id == WorldMapTerrainSourceId.CLIENT_GL) {
            return Config.worldMapClientGlCaptureEnabled;
        }
        return true;
    }

    public static String summarizeSourceStats(java.util.Map<String, Integer> stats) {
        if (stats == null || stats.isEmpty()) {
            return WorldMapTerrainSourceId.CLIENT_GL.id;
        }
        int distinct = 0;
        String sole = WorldMapTerrainSourceId.CLIENT_GL.id;
        for (java.util.Map.Entry<String, Integer> entry : stats.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                distinct++;
                sole = entry.getKey();
            }
        }
        if (distinct <= 1) {
            return sole != null ? sole : WorldMapTerrainSourceId.CLIENT_GL.id;
        }
        return "mixed";
    }
}
