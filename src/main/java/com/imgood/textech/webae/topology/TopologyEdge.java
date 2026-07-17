package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.List;

/**
 * A simulated AE-style cable edge between topology nodes (not a real AE connection).
 */
public class TopologyEdge {

    public String from;
    public String to;
    public String cableType;
    /**
     * Edge kind for ae_budget_v2: capacity_trunk | capacity_lane | pod_uplink | device_link | orbit_link.
     */
    public String kind = "";
    /** Smart branch index (0-3) for edge coloring in abstract views. */
    public int branchIndex = -1;
    /** True when the edge represents an empty smart branch slot. */
    public boolean emptyBranch;
    /** True when planned channel use on this capacity edge exceeds max. */
    public boolean overflow;
    public ChannelInfo channelsSimulated = new ChannelInfo();
    public ChannelInfo channelsReal = new ChannelInfo();
    /** Optional orthogonal path points for simulated rendering. */
    public List<PathPoint> pathPoints = new ArrayList<PathPoint>();

    public static class PathPoint {

        public double x;
        public double y;
    }

    public static class ChannelInfo {

        public int used;
        public int max;
        public boolean available;
    }
}
