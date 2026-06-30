package com.imgood.textech.entity;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.imgood.textech.handler.StarryCosmosSounds;
import com.imgood.textech.handler.StarryEntityMotionUtil;
import com.imgood.textech.handler.StarryPlayerLookup;
import com.imgood.textech.handler.ThrownImpactPoint;
import com.imgood.textech.loader.LoaderItem;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * Thrown sword flies straight along the thrower's look vector for one chunk, then spawns sword rain.
 */
public class EntityStarrySwordThrown extends Entity implements IEntityAdditionalSpawnData {

    private static final float FLIGHT_SPEED = 2.8F;
    private static final double MAX_DISTANCE = 16.0D;

    private UUID throwerId;
    private double traveledDistance;
    private double dirX;
    private double dirY;
    private double dirZ;

    public EntityStarrySwordThrown(World world) {
        super(world);
        setSize(0.5F, 0.5F);
        dirZ = 1.0D;
    }

    public EntityStarrySwordThrown(World world, EntityLivingBase thrower, ItemStack stack) {
        this(world);
        if (thrower != null) {
            throwerId = thrower.getUniqueID();

            Vec3 look = thrower.getLookVec();
            double len = look.lengthVector();
            if (len < 0.001D) {
                dirX = 0.0D;
                dirY = 0.0D;
                dirZ = 1.0D;
            } else {
                dirX = look.xCoord / len;
                dirY = look.yCoord / len;
                dirZ = look.zCoord / len;
            }

            motionX = dirX * FLIGHT_SPEED;
            motionY = dirY * FLIGHT_SPEED;
            motionZ = dirZ * FLIGHT_SPEED;

            setPosition(
                thrower.posX + dirX * 0.8D,
                thrower.posY + thrower.getEyeHeight() - 0.1D + dirY * 0.8D,
                thrower.posZ + dirZ * 0.8D);

            traveledDistance = 0.0D;
        }
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        super.onUpdate();

        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;

        if (!worldObj.isRemote) {
            EntityLivingBase owner = resolveThrower();
            if (owner != null) {
                StarryEntityMotionUtil.killLivingInBox(worldObj, boundingBox.expand(0.35D, 0.35D, 0.35D), owner, this);
            }

            MovingObjectPosition blockHit = StarryEntityMotionUtil
                .traceBlocks(worldObj, prevPosX, prevPosY, prevPosZ, posX + motionX, posY + motionY, posZ + motionZ);
            if (blockHit != null) {
                onImpact(blockHit);
                return;
            }
        }

        moveEntity(motionX, motionY, motionZ);
        traveledDistance += FLIGHT_SPEED;

        if (worldObj.isRemote) {
            worldObj.spawnParticle("magicCrit", posX, posY, posZ, -motionX * 0.04D, -motionY * 0.04D, -motionZ * 0.04D);
        }

        if (!worldObj.isRemote) {
            if (traveledDistance >= MAX_DISTANCE || ticksExisted > 80) {
                onImpact(null);
            }
        }
    }

    private void onImpact(MovingObjectPosition mop) {
        if (!worldObj.isRemote) {
            ThrownImpactPoint impact = ThrownImpactPoint.resolve(worldObj, mop, posX, posY, posZ);
            setPosition(impact.x, impact.hitY, impact.z);

            EntityLivingBase owner = resolveThrower();
            float displayYaw = (float) (Math.atan2(dirX, dirZ) * 180.0D / Math.PI);
            StarryCosmosSounds.playSlamImpact(worldObj, impact.x, impact.groundY, impact.z);
            StarryCosmosSounds.playRainStart(worldObj, impact.x, impact.groundY, impact.z);
            EntityStarrySwordSlam slam = new EntityStarrySwordSlam(
                worldObj,
                impact,
                owner,
                EntityStarrySwordRainField.DURATION,
                displayYaw);
            worldObj.spawnEntityInWorld(slam);
            spawnSwordRainField(impact, owner);
        }
        setDead();
    }

    private void spawnSwordRainField(ThrownImpactPoint impact, EntityLivingBase owner) {
        EntityStarrySwordRainField field = new EntityStarrySwordRainField(worldObj, impact, owner);
        worldObj.spawnEntityInWorld(field);
    }

    private EntityLivingBase resolveThrower() {
        if (throwerId == null) {
            return null;
        }
        return StarryPlayerLookup.findPlayer(worldObj, throwerId);
    }

    public double getDirX() {
        return dirX;
    }

    public double getDirY() {
        return dirY;
    }

    public double getDirZ() {
        return dirZ;
    }

    public ItemStack getDisplayedStack() {
        return new ItemStack(LoaderItem.starryCosmosSword);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("Thrower")) {
            throwerId = UUID.fromString(tag.getString("Thrower"));
        }
        traveledDistance = tag.getDouble("Traveled");
        dirX = tag.getDouble("DirX");
        dirY = tag.getDouble("DirY");
        dirZ = tag.getDouble("DirZ");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        if (throwerId != null) {
            tag.setString("Thrower", throwerId.toString());
        }
        tag.setDouble("Traveled", traveledDistance);
        tag.setDouble("DirX", dirX);
        tag.setDouble("DirY", dirY);
        tag.setDouble("DirZ", dirZ);
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeDouble(dirX);
        buf.writeDouble(dirY);
        buf.writeDouble(dirZ);
        if (throwerId == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeLong(throwerId.getMostSignificantBits());
            buf.writeLong(throwerId.getLeastSignificantBits());
        }
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        dirX = buf.readDouble();
        dirY = buf.readDouble();
        dirZ = buf.readDouble();
        if (buf.readBoolean()) {
            throwerId = new UUID(buf.readLong(), buf.readLong());
        }
    }
}
