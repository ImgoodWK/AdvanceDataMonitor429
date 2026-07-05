package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.List;

/**
 * A topology graph node — either a logical device-class aggregate or a spatial bin.
 */
public class TopologyNode {

    public String id;
    public String type;
    public String displayName;
    public int count;
    public int channelCost;
    public String iconItemId;
    public String role;
    public double layoutX;
    public double layoutY;
    public List<DeviceRecord> devices = new ArrayList<DeviceRecord>();

    /** Optional spatial metadata (spatial mode only). */
    public int dim = Integer.MIN_VALUE;
    public int binX = Integer.MIN_VALUE;
    public int binZ = Integer.MIN_VALUE;

    public static class DeviceRecord {

        public String className;
        public String displayName;
        public int x;
        public int y;
        public int z;
        public int dim;
        public int channelCost;
    }
}
