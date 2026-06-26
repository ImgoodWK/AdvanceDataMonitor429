package com.imgood.advancedatamonitor.items;

/**
 * Grapple hook operating mode / 挂索器工作模式
 */
public enum GrappleHookMode {

    /** Single-point queue — manual hop enqueue while sliding. */
    QUEUE(0),
    /** Path planning — auto-record visited nodes, save named routes. */
    PLANNING(1),
    /** Path mode — auto sub-path along saved routes. */
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
