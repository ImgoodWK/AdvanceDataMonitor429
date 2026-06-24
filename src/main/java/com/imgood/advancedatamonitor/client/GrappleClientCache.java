package com.imgood.advancedatamonitor.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.imgood.advancedatamonitor.network.packet.PacketGrappleSync;
import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class GrappleClientCache {

    private static boolean attached;
    private static boolean traveling;
    private static int anchorX;
    private static int anchorY;
    private static int anchorZ;
    private static int travelTargetX;
    private static int travelTargetY;
    private static int travelTargetZ;
    private static float travelProgress;
    private static final List<BlockPos> nearbyNodes = new ArrayList<BlockPos>();
    private static final List<BlockPos> travelQueue = new ArrayList<BlockPos>();
    private static BlockPos selectedTarget;
    private static final Map<BlockPos, Float> iconScales = new HashMap<BlockPos, Float>();

    private GrappleClientCache() {}

    public static void apply(PacketGrappleSync packet) {
        boolean wasAttached = attached;
        attached = packet.attached;
        traveling = packet.traveling;
        anchorX = packet.anchorX;
        anchorY = packet.anchorY;
        anchorZ = packet.anchorZ;
        travelTargetX = packet.travelTargetX;
        travelTargetY = packet.travelTargetY;
        travelTargetZ = packet.travelTargetZ;
        travelProgress = packet.travelProgress;
        nearbyNodes.clear();
        if (packet.nodes != null) {
            nearbyNodes.addAll(packet.nodes);
        }
        travelQueue.clear();
        if (packet.travelQueue != null) {
            travelQueue.addAll(packet.travelQueue);
        }
        if (!attached || !wasAttached) {
            selectedTarget = null;
            iconScales.clear();
        }
    }

    public static boolean isAttached() {
        return attached;
    }

    public static boolean isTraveling() {
        return traveling;
    }

    public static int getAnchorX() {
        return anchorX;
    }

    public static int getAnchorY() {
        return anchorY;
    }

    public static int getAnchorZ() {
        return anchorZ;
    }

    public static int getTravelTargetX() {
        return travelTargetX;
    }

    public static int getTravelTargetY() {
        return travelTargetY;
    }

    public static int getTravelTargetZ() {
        return travelTargetZ;
    }

    public static float getTravelProgress() {
        return travelProgress;
    }

    public static List<BlockPos> getNearbyNodes() {
        return nearbyNodes;
    }

    public static List<BlockPos> getTravelQueue() {
        return travelQueue;
    }

    public static int getTravelQueueSize() {
        return travelQueue.size();
    }

    public static BlockPos getSelectedTarget() {
        return selectedTarget;
    }

    public static void setSelectedTarget(BlockPos target) {
        selectedTarget = target;
    }

    public static float getIconScale(BlockPos pos) {
        Float scale = iconScales.get(pos);
        return scale == null ? 1.0F : scale.floatValue();
    }

    public static void setIconScale(BlockPos pos, float scale) {
        iconScales.put(pos, Float.valueOf(scale));
    }

    public static Map<BlockPos, Float> getIconScales() {
        return iconScales;
    }
}
