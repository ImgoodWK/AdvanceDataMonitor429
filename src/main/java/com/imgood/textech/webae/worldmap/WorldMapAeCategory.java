package com.imgood.textech.webae.worldmap;

/**
 * Heuristic AE device categories for world-map overlay ID tiles (R channel = {@link #id}).
 */
public enum WorldMapAeCategory {

    CONTROLLER(1),
    DRIVE(2),
    CELL(3),
    INTERFACE(4),
    TERMINAL(5),
    CABLE(6),
    ENERGY(7),
    P2P(8),
    BUS(9),
    OTHER(10),
    CPU(11);

    public final int id;

    WorldMapAeCategory(int id) {
        this.id = id;
    }

    public static WorldMapAeCategory fromId(int id) {
        for (WorldMapAeCategory cat : values()) {
            if (cat.id == id) {
                return cat;
            }
        }
        return OTHER;
    }

    public static WorldMapAeCategory resolve(WorldMapAePlacementRecord placement) {
        if (placement == null) {
            return OTHER;
        }
        if ("cable".equals(placement.kind)) {
            return CABLE;
        }
        if ("part".equals(placement.kind)) {
            return BUS;
        }
        String icon = placement.iconItemId != null ? placement.iconItemId.toLowerCase() : "";
        String cls = placement.className != null ? placement.className.toLowerCase() : "";
        String name = placement.displayName != null ? placement.displayName.toLowerCase() : "";
        String hay = icon + " " + cls + " " + name;

        if (containsAny(hay, "controller")) {
            return CONTROLLER;
        }
        if (containsAny(hay, "me_drive", "iodrive", "drive")) {
            return DRIVE;
        }
        if (containsAny(
            hay,
            "craftingcpu",
            "craftingtile",
            "blockcrafting",
            "crafting co-processor",
            "crafting storage",
            "crafting monitor",
            "crafting unit",
            "coprocessor",
            "accelerator")) {
            return CPU;
        }
        if (containsAny(hay, "cell", "storage", "chest")) {
            return CELL;
        }
        if (containsAny(hay, "interface")) {
            return INTERFACE;
        }
        if (containsAny(hay, "terminal", "monitor", "pattern")) {
            return TERMINAL;
        }
        if (containsAny(hay, "p2p", "quantum")) {
            return P2P;
        }
        if (containsAny(hay, "energy", "vibration", "charger", "crank")) {
            return ENERGY;
        }
        if (containsAny(hay, "cable", "glass", "covered", "smart", "dense")) {
            return CABLE;
        }
        if (containsAny(hay, "bus", "facade", "part")) {
            return BUS;
        }
        return OTHER;
    }

    private static boolean containsAny(String hay, String... needles) {
        if (hay == null || hay.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && hay.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Pack category id into 24-bit RGB (R=id, G=B=0). */
    public static int idRgb(int categoryId) {
        int cid = categoryId & 0xFF;
        if (cid <= 0) {
            return 0;
        }
        return (cid << 16);
    }

    public static int argbForCategory(int categoryId, int alpha) {
        int a = alpha & 0xFF;
        if (a <= 0) {
            return 0;
        }
        int rgb = idRgb(categoryId);
        if (rgb == 0) {
            return 0;
        }
        return (a << 24) | rgb;
    }
}
