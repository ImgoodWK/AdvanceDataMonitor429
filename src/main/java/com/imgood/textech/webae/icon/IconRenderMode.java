package com.imgood.textech.webae.icon;

import java.util.ArrayList;
import java.util.List;

/**
 * WebAE icon export / serve render mode identifiers.
 *
 * <p>
 * Only {@link #NEI} is active (NESQL-style {@code GuiContainerManager.drawItem} FBO). All other
 * constants are retained for archival disk paths / strategy classes and must not be selected by
 * commands or upload/lazy/direct-capture call sites.
 * </p>
 */
public enum IconRenderMode {

    /** @deprecated archival only; not used by active export/serve */
    @Deprecated
    ATLAS("atlas"),
    /** @deprecated archival only; not used by active export/serve */
    @Deprecated
    HYBRID("hybrid"),
    /** @deprecated archival only; not used by active export/serve */
    @Deprecated
    INVENTORY_GL("inventory_gl"),
    /** @deprecated archival only; not used by active export/serve */
    @Deprecated
    INVENTORY_FLAT("inventory_flat"),
    /** @deprecated archival only; not used by active export/serve */
    @Deprecated
    ENTITY("entity"),
    /** @deprecated archival only; not used by active export/serve */
    @Deprecated
    BLOCK("block"),
    /** @deprecated archival only; not used by active export/serve */
    @Deprecated
    FIRST_PERSON("first_person"),
    /** Active NESQL-style mode (storage dir {@code nei/}). */
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

    /** Only {@link #NEI} is active. */
    public boolean isImplemented() {
        return this == NEI;
    }

    /** Non-{@link #NEI} modes are archival only. */
    public boolean isDeprecated() {
        return this != NEI;
    }

    /** Modes available for bulk export ({@link #NEI} only). */
    public static List<IconRenderMode> exportModes() {
        List<IconRenderMode> out = new ArrayList<IconRenderMode>();
        out.add(NEI);
        return out;
    }

    /**
     * Resolve a mode id. Unknown / empty → {@link #NEI}. Deprecated ids still parse for disk
     * lookup of legacy packs, but callers must not select them for new renders.
     */
    public static IconRenderMode fromId(String raw) {
        if (raw == null || raw.isEmpty()) return NEI;
        for (IconRenderMode m : values()) {
            if (m.id.equalsIgnoreCase(raw)) return m;
        }
        return null;
    }

    /** @deprecated {@code all} multi-mode export removed; only {@link #NEI} exists */
    @Deprecated
    public static boolean isAllToken(String raw) {
        return raw != null && "all".equalsIgnoreCase(raw.trim());
    }

    /** Valid for new export/upload: only {@code nei} (case-insensitive). */
    public static boolean isValidModeId(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        return NEI.getId()
            .equalsIgnoreCase(raw.trim());
    }

    /** Modes with a client-side render strategy in the current build. */
    public static List<IconRenderMode> implementedModes() {
        List<IconRenderMode> out = new ArrayList<IconRenderMode>();
        out.add(NEI);
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
