package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.imgood.textech.webae.topology.LogicalTopologyBuilder.BuildResult;

/**
 * Assigns grid coordinates for the simulated view with Manhattan cable routing.
 * Generates intermediate cable graph nodes between devices following tree/star semantics.
 */
public final class SimulatedLayoutBuilder {

    private static final int ORIGIN_X = 12;
    private static final int ORIGIN_Y = 12;
    private static final int BRANCH_SPACING = 4;
    private static final int RING_INNER = 5;
    private static final int RING_OUTER = 8;

    private SimulatedLayoutBuilder() {}

    public static void apply(BuildResult tree) {
        apply(tree, TopologyRules.LAYOUT_TREE);
    }

    public static void apply(BuildResult tree, String layoutMode) {
        if (tree == null || tree.nodes.isEmpty()) {
            return;
        }
        boolean star = TopologyRules.LAYOUT_STAR.equals(layoutMode);
        if (star) {
            LogicalTopologyBuilder.assignStarLayout(tree.nodes, LogicalTopologyBuilder.CONTROLLER_ID);
            applyStarGrid(tree);
        } else {
            applyTreeGrid(tree);
        }
        rebuildEdgePaths(tree);
    }

    private static void applyTreeGrid(BuildResult tree) {
        Map<String, TopologyNode> byId = indexNodes(tree.nodes);
        TopologyNode root = byId.get(LogicalTopologyBuilder.CONTROLLER_ID);
        if (root == null && !tree.nodes.isEmpty()) {
            root = tree.nodes.get(0);
        }
        if (root != null) {
            placeBlock(root, ORIGIN_X, ORIGIN_Y);
        }

        List<TopologyNode> north = new ArrayList<TopologyNode>();
        List<TopologyNode> southZero = new ArrayList<TopologyNode>();
        List<TopologyNode> branches = new ArrayList<TopologyNode>();
        List<TopologyNode> branchDevices = new ArrayList<TopologyNode>();
        List<TopologyNode> emptyBranches = new ArrayList<TopologyNode>();
        TopologyNode dense = byId.get(LogicalTopologyBuilder.DENSE_TRUNK_ID);

        for (TopologyNode node : tree.nodes) {
            if (node == root || node == dense || isVirtualBranch(node)) {
                if (isVirtualBranch(node)) {
                    branches.add(node);
                }
                continue;
            }
            if ("empty_branch".equals(node.role)) {
                emptyBranches.add(node);
                continue;
            }
            if ("north".equals(node.layoutSector)) {
                north.add(node);
            } else if ("south".equals(node.layoutSector) && node.branchIndex < 0) {
                southZero.add(node);
            } else if (node.branchIndex >= 0) {
                branchDevices.add(node);
            } else if ("west".equals(node.layoutSector) || "east".equals(node.layoutSector)) {
                int offset = "west".equals(node.layoutSector) ? -3 : 3;
                placeBlock(node, ORIGIN_X + offset, ORIGIN_Y);
            }
        }

        int northY = ORIGIN_Y - 4;
        for (int i = 0; i < north.size(); i++) {
            placeBlock(north.get(i), ORIGIN_X + (i - north.size() / 2) * 2, northY);
        }

        if (dense != null) {
            placeBlock(dense, ORIGIN_X, ORIGIN_Y + 2);
        }

        for (int i = 0; i < branches.size(); i++) {
            TopologyNode branch = branches.get(i);
            int bx = ORIGIN_X + (i - branches.size() / 2) * BRANCH_SPACING;
            placeBlock(branch, bx, ORIGIN_Y + 4);
        }

        Map<Integer, List<TopologyNode>> byBranch = new HashMap<Integer, List<TopologyNode>>();
        for (TopologyNode node : branchDevices) {
            List<TopologyNode> list = byBranch.get(node.branchIndex);
            if (list == null) {
                list = new ArrayList<TopologyNode>();
                byBranch.put(node.branchIndex, list);
            }
            list.add(node);
        }
        for (Map.Entry<Integer, List<TopologyNode>> entry : byBranch.entrySet()) {
            int branchIndex = entry.getKey();
            List<TopologyNode> devices = entry.getValue();
            int bx = ORIGIN_X + (branchIndex - 1) * BRANCH_SPACING;
            for (int i = 0; i < devices.size(); i++) {
                placeBlock(devices.get(i), bx, ORIGIN_Y + 6 + i * 2);
            }
        }

        for (TopologyNode empty : emptyBranches) {
            int bx = ORIGIN_X + (empty.branchIndex - 1) * BRANCH_SPACING;
            placeBlock(empty, bx, ORIGIN_Y + 6);
        }

        int southY = ORIGIN_Y + 6;
        for (int i = 0; i < southZero.size(); i++) {
            placeBlock(southZero.get(i), ORIGIN_X + (i + 1) * 2, southY);
        }
    }

