package com.imgood.advancedatamonitor.handler;

import com.imgood.advancedatamonitor.utils.BlockPos;

public final class GrappleQueuedHop {

    public final int x;
    public final int y;
    public final int z;
    public final double speed;

    public GrappleQueuedHop(int x, int y, int z, double speed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.speed = speed;
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }
}
