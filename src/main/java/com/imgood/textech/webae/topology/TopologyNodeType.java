package com.imgood.textech.webae.topology;

/**
 * Logical device categories for AE network topology grouping.
 * Aggregated by class, not split by dimension.
 */
public enum TopologyNodeType {

    CONTROLLER("controller", "hub"),
    /** Energy cell / acceptor adjacent to controller hub. */
    ENERGY("energy", "hub"),
    DRIVE("drive", "branch"),
    TERMINAL("terminal", "branch"),
    BUS("bus", "branch"),
    MONITOR("monitor", "branch"),
    INTERFACE("interface", "branch"),
    CPU("cpu", "branch"),
    P2P("p2p", "branch"),
    QUANTUM("quantum", "branch"),
    /** Storage cell inside a drive / ME chest slot. */
    CELL("cell", "leaf"),
    /** Planned smart cable segment (≤8 channels). */
    CABLE_SMART("cable_smart", "branch"),
    /** Planned dense/covered cable segment (≤32 channels, up to 4 smart branches). */
    CABLE_DENSE("cable_dense", "branch"),
    MISC("misc", "branch"),
    /** Spatial view: one node per dimension + 64×64 chunk bin. */
    SPATIAL_BIN("spatial_bin", "branch");

    public final String id;
    public final String role;

    TopologyNodeType(String id, String role) {
        this.id = id;
        this.role = role;
    }

    public static TopologyNodeType fromId(String id) {
        if (id == null) {
            return MISC;
        }
        for (TopologyNodeType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return MISC;
    }
}