    private static void applyStarGrid(BuildResult tree) {
        Map<String, TopologyNode> byId = indexNodes(tree.nodes);
        TopologyNode root = byId.get(LogicalTopologyBuilder.CONTROLLER_ID);
        if (root != null) {
            placeBlock(root, ORIGIN_X, ORIGIN_Y);
        }

        List<TopologyNode> inner = new ArrayList<TopologyNode>();
        List<TopologyNode> outer = new ArrayList<TopologyNode>();
        for (TopologyNode node : tree.nodes) {
            if (node == root || isVirtualCable(node) || "empty_branch".equals(node.role)) {
                continue;
            }
            if (node.layoutX >= 2) {
                outer.add(node);
            } else if (node.layoutX >= 1) {
                inner.add(node);
            }
        }

        placeRing(inner, ORIGIN_X, ORIGIN_Y, RING_INNER);
        placeRing(outer, ORIGIN_X, ORIGIN_Y, RING_OUTER);
    }

    private static void placeRing(List<TopologyNode> nodes, int cx, int cy, int radius) {
        int count = nodes.size();
        for (int i = 0; i < count; i++) {
            TopologyNode node = nodes.get(i);
            double angle = count > 0 ? (2.0 * Math.PI * i) / count - Math.PI / 2.0 : 0;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            placeBlock(node, x, y);
        }
    }

    private static void rebuildEdgePaths(BuildResult tree) {
        Map<String, TopologyNode> byId = indexNodes(tree.nodes);
        Set<String> occupied = new HashSet<String>();
        for (TopologyNode node : tree.nodes) {
            if (node.simGridX != 0 || node.simGridY != 0 || node.simKind != null) {
                occupied.add(gridKey(node.simGridX, node.simGridY));
            }
        }

        List<TopologyNode> cableNodes = new ArrayList<TopologyNode>();
        int cableSeq = 0;

        for (TopologyEdge edge : tree.edges) {
            TopologyNode from = byId.get(edge.from);
            TopologyNode to = byId.get(edge.to);
            if (from == null || to == null) {
                continue;
            }
            edge.pathPoints.clear();
            if (edge.emptyBranch) {
                routeManhattan(edge.pathPoints, from, to, occupied, edge.cableType);
                continue;
            }
            cableSeq = routeManhattanWithCableNodes(
                edge.pathPoints,
                from,
                to,
                occupied,
                edge.cableType,
                edge.from,
                edge.to,
                cableNodes,
                cableSeq);
        }

        tree.nodes.addAll(cableNodes);
    }

    private static int routeManhattanWithCableNodes(List<TopologyEdge.PathPoint> out, TopologyNode from,
        TopologyNode to, Set<String> occupied, String cableType, String edgeFrom, String edgeTo,
        List<TopologyNode> cableNodes, int cableSeq) {
        int x1 = (int) from.simGridX;
        int y1 = (int) from.simGridY;
        int x2 = (int) to.simGridX;
        int y2 = (int) to.simGridY;

        List<int[]> path = new ArrayList<int[]>();
        path.add(new int[] { x1, y1 });
        if (x1 != x2) {
            path.add(new int[] { x2, y1 });
        }
        if (y1 != y2) {
            path.add(new int[] { x2, y2 });
        }

        for (int[] pt : path) {
            TopologyEdge.PathPoint point = new TopologyEdge.PathPoint();
            point.x = pt[0];
            point.y = pt[1];
            out.add(point);
        }

        for (int i = 0; i < path.size() - 1; i++) {
            int ax = path.get(i)[0];
            int ay = path.get(i)[1];
            int bx = path.get(i + 1)[0];
            int by = path.get(i + 1)[1];
            if (ax == bx) {
                int step = ay < by ? 1 : -1;
                for (int y = ay + step; step > 0 ? y < by : y > by; y += step) {
                    cableSeq = placeCableCell(
                        ax,
                        y,
                        cableType,
                        edgeFrom,
                        edgeTo,
                        cableNodes,
                        occupied,
                        cableSeq,
                        false,
                        null);
                }
            } else {
                int step = ax < bx ? 1 : -1;
                for (int x = ax + step; step > 0 ? x < bx : x > bx; x += step) {
                    cableSeq = placeCableCell(
                        x,
                        ay,
                        cableType,
                        edgeFrom,
                        edgeTo,
                        cableNodes,
                        occupied,
                        cableSeq,
                        false,
                        null);
                }
            }
        }

        for (int i = 1; i < path.size() - 1; i++) {
            int[] prev = path.get(i - 1);
            int[] cur = path.get(i);
            int[] next = path.get(i + 1);
            boolean prevH = prev[1] == cur[1];
            boolean nextH = cur[1] == next[1];
            if (prevH != nextH) {
                String corner = cornerKind(prev[0], prev[1], cur[0], cur[1], next[0], next[1]);
                cableSeq = placeCableCell(
                    cur[0],
                    cur[1],
                    cableType,
                    edgeFrom,
                    edgeTo,
                    cableNodes,
                    occupied,
                    cableSeq,
                    true,
                    corner);
            }
        }

        return cableSeq;
    }

