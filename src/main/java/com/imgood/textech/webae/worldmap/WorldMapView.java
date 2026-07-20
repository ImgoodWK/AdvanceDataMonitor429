package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.Config;

/**
 * 
 * Supported world map tile perspectives: top-down flat + mineshot-style oblique orbit.
 * 
 */

public enum WorldMapView {

    FLAT("flat", "adm.webae.worldmap.view.flat", null),

    OBLIQUE_SE("oblique_se", "adm.webae.worldmap.view.oblique", WorldMapObliqueDirection.SE),

    OBLIQUE_SW("oblique_sw", "adm.webae.worldmap.view.oblique", WorldMapObliqueDirection.SW),

    OBLIQUE_NE("oblique_ne", "adm.webae.worldmap.view.oblique", WorldMapObliqueDirection.NE),

    OBLIQUE_NW("oblique_nw", "adm.webae.worldmap.view.oblique", WorldMapObliqueDirection.NW);

    public final String id;

    public final String labelKey;

    public final WorldMapObliqueDirection obliqueDirection;

    WorldMapView(String id, String labelKey, WorldMapObliqueDirection obliqueDirection) {

        this.id = id;

        this.labelKey = labelKey;

        this.obliqueDirection = obliqueDirection;

    }

    public boolean isOblique() {

        return obliqueDirection != null;

    }

    public static WorldMapView fromId(String id) {

        if (id == null || id.isEmpty()) {

            return FLAT;

        }

        String trimmed = id.trim()

            .toLowerCase();

        for (WorldMapView view : values()) {

            if (view.id.equals(trimmed)) {

                return view;

            }

        }

        // Legacy aliases

        if ("iso_se".equals(trimmed)) {

            return OBLIQUE_SE;

        }

        if ("north".equals(trimmed) || "south".equals(trimmed) || "east".equals(trimmed) || "west".equals(trimmed)) {

            return null;

        }

        if ("oblique".equals(trimmed)) {

            return OBLIQUE_SE;

        }

        return null;

    }

    public static WorldMapView obliqueForDirection(WorldMapObliqueDirection direction) {

        if (direction == null) {

            return OBLIQUE_SE;

        }

        switch (direction) {

            case SW:

                return OBLIQUE_SW;

            case NE:

                return OBLIQUE_NE;

            case NW:

                return OBLIQUE_NW;

            case SE:

            default:

                return OBLIQUE_SE;

        }

    }

    public static boolean isEnabled(WorldMapView view) {

        if (view == null) {

            return false;

        }

        for (WorldMapView enabled : enabledViews()) {

            if (enabled == view) {

                return true;

            }

        }

        return false;

    }

    public static List<WorldMapView> enabledViews() {

        List<WorldMapView> out = new ArrayList<WorldMapView>();

        String raw = Config.webWorldMapViewsEnabled;

        if (raw == null || raw.trim()

            .isEmpty()) {

            out.add(FLAT);

            out.add(OBLIQUE_SE);

            return out;

        }

        String[] parts = raw.split(",");

        for (String part : parts) {

            if (part == null) {

                continue;

            }

            String token = part.trim()

                .toLowerCase();

            if (token.isEmpty()) {

                continue;

            }

            if ("oblique".equals(token)) {

                if (Config.webWorldMapObliqueEnabled) {

                    addObliqueDirections(out);

                }

                continue;

            }

            WorldMapView view = fromId(token);

            if (view != null && view.isOblique() && !Config.webWorldMapObliqueEnabled) {

                continue;

            }

            if (view != null && !out.contains(view)) {

                out.add(view);

            }

        }

        if (out.isEmpty()) {

            out.add(FLAT);

        }

        return out;

    }

    private static void addObliqueDirections(List<WorldMapView> out) {

        for (WorldMapView view : values()) {

            if (view.isOblique() && !out.contains(view)) {

                out.add(view);

            }

        }

    }

    /** UI tabs: flat + generic oblique reference view. */

    public static List<WorldMapMetaDto.ViewInfo> uiViewInfos() {

        List<WorldMapMetaDto.ViewInfo> infos = new ArrayList<WorldMapMetaDto.ViewInfo>();

        if (isEnabled(FLAT)) {

            WorldMapMetaDto.ViewInfo flat = new WorldMapMetaDto.ViewInfo();

            flat.id = FLAT.id;

            flat.labelKey = FLAT.labelKey;

            infos.add(flat);

        }

        if (hasEnabledOblique()) {

            WorldMapMetaDto.ViewInfo oblique = new WorldMapMetaDto.ViewInfo();

            oblique.id = "oblique";

            oblique.labelKey = "adm.webae.worldmap.view.oblique";

            infos.add(oblique);

        }

        if (infos.isEmpty()) {

            WorldMapMetaDto.ViewInfo flat = new WorldMapMetaDto.ViewInfo();

            flat.id = FLAT.id;

            flat.labelKey = FLAT.labelKey;

            infos.add(flat);

        }

        return infos;

    }

    public static List<WorldMapMetaDto.ViewInfo> enabledObliqueDirectionInfos() {

        List<WorldMapMetaDto.ViewInfo> infos = new ArrayList<WorldMapMetaDto.ViewInfo>();

        if (!Config.webWorldMapObliqueEnabled) {

            return infos;

        }

        for (WorldMapView view : enabledViews()) {

            if (!view.isOblique() || view.obliqueDirection == null) {

                continue;

            }

            WorldMapObliqueDirection dir = view.obliqueDirection;

            boolean duplicate = false;

            for (WorldMapMetaDto.ViewInfo existing : infos) {

                if (dir.id.equals(existing.id)) {

                    duplicate = true;

                    break;

                }

            }

            if (duplicate) {

                continue;

            }

            WorldMapMetaDto.ViewInfo info = new WorldMapMetaDto.ViewInfo();

            info.id = dir.id;

            info.labelKey = dir.labelKey;

            infos.add(info);

        }

        if (infos.isEmpty()) {

            WorldMapMetaDto.ViewInfo info = new WorldMapMetaDto.ViewInfo();

            info.id = WorldMapObliqueDirection.SE.id;

            info.labelKey = WorldMapObliqueDirection.SE.labelKey;

            infos.add(info);

        }

        return infos;

    }

    private static boolean hasEnabledOblique() {

        if (!Config.webWorldMapObliqueEnabled) {

            return false;

        }

        for (WorldMapView view : enabledViews()) {

            if (view.isOblique()) {

                return true;

            }

        }

        return false;

    }

    /** @deprecated Use {@link #uiViewInfos()}. */

    public static List<WorldMapMetaDto.ViewInfo> enabledViewInfos() {

        return uiViewInfos();

    }

}
