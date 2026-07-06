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
