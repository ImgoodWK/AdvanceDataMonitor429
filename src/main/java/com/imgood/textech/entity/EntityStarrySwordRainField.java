package com.imgood.textech.entity;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.imgood.textech.handler.StarryEntityMotionUtil;
import com.imgood.textech.handler.StarryPlayerLookup;
import com.imgood.textech.handler.ThrownImpactPoint;
import com.imgood.textech.items.ItemHolyJudgment;
import com.imgood.textech.items.ItemStarryCosmosSword;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * Spawns falling swords around an impact point for five seconds.
 * Targeted rain (Empyrean / non-HolyJudgment): scans chunk-radius for hostile mobs and drops swords above their heads.
 * Legacy rain: random scatter within radius.
 */
public class EntityStarrySwordRainField extends Entity implements IEntityAdditionalSpawnData {

    public static final int DURATION = 100;
    private static final float RADIUS = 5.0F;
    private static final int TARGET_CHUNK_RADIUS = 1;
    private static final Random RANDOM = new Random();

    private UUID ownerId;
    private int spawnCooldown;
    private double groundY;
    private boolean targetedRain;

    public EntityStarrySwordRainField(World world) {
        super(world);
        setSize(0.1F, 0.1F);
        noClip = true;
    }

    public EntityStarrySwordRainField(World world, ThrownImpactPoint impact, EntityLivingBase owner) {
        this(world, impact, owner, isTargetedRainOwner(owner));
    }

    public EntityStarrySwordRainField(World world, ThrownImpactPoint impact, EntityLivingBase owner,
        boolean targetedRain) {
        this(world);
        posX = impact.x;
        posZ = impact.z;
        groundY = impact.groundY;
        posY = groundY;
        this.targetedRain = targetedRain;
        if (owner != null) {
            ownerId = owner.getUniqueID();
        }
    }

    public static boolean isTargetedRainOwner(EntityLivingBase owner) {
        if (!(owner instanceof EntityPlayer)) {
            return true;
        }
        ItemStack held = ((EntityPlayer) owner).getHeldItem();
        return held != null && held.getItem() instanceof ItemStarryCosmosSword
            && !(held.getItem() instanceof ItemHolyJudgment);
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
        spawnCooldown = targetedRain ? 5 + RANDOM.nextInt(4) : 2 + RANDOM.nextInt(3);

        EntityLivingBase owner = resolveOwner();

        if (targetedRain) {
            List<EntityLivingBase> targets = StarryEntityMotionUtil
                .collectHostileInChunkAreaAt(worldObj, posX, posZ, TARGET_CHUNK_RADIUS, this);

            if (targets.isEmpty()) {
                // Fallback: random legacy rain
                int batch = 1 + RANDOM.nextInt(2);
                for (int i = 0; i < batch; i++) {
                    randomLegacySword(owner);
                }
                return;
            }

            // Drop one sword above each hostile mob
            for (EntityLivingBase target : targets) {
                if (target == null || target.isDead) {
                    continue;
                }
                double sx = target.posX;
                double sy = target.posY + target.height + 8.0D + RANDOM.nextDouble() * 4.0D;
                double sz = target.posZ;
                EntityStarrySwordRain rain = new EntityStarrySwordRain(worldObj, sx, sy, sz, owner);
                worldObj.spawnEntityInWorld(rain);
            }
            return;
        }

        int batch = 1 + RANDOM.nextInt(2);
        for (int i = 0; i < batch; i++) {
            randomLegacySword(owner);
        }
    }

    private void randomLegacySword(EntityLivingBase owner) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
        double dist = RANDOM.nextDouble() * RADIUS;
        double sx = posX + Math.cos(angle) * dist;
        double sz = posZ + Math.sin(angle) * dist;
        double sy = groundY + 8.0D + RANDOM.nextDouble() * 6.0D;
        EntityStarrySwordRain rain = new EntityStarrySwordRain(worldObj, sx, sy, sz, owner);
        worldObj.spawnEntityInWorld(rain);
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
        targetedRain = tag.getBoolean("HomingRain");
        if (tag.hasKey("Owner")) {
            ownerId = UUID.fromString(tag.getString("Owner"));
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setDouble("GroundY", groundY);
        tag.setBoolean("HomingRain", targetedRain);
        if (ownerId != null) {
            tag.setString("Owner", ownerId.toString());
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeDouble(posX);
        buf.writeDouble(posZ);
        buf.writeDouble(groundY);
        buf.writeBoolean(targetedRain);
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
        targetedRain = buf.readBoolean();
        posY = groundY;
        if (buf.readBoolean()) {
            ownerId = new UUID(buf.readLong(), buf.readLong());
        }
        setPosition(posX, posY, posZ);
    }
}
