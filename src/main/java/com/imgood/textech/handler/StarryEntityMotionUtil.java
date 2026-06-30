package com.imgood.textech.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Shared motion, ground lookup and combat helpers for starry sword entities.
 */
public final class StarryEntityMotionUtil {

    /** Block-top offset used for sword rain stick and slam embed surface (matches rain entity snap). */
    public static final double GROUND_SURFACE_OFFSET = 1.02D;

    private StarryEntityMotionUtil() {}

    public static void setThrowVelocity(EntityLivingBase thrower, net.minecraft.entity.Entity projectile, float speed) {
        float yaw = thrower.rotationYaw;
        float pitch = thrower.rotationPitch;
        float cosPitch = MathHelper.cos(-pitch * 0.017453292F - (float) Math.PI);
        float sinPitch = MathHelper.sin(-pitch * 0.017453292F - (float) Math.PI);
        float cosYaw = -MathHelper.cos(-yaw * 0.017453292F);
        float sinYaw = MathHelper.sin(-yaw * 0.017453292F);

        projectile.motionX = sinYaw * cosPitch * speed;
        projectile.motionY = sinPitch * speed;
        projectile.motionZ = cosYaw * cosPitch * speed;

        Vec3 look = thrower.getLookVec();
        projectile.setPosition(
            thrower.posX + look.xCoord * 0.8D,
            thrower.posY + thrower.getEyeHeight() - 0.1D + look.yCoord * 0.8D,
            thrower.posZ + look.zCoord * 0.8D);
    }

    public static double getGroundY(World world, double x, double z) {
        int bx = MathHelper.floor_double(x);
        int bz = MathHelper.floor_double(z);
        int by = world.getTopSolidOrLiquidBlock(bx, bz);
        if (by < 0) {
            by = 64;
        }
        return by + GROUND_SURFACE_OFFSET;
    }

    public static MovingObjectPosition traceBlocks(World world, double x0, double y0, double z0, double x1, double y1,
        double z1) {
        return world.rayTraceBlocks(Vec3.createVectorHelper(x0, y0, z0), Vec3.createVectorHelper(x1, y1, z1));
    }

    public static void killLivingInRadius(World world, double centerX, double centerY, double centerZ, float radius,
        EntityLivingBase owner, net.minecraft.entity.Entity exclude) {
        killLivingInRadius(world, centerX, centerY, centerZ, radius, owner, exclude, false);
    }

