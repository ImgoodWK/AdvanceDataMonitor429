package com.imgood.textech.webae.icon;

/**
 * Controls which items are included in an icon export session.
 */
public enum IconExportScope {

    /** All NEI/registry items (legacy full export). */
    ALL("all"),
    /** Items appearing in cached AE storage snapshots only. */
    SNAPSHOT("snapshot"),
    /** Explicit item id list supplied by server (lazy-load / render command). */
    LIST("list");

    private final String id;

    IconExportScope(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static IconExportScope fromId(String raw) {
        if (raw == null || raw.isEmpty()) return ALL;
        for (IconExportScope s : values()) {
            if (s.id.equalsIgnoreCase(raw)) return s;
        }
        return ALL;
    }
}
