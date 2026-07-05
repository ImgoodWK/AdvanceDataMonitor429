package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds star-topology fake cable edges with simulated channel loads.
 * Hub = controller group; branches radiate outward per {@link TopologyRules#branchOrder()}.
 */
public final class FakeChannelAllocator {

    private FakeChannelAllocator() {}

    public static List<TopologyEdge> allocateStar(List<TopologyNode> nodes) {
        List<TopologyEdge> edges = new ArrayList<TopologyEdge>();
        if (nodes == null || nodes.isEmpty()) {
            return edges;
        }

        TopologyNode hub = findHub(nodes);
        if (hub == null) {
            hub = nodes.get(0);
        }

        int totalSimulated = 0;
        for (TopologyNode node : nodes) {
            if (node == hub) {
                continue;
            }
            int branchLoad = node.channelCost * Math.max(1, node.count);
            totalSimulated += branchLoad;

            TopologyEdge edge = new TopologyEdge();
            edge.from = hub.id;
            edge.to = node.id;
            edge.channelsSimulated.used = branchLoad;
            edge.channelsSimulated.max = cableMaxForLoad(branchLoad);
            edge.channelsSimulated.available = true;
            edge.cableType = TopologyRules.cableTypeForLoad(branchLoad);
            edge.channelsReal.available = false;
            edges.add(edge);
        }

        return edges;
    }

    public static void applyNetworkChannelTotals(TopologySnapshot.Meta meta, List<TopologyEdge> edges,
        ChannelProbeResult probe) {
        int simulatedUsed = 0;
        int simulatedMax = 0;
        for (TopologyEdge edge : edges) {
            if (edge.channelsSimulated != null) {
                simulatedUsed += edge.channelsSimulated.used;
                simulatedMax += edge.channelsSimulated.max;
            }
        }
        if (meta.channelsSimulated == null) {
            meta.channelsSimulated = new TopologyEdge.ChannelInfo();
        }
        meta.channelsSimulated.used = simulatedUsed;
        meta.channelsSimulated.max = Math.max(simulatedMax, TopologyRules.CABLE_COVERED_MAX);
        meta.channelsSimulated.available = true;

        if (probe != null && probe.available) {
            if (meta.channelsReal == null) {
                meta.channelsReal = new TopologyEdge.ChannelInfo();
            }
            meta.channelsReal.used = probe.used;
            meta.channelsReal.max = probe.max;
            meta.channelsReal.available = true;
        } else if (meta.channelsReal == null) {
            meta.channelsReal = new TopologyEdge.ChannelInfo();
            meta.channelsReal.available = false;
        }
    }

    public static void assignLogicalLayout(List<TopologyNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        TopologyNode hub = findHub(nodes);
        if (hub != null) {
            hub.layoutX = 0;
            hub.layoutY = 0;
        }

        List<TopologyNode> branches = new ArrayList<TopologyNode>();
        Map<String, TopologyNode> byId = new HashMap<String, TopologyNode>();
        for (TopologyNode node : nodes) {
            byId.put(node.id, node);
            if (node != hub) {
                branches.add(node);
            }
        }

        branches = sortBranches(branches, byId);
        int n = branches.size();
        double radius = 1.0;
        for (int i = 0; i < n; i++) {
            double angle = (2.0 * Math.PI * i) / Math.max(1, n);
            branches.get(i).layoutX = Math.cos(angle) * radius;
            branches.get(i).layoutY = Math.sin(angle) * radius;
        }
    }

    public static void assignSpatialLayout(List<TopologyNode> nodes) {
        if (nodes == null) {
            return;
        }
        for (TopologyNode node : nodes) {
            if (node.binX != Integer.MIN_VALUE && node.binZ != Integer.MIN_VALUE) {
                node.layoutX = node.binX;
                node.layoutY = node.binZ;
            }
        }
    }

    private static TopologyNode findHub(List<TopologyNode> nodes) {
        for (TopologyNode node : nodes) {
            if (TopologyNodeType.CONTROLLER.id.equals(node.type)) {
                return node;
            }
        }
        for (TopologyNode node : nodes) {
            if ("hub".equals(node.role)) {
                return node;
            }
        }
        return null;
    }

    private static List<TopologyNode> sortBranches(List<TopologyNode> branches, Map<String, TopologyNode> byId) {
        final String[] order = TopologyRules.branchOrder();
        List<TopologyNode> sorted = new ArrayList<TopologyNode>(branches);
        java.util.Collections.sort(sorted, new java.util.Comparator<TopologyNode>() {

            @Override
            public int compare(TopologyNode a, TopologyNode b) {
                return Integer.compare(indexOf(order, a.type), indexOf(order, b.type));
            }

            private int indexOf(String[] arr, String type) {
                for (int i = 0; i < arr.length; i++) {
                    if (arr[i].equals(type)) {
                        return i;
                    }
                }
                return arr.length;
            }
        });
        return sorted;
    }

    private static int cableMaxForLoad(int load) {
        if (load <= TopologyRules.CABLE_SMART_MAX) {
            return TopologyRules.CABLE_SMART_MAX;
        }
        if (load <= TopologyRules.CABLE_COVERED_MAX) {
            return TopologyRules.CABLE_COVERED_MAX;
        }
        return load;
    }

    /** Result of optional AE channel API probe. */
    public static final class ChannelProbeResult {

        public int used;
        public int max;
        public boolean available;
    }
}
