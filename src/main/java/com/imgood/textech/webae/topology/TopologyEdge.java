package com.imgood.textech.webae.topology;

/**
 * A simulated AE-style cable edge between topology nodes (not a real AE connection).
 */
public class TopologyEdge {

    public String from;
    public String to;
    public String cableType;
    public ChannelInfo channelsSimulated = new ChannelInfo();
    public ChannelInfo channelsReal = new ChannelInfo();

    public static class ChannelInfo {

        public int used;
        public int max;
        public boolean available;
    }
}
