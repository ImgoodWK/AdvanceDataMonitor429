package com.imgood.textech.client.worldmap;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-side cache for the latest world map capture consent offer. */
@SideOnly(Side.CLIENT)
public final class WorldMapCaptureClientState {

    private static String latestRequestId;

    private WorldMapCaptureClientState() {}

    public static void setLatestRequestId(String requestId) {
        latestRequestId = requestId != null && !requestId.isEmpty() ? requestId : null;
    }

    public static String getLatestRequestId() {
        return latestRequestId;
    }
}
