package com.imgood.advancedatamonitor.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.advancedatamonitor.items.GrappleRouteEntry;
import com.imgood.advancedatamonitor.utils.BlockPos;

/**
 * Matches saved routes for path-mode sub-path extraction.
 */
public final class GrappleRouteMatcher {

    public static final class Match {

        public final String routeId;
        public final String routeName;
        public final boolean reversed;
        public final List<BlockPos> subPath;

        public Match(String routeId, String routeName, boolean reversed, List<BlockPos> subPath) {
            this.routeId = routeId;
            this.routeName = routeName;
            this.reversed = reversed;
            this.subPath = subPath;
        }
    }

    private GrappleRouteMatcher() {}

    public static List<Match> findMatches(EntityPlayerMP player, BlockPos start, BlockPos target, int dimension) {
        List<GrappleRouteEntry> routes = GrapplePathStore.instance()
            .getRoutesForPlayerDimension(player, dimension);
        return findMatchesInRoutes(routes, start, target);
    }

    public static List<Match> findMatchesInRoutes(List<GrappleRouteEntry> routes, BlockPos start, BlockPos target) {
        List<Match> matches = new ArrayList<Match>();
        if (routes == null || start == null || target == null) {
            return matches;
        }
        if (start.equals(target)) {
            return matches;
        }
        for (GrappleRouteEntry route : routes) {
            appendMatchesForRoute(route, start, target, matches);
        }
        return matches;
    }

    private static void appendMatchesForRoute(GrappleRouteEntry route, BlockPos start, BlockPos target,
        List<Match> matches) {
        int startIdx = indexOfNode(route.nodes, start);
        int targetIdx = indexOfNode(route.nodes, target);
        if (startIdx < 0 || targetIdx < 0) {
            return;
        }
        if (startIdx < targetIdx) {
            List<BlockPos> forward = new ArrayList<BlockPos>(route.nodes.subList(startIdx, targetIdx + 1));
            if (forward.size() >= 2) {
                matches.add(new Match(route.routeId, route.name, false, forward));
            }
        }
        if (startIdx > targetIdx) {
            List<BlockPos> reverse = new ArrayList<BlockPos>();
            for (int i = startIdx; i >= targetIdx; i--) {
                reverse.add(route.nodes.get(i));
            }
            if (reverse.size() >= 2) {
                matches.add(new Match(route.routeId, route.name, true, reverse));
            }
        }
        if (route.nodes.size() >= 3) {
            BlockPos first = route.nodes.get(0);
            BlockPos last = route.nodes.get(route.nodes.size() - 1);
            if (first.equals(last) && startIdx != targetIdx) {
                appendRingMatches(route, startIdx, targetIdx, matches);
            }
        }
    }

    private static void appendRingMatches(GrappleRouteEntry route, int startIdx, int targetIdx, List<Match> matches) {
        int size = route.nodes.size();
        int forwardSteps = (targetIdx - startIdx + size) % size;
        int backwardSteps = (startIdx - targetIdx + size) % size;
        if (forwardSteps > 0 && forwardSteps < size) {
            List<BlockPos> forward = collectRingPath(route.nodes, startIdx, forwardSteps);
            if (forward.size() >= 2 && !containsMatch(matches, route.routeId, false, forward)) {
                matches.add(new Match(route.routeId, route.name, false, forward));
            }
        }
        if (backwardSteps > 0 && backwardSteps < size) {
            List<BlockPos> reverse = collectRingPathBackward(route.nodes, startIdx, backwardSteps);
            if (reverse.size() >= 2 && !containsMatch(matches, route.routeId, true, reverse)) {
                matches.add(new Match(route.routeId, route.name, true, reverse));
            }
        }
    }

    private static List<BlockPos> collectRingPath(List<BlockPos> nodes, int startIdx, int steps) {
        List<BlockPos> path = new ArrayList<BlockPos>();
        int size = nodes.size();
        for (int i = 0; i <= steps; i++) {
            path.add(nodes.get((startIdx + i) % size));
        }
        return path;
    }

    private static List<BlockPos> collectRingPathBackward(List<BlockPos> nodes, int startIdx, int steps) {
        List<BlockPos> path = new ArrayList<BlockPos>();
        int size = nodes.size();
        for (int i = 0; i <= steps; i++) {
            int idx = startIdx - i;
            if (idx < 0) {
                idx += size;
            }
            path.add(nodes.get(idx));
        }
        return path;
    }

    private static boolean containsMatch(List<Match> matches, String routeId, boolean reversed,
        List<BlockPos> subPath) {
        for (Match match : matches) {
            if (routeId.equals(match.routeId) && reversed == match.reversed && match.subPath.equals(subPath)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfNode(List<BlockPos> nodes, BlockPos target) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i)
                .equals(target)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isOnAnyRoute(EntityPlayerMP player, BlockPos node, int dimension) {
        if (player == null || node == null) {
            return false;
        }
        List<GrappleRouteEntry> routes = GrapplePathStore.instance()
            .getRoutesForPlayerDimension(player, dimension);
        for (GrappleRouteEntry route : routes) {
            if (indexOfNode(route.nodes, node) >= 0) {
                return true;
            }
        }
        return false;
    }
}
