package com.imgood.textech.webae.icon;

import java.util.ArrayList;
import java.util.List;

/**
 * WebAE icon export / serve render mode identifiers.
 *
 * <p>
 * Phase 0a: {@link #ATLAS}, {@link #HYBRID}, {@link #INVENTORY_GL}, {@link #INVENTORY_FLAT}.
 * Phase 0b: {@link #ENTITY}, {@link #BLOCK}, {@link #FIRST_PERSON}, {@link #NEI}.
 * </p>
 */
public enum IconRenderMode {

    ATLAS("atlas"),
    HYBRID("hybrid"),
    INVENTORY_GL("inventory_gl"),
    INVENTORY_FLAT("inventory_flat"),
    ENTITY("entity"),
    BLOCK("block"),
    FIRST_PERSON("first_person"),
    NEI("nei");

    private final String id;

    IconRenderMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** Lang key: {@code adm.iconRenderMode.<id>}. */
    public String getLabelKey() {
        return "adm.iconRenderMode." + id;
    }

    /** Lang key: {@code adm.iconRenderMode.<id>.tooltip}. */
    public String getTooltipKey() {
        return "adm.iconRenderMode." + id + ".tooltip";
    }

    public boolean isImplemented() {
        return this == NEI || this == INVENTORY_GL;
    }

    /** Deprecated atlas/hybrid modes are no longer exported. */
    public boolean isDeprecated() {
        return this == ATLAS || this == HYBRID
            || this == INVENTORY_FLAT
            || this == ENTITY
            || this == BLOCK
            || this == FIRST_PERSON;
    }

    /** Modes available for bulk export in accuracy-first builds. */
    public static List<IconRenderMode> exportModes() {
        List<IconRenderMode> out = new ArrayList<IconRenderMode>();
        out.add(NEI);
        out.add(INVENTORY_GL);
        return out;
    }

    public static IconRenderMode fromId(String raw) {
        if (raw == null || raw.isEmpty()) return NEI;
        if ("all".equalsIgnoreCase(raw)) return null;
        for (IconRenderMode m : values()) {
            if (m.id.equalsIgnoreCase(raw)) return m;
        }
        return null;
    }

    public static boolean isAllToken(String raw) {
        return raw != null && "all".equalsIgnoreCase(raw.trim());
    }

    public static boolean isValidModeId(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        if (isAllToken(raw)) return true;
        return fromId(raw) != null;
    }

    /** Modes with a client-side render strategy in the current build. */
    public static List<IconRenderMode> implementedModes() {
        List<IconRenderMode> out = new ArrayList<IconRenderMode>();
        for (IconRenderMode m : values()) {
            if (m.isImplemented()) out.add(m);
        }
        return out;
    }

    public static List<IconRenderMode> allModes() {
        List<IconRenderMode> out = new ArrayList<IconRenderMode>();
        for (IconRenderMode m : values()) {
            out.add(m);
        }
        return out;
    }
}
