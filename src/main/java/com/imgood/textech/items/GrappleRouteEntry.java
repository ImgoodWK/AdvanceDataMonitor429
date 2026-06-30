package com.imgood.textech.items;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.utils.BlockPos;

/**
 * Saved grapple route DTO / 已保存挂索路�?
 */
public class GrappleRouteEntry {

    public String routeId = "";
    public String ownerUuid = "";
    public String name = "";
    public int dimension;
    public long createdAt;
    public final List<BlockPos> nodes = new ArrayList<BlockPos>();

    public GrappleRouteEntry copy() {
        GrappleRouteEntry copy = new GrappleRouteEntry();
        copy.routeId = routeId;
        copy.ownerUuid = ownerUuid;
        copy.name = name;
        copy.dimension = dimension;
        copy.createdAt = createdAt;
        copy.nodes.addAll(nodes);
        return copy;
    }

    public int getNodeCount() {
        return nodes.size();
    }
}
