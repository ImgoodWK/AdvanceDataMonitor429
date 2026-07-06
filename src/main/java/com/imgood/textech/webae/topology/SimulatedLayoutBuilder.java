package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.imgood.textech.webae.topology.TreeTopologyBuilder.BuildResult;

/**
 * Assigns grid coordinates for the simulated view after star topology build.
 * Places the controller at the center and device groups on a ring — no synthetic cable cells.
 */
public final class SimulatedLayoutBuilder {

    private static final int ORIGIN_X = 8;
    private static final int ORIGIN_Y = 8;
    private static final int RING_RADIUS = 5;

    private SimulatedLayoutBuilder() {}

    public static void apply(BuildResult tree) {
        if (tree == null || tree.nodes.isEmpty()) {
            return;
        }
        Map<String, TopologyNode> byId = new HashMap<String, TopologyNode>();
        for (TopologyNode node : tree.nodes) {
            byId.put(node.id, node);
        }

        TopologyNode root = byId.get("controller");
        if (root == null) {
            root = tree.nodes.get(0);
        }
        placeBlock(root, ORIGIN_X, ORIGIN_Y);

        List<TopologyNode> satellites = new ArrayList<TopologyNode>();
        for (TopologyNode node : tree.nodes) {
            if (node == root || TopologyNodeType.CELL.id.equals(node.type)) {
                continue;
            }
            satellites.add(node);
        }

        int count = satellites.size();
        if (count == 0) {
            rebuildEdgePaths(tree.edges, byId);
            return;
        }

        for (int i = 0; i < count; i++) {
            TopologyNode node = satellites.get(i);
            double angle = (2.0 * Math.PI * i) / count - Math.PI / 2.0;
            int x = ORIGIN_X + (int) Math.round(Math.cos(angle) * RING_RADIUS);
            int y = ORIGIN_Y + (int) Math.round(Math.sin(angle) * RING_RADIUS);
            placeBlock(node, x, y);
        }

        rebuildEdgePaths(tree.edges, byId);
    }

    private static void placeBlock(TopologyNode node, int x, int y) {
        node.simGridX = x;
        node.simGridY = y;
        node.simKind = "block";
    }

    private static void rebuildEdgePaths(List<TopologyEdge> edges, Map<String, TopologyNode> byId) {
        for (TopologyEdge edge : edges) {
            TopologyNode from = byId.get(edge.from);
            TopologyNode to = byId.get(edge.to);
            if (from == null || to == null) {
                continue;
            }
            edge.pathPoints.clear();
            addPathPoints(edge.pathPoints, from.simGridX, from.simGridY, to.simGridX, to.simGridY);
        }
    }

    private static void addPathPoints(List<TopologyEdge.PathPoint> out, double x1, double y1, double x2, double y2) {
        TopologyEdge.PathPoint start = new TopologyEdge.PathPoint();
        start.x = x1;
        start.y = y1;
        out.add(start);
        TopologyEdge.PathPoint end = new TopologyEdge.PathPoint();
        end.x = x2;
        end.y = y2;
        out.add(end);
    }
}
