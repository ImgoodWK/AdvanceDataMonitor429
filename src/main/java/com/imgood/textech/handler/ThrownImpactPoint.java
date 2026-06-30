package com.imgood.textech.handler;

import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/**
 * Resolved landing point for a thrown starry sword (shared by slam, rain field, VFX).
 */
public final class ThrownImpactPoint {

    public final double x;
    public final double hitY;
    public final double z;
    /** Top-solid surface Y used for giant-sword embed and rain field. */
    public final double groundY;

    public ThrownImpactPoint(double x, double hitY, double z, double groundY) {
        this.x = x;
        this.hitY = hitY;
        this.z = z;
        this.groundY = groundY;
    }

    /**
     * @param posX entity X when {@code mop} is null (max-range fallback)
     * @param posY entity Y when {@code mop} is null
     * @param posZ entity Z when {@code mop} is null
     */
    public static ThrownImpactPoint resolve(World world, MovingObjectPosition mop, double posX, double posY,
        double posZ) {
        double x;
        double hitY;
        double z;
        if (mop != null) {
            x = mop.hitVec.xCoord;
            hitY = mop.hitVec.yCoord;
            z = mop.hitVec.zCoord;
        } else {
            x = posX;
            hitY = posY;
            z = posZ;
        }

        double groundY;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            if (mop.sideHit == 1) {
                groundY = mop.blockY + StarryEntityMotionUtil.GROUND_SURFACE_OFFSET;
            } else {
                groundY = StarryEntityMotionUtil.getGroundY(world, x, z);
            }
        } else {
            groundY = StarryEntityMotionUtil.getGroundY(world, x, z);
        }

        return new ThrownImpactPoint(x, hitY, z, groundY);
    }
}
