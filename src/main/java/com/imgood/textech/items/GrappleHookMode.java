package com.imgood.textech.items;

/**
 * Grapple hook operating mode / æŒ‚ç´¢å™¨å·¥ä½œæ¨¡å¼?
 */
public enum GrappleHookMode {

    /** Single-point queue â€?manual hop enqueue while sliding. */
    QUEUE(0),
    /** Path planning â€?auto-record visited nodes, save named routes. */
    PLANNING(1),
    /** Path mode â€?auto sub-path along saved routes. */
    PATH(2);

    private final int id;

    GrappleHookMode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static GrappleHookMode fromId(int id) {
        for (GrappleHookMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return QUEUE;
    }
}