    private static String cornerKind(int px, int py, int cx, int cy, int nx, int ny) {
        if (px < cx && cy < ny) {
            return "br";
        }
        if (px < cx && cy > ny) {
            return "tr";
        }
        if (px > cx && cy < ny) {
            return "bl";
        }
        return "tl";
    }

    private static int placeCableCell(int x, int y, String cableType, String edgeFrom, String edgeTo,
        List<TopologyNode> cableNodes, Set<String> occupied, int cableSeq, boolean corner, String cornerKind) {
        String key = gridKey(x, y);
        if (occupied.contains(key)) {
            return cableSeq;
        }
        occupied.add(key);

        TopologyNodeType nodeType = "dense".equals(cableType) ? TopologyNodeType.CABLE_DENSE
            : TopologyNodeType.CABLE_SMART;
        TopologyNode node = new TopologyNode();
        node.id = "cable:" + edgeFrom + ":" + edgeTo + ":" + x + "," + y;
        node.type = nodeType.id;
        node.subtype = nodeType.id;
        node.displayName = corner ? "Cable corner" : "Cable";
        node.count = 1;
        node.channelCost = 0;
        node.iconItemId = TopologyRules.iconItemIdFor(nodeType);
        node.simGridX = x;
        node.simGridY = y;
        if (corner && cornerKind != null) {
            node.simKind = "cable_corner_" + cornerKind;
        } else {
            node.simKind = "dense".equals(cableType) ? "cable_dense" : "cable_smart";
        }
        cableNodes.add(node);
        return cableSeq + 1;
    }

    private static void routeManhattan(List<TopologyEdge.PathPoint> out, TopologyNode from, TopologyNode to,
        Set<String> occupied, String cableType) {
        int x1 = (int) from.simGridX;
        int y1 = (int) from.simGridY;
        int x2 = (int) to.simGridX;
        int y2 = (int) to.simGridY;

        List<int[]> path = new ArrayList<int[]>();
        path.add(new int[] { x1, y1 });
        if (x1 != x2) {
            path.add(new int[] { x2, y1 });
        }
        if (y1 != y2) {
            path.add(new int[] { x2, y2 });
        }

        for (int[] pt : path) {
            TopologyEdge.PathPoint point = new TopologyEdge.PathPoint();
            point.x = pt[0];
            point.y = pt[1];
            out.add(point);
        }

        for (int i = 0; i < path.size() - 1; i++) {
            int ax = path.get(i)[0];
            int ay = path.get(i)[1];
            int bx = path.get(i + 1)[0];
            int by = path.get(i + 1)[1];
            if (ax == bx) {
                int step = ay < by ? 1 : -1;
                for (int y = ay; step > 0 ? y <= by : y >= by; y += step) {
                    String key = gridKey(ax, y);
                    if (!occupied.contains(key)) {
                        occupied.add(key);
                    }
                }
            } else {
                int step = ax < bx ? 1 : -1;
                for (int x = ax; step > 0 ? x <= bx : x >= bx; x += step) {
                    String key = gridKey(x, ay);
                    if (!occupied.contains(key)) {
                        occupied.add(key);
                    }
                }
            }
        }
    }

    private static Map<String, TopologyNode> indexNodes(List<TopologyNode> nodes) {
        Map<String, TopologyNode> byId = new HashMap<String, TopologyNode>();
        for (TopologyNode node : nodes) {
            byId.put(node.id, node);
        }
        return byId;
    }

    private static void placeBlock(TopologyNode node, int x, int y) {
        node.simGridX = x;
        node.simGridY = y;
        if (!"empty_branch".equals(node.role)) {
            node.simKind = "block";
        } else {
            node.simKind = "hidden";
        }
    }

    private static boolean isVirtualBranch(TopologyNode node) {
        return node.id != null && node.id.startsWith("virtual:branch:");
    }

    private static boolean isVirtualCable(TopologyNode node) {
        return node.id != null && node.id.startsWith("virtual:");
    }

    private static String gridKey(double x, double y) {
        return (int) x + "," + (int) y;
    }
}