    public static void killLivingInRadius(World world, double centerX, double centerY, double centerZ, float radius,
        EntityLivingBase owner, net.minecraft.entity.Entity exclude, boolean thrownGreatswordChain) {
        if (world.isRemote || owner == null || radius <= 0.0F) {
            return;
        }
        double expand = radius;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            centerX - expand,
            centerY - expand,
            centerZ - expand,
            centerX + expand,
            centerY + expand,
            centerZ + expand);
        List<?> list = world.getEntitiesWithinAABBExcludingEntity(exclude, box);
        double radiusSq = radius * radius;
        for (Object obj : list) {
            if (!(obj instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) obj;
            if (living == owner || living.isDead) {
                continue;
            }
            double dx = living.posX - centerX;
            double dy = (living.posY + living.height * 0.5D) - centerY;
            double dz = living.posZ - centerZ;
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                continue;
            }
            if (living instanceof EntityPlayer) {
                EntityPlayer target = (EntityPlayer) living;
                if (target.capabilities.isCreativeMode) {
                    continue;
                }
            }
            StarryCosmosSwordUtil.applyDamage(
                living,
                owner,
                thrownGreatswordChain ? StarryCosmosSwordUtil.StarryCosmosAttackKind.GREATSWORD
                    : StarryCosmosSwordUtil.StarryCosmosAttackKind.DEFAULT);
            world.spawnParticle(
                "magicCrit",
                living.posX,
                living.posY + living.height * 0.5D,
                living.posZ,
                0.0D,
                0.1D,
                0.0D);
        }
    }

    public static void killLivingInBox(World world, AxisAlignedBB box, EntityLivingBase owner,
        net.minecraft.entity.Entity exclude) {
        killLivingInBox(world, box, owner, exclude, false);
    }

    public static void killLivingInBox(World world, AxisAlignedBB box, EntityLivingBase owner,
        net.minecraft.entity.Entity exclude, boolean thrownGreatswordChain) {
        if (world.isRemote || owner == null) {
            return;
        }
        List<?> list = world.getEntitiesWithinAABBExcludingEntity(exclude, box);
        for (Object obj : list) {
            if (!(obj instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) obj;
            if (living == owner || living.isDead) {
                continue;
            }
            if (living instanceof EntityPlayer) {
                EntityPlayer target = (EntityPlayer) living;
                if (target.capabilities.isCreativeMode) {
                    continue;
                }
            }
            StarryCosmosSwordUtil.applyDamage(
                living,
                owner,
                thrownGreatswordChain ? StarryCosmosSwordUtil.StarryCosmosAttackKind.GREATSWORD
                    : StarryCosmosSwordUtil.StarryCosmosAttackKind.DEFAULT);
            world.spawnParticle(
                "magicCrit",
                living.posX,
                living.posY + living.height * 0.5D,
                living.posZ,
                0.0D,
                0.1D,
                0.0D);
        }
    }

    public static List<EntityLivingBase> collectHostileInChunkArea(World world, EntityLivingBase anchor,
        int chunkRadius) {
        if (world.isRemote || anchor == null || chunkRadius < 0) {
            return new ArrayList<EntityLivingBase>();
        }
        return collectHostileInChunkAreaAt(world, anchor.posX, anchor.posZ, chunkRadius, anchor);
    }

    /**
     * Hostile mobs ({@link IMob}) in a square of {@code (2 * chunkRadius + 1)^2} chunks centered on
     * ({@code centerX}, {@code centerZ}).
     */
    public static List<EntityLivingBase> collectHostileInChunkAreaAt(World world, double centerX, double centerZ,
        int chunkRadius, net.minecraft.entity.Entity exclude) {
        List<EntityLivingBase> result = new ArrayList<EntityLivingBase>();
        if (world.isRemote || chunkRadius < 0) {
            return result;
        }

        int centerChunkX = MathHelper.floor_double(centerX / 16.0D);
        int centerChunkZ = MathHelper.floor_double(centerZ / 16.0D);
        double minX = (centerChunkX - chunkRadius) * 16.0D;
        double maxX = (centerChunkX + chunkRadius + 1) * 16.0D;
        double minZ = (centerChunkZ - chunkRadius) * 16.0D;
        double maxZ = (centerChunkZ + chunkRadius + 1) * 16.0D;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(minX, 0.0D, minZ, maxX, 256.0D, maxZ);

        List<?> list = world.getEntitiesWithinAABBExcludingEntity(exclude, box);
        for (Object obj : list) {
            if (!(obj instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) obj;
            if (living.isDead) {
                continue;
            }
            if (!(living instanceof IMob)) {
                continue;
            }
            result.add(living);
        }
        return result;
    }

    /**
     * Living entities within {@code radius} of the line segment from ({@code x0,y0,z0}) along
     * normalized direction for {@code length} blocks.
     */
    public static List<EntityLivingBase> collectLivingAlongLine(World world, double x0, double y0, double z0,
        double dirX, double dirY, double dirZ, double length, double radius, EntityLivingBase owner,
        net.minecraft.entity.Entity exclude) {
        List<EntityLivingBase> result = new ArrayList<EntityLivingBase>();
        if (world.isRemote || owner == null || length <= 0.0D || radius <= 0.0D) {
            return result;
        }

        double x1 = x0 + dirX * length;
        double y1 = y0 + dirY * length;
        double z1 = z0 + dirZ * length;
        double pad = radius + 0.5D;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            Math.min(x0, x1) - pad,
            Math.min(y0, y1) - pad,
            Math.min(z0, z1) - pad,
            Math.max(x0, x1) + pad,
            Math.max(y0, y1) + pad,
            Math.max(z0, z1) + pad);
        List<?> list = world.getEntitiesWithinAABBExcludingEntity(exclude, box);
        double radiusSq = radius * radius;

        for (Object obj : list) {
            if (!(obj instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) obj;
            if (living == owner || living.isDead) {
                continue;
            }
            if (living instanceof EntityPlayer) {
                EntityPlayer target = (EntityPlayer) living;
                if (target.capabilities.isCreativeMode) {
                    continue;
                }
            }

            double px = living.posX;
            double py = living.posY + living.height * 0.5D;
            double pz = living.posZ;
            if (distancePointToSegmentSq(px, py, pz, x0, y0, z0, x1, y1, z1) > radiusSq) {
                continue;
            }
            result.add(living);
        }
        return result;
    }

    /**
     * Instant-kill living entities whose center is within {@code radius} of the line segment
     * from ({@code x0,y0,z0}) along normalized ({@code dirX,dirY,dirZ}) for {@code length} blocks.
     */
    public static void killLivingAlongLine(World world, double x0, double y0, double z0, double dirX, double dirY,
        double dirZ, double length, double radius, EntityLivingBase owner, net.minecraft.entity.Entity exclude) {
        killLivingAlongLine(world, x0, y0, z0, dirX, dirY, dirZ, length, radius, owner, exclude, false);
    }

    public static void killLivingAlongLine(World world, double x0, double y0, double z0, double dirX, double dirY,
        double dirZ, double length, double radius, EntityLivingBase owner, net.minecraft.entity.Entity exclude,
        boolean thrownGreatswordChain) {
        if (world.isRemote || owner == null || length <= 0.0D || radius <= 0.0D) {
            return;
        }

        for (EntityLivingBase living : collectLivingAlongLine(
            world,
            x0,
            y0,
            z0,
            dirX,
            dirY,
            dirZ,
            length,
            radius,
            owner,
            exclude)) {
            double px = living.posX;
            double py = living.posY + living.height * 0.5D;
            double pz = living.posZ;
            StarryCosmosSwordUtil.applyDamage(
                living,
                owner,
                thrownGreatswordChain ? StarryCosmosSwordUtil.StarryCosmosAttackKind.GREATSWORD
                    : StarryCosmosSwordUtil.StarryCosmosAttackKind.DEFAULT);
            world.spawnParticle("magicCrit", px, py, pz, 0.0D, 0.1D, 0.0D);
        }
    }

    private static double distancePointToSegmentSq(double px, double py, double pz, double x0, double y0, double z0,
        double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq < 1.0E-8D) {
            double ox = px - x0;
            double oy = py - y0;
            double oz = pz - z0;
            return ox * ox + oy * oy + oz * oz;
        }
        double t = ((px - x0) * dx + (py - y0) * dy + (pz - z0) * dz) / lenSq;
        if (t < 0.0D) {
            t = 0.0D;
        } else if (t > 1.0D) {
            t = 1.0D;
        }
        double cx = x0 + dx * t;
        double cy = y0 + dy * t;
        double cz = z0 + dz * t;
        double rx = px - cx;
        double ry = py - cy;
        double rz = pz - cz;
        return rx * rx + ry * ry + rz * rz;
    }

    public static boolean isSolidAt(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        return block != null && block.getMaterial()
            .isSolid();
    }
}
