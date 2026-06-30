package com.imgood.textech.entity;

import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.imgood.textech.handler.StarryPlayerLookup;
import com.imgood.textech.handler.ThrownImpactPoint;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * Spawns falling sword models around an impact point for five seconds.
 */
public class EntityStarrySwordRainField extends Entity implements IEntityAdditionalSpawnData {

    public static final int DURATION = 100;
    private static final float RADIUS = 5.0F;
    private static final Random RANDOM = new Random();

    private UUID ownerId;
    private int spawnCooldown;
    private double groundY;

    public EntityStarrySwordRainField(World world) {
        super(world);
        setSize(0.1F, 0.1F);
        noClip = true;
    }

    public EntityStarrySwordRainField(World world, ThrownImpactPoint impact, EntityLivingBase owner) {
        this(world);
        posX = impact.x;
        posZ = impact.z;
        groundY = impact.groundY;
        posY = groundY;
        if (owner != null) {
            ownerId = owner.getUniqueID();
        }
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (ticksExisted >= DURATION) {
            setDead();
            return;
        }

        if (worldObj.isRemote) {
            if (ticksExisted % 4 == 0) {
                worldObj.spawnParticle(
                    "witchMagic",
                    posX + (rand.nextDouble() - 0.5D) * 2.0D,
                    posY + 0.2D,
                    posZ + (rand.nextDouble() - 0.5D) * 2.0D,
                    0.0D,
                    0.15D,
                    0.0D);
            }
            return;
        }

        spawnCooldown--;
        if (spawnCooldown > 0) {
            return;
        }
        spawnCooldown = 2 + RANDOM.nextInt(3);

        int batch = 1 + RANDOM.nextInt(2);
        EntityLivingBase owner = resolveOwner();
        for (int i = 0; i < batch; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double dist = RANDOM.nextDouble() * RADIUS;
            double sx = posX + Math.cos(angle) * dist;
            double sz = posZ + Math.sin(angle) * dist;
            double sy = groundY + 8.0D + RANDOM.nextDouble() * 6.0D;

            EntityStarrySwordRain rain = new EntityStarrySwordRain(worldObj, sx, sy, sz, owner);
            worldObj.spawnEntityInWorld(rain);
        }
    }

    private EntityLivingBase resolveOwner() {
        if (ownerId == null) {
            return null;
        }
        return StarryPlayerLookup.findPlayer(worldObj, ownerId);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        groundY = tag.getDouble("GroundY");
        if (tag.hasKey("Owner")) {
            ownerId = UUID.fromString(tag.getString("Owner"));
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setDouble("GroundY", groundY);
        if (ownerId != null) {
            tag.setString("Owner", ownerId.toString());
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeDouble(posX);
        buf.writeDouble(posZ);
        buf.writeDouble(groundY);
        if (ownerId == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeLong(ownerId.getMostSignificantBits());
            buf.writeLong(ownerId.getLeastSignificantBits());
        }
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        posX = buf.readDouble();
        posZ = buf.readDouble();
        groundY = buf.readDouble();
        posY = groundY;
        if (buf.readBoolean()) {
            ownerId = new UUID(buf.readLong(), buf.readLong());
        }
        setPosition(posX, posY, posZ);
    }
}
