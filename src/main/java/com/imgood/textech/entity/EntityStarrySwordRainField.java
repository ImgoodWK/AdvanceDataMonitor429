package com.imgood.textech.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
 * Spawns falling sword models around an impact point for five seconds.
 * Empyrean Holy Judgment rain: one sword at a time, hover, homing to unique hostile targets within 1 chunk.
 */
public class EntityStarrySwordRainField extends Entity implements IEntityAdditionalSpawnData {

    public static final int DURATION = 100;
    private static final float RADIUS = 5.0F;
    private static final int CHUNK_TARGET_RADIUS = 1;
    private static final Random RANDOM = new Random();

    private UUID ownerId;
    private int spawnCooldown;
    private double groundY;
    private boolean homingRain;
    private final Set<Integer> assignedTargetIds = new HashSet<Integer>();

    public EntityStarrySwordRainField(World world) {
        super(world);
        setSize(0.1F, 0.1F);
        noClip = true;
    }

    public EntityStarrySwordRainField(World world, ThrownImpactPoint impact, EntityLivingBase owner) {
        this(world, impact, owner, isEmpyreanRainOwner(owner));
    }

    public EntityStarrySwordRainField(World world, ThrownImpactPoint impact, EntityLivingBase owner,
        boolean homingRain) {
        this(world);
        posX = impact.x;
        posZ = impact.z;
        groundY = impact.groundY;
        posY = groundY;
        this.homingRain = homingRain;
        if (owner != null) {
            ownerId = owner.getUniqueID();
        }
    }

    public static boolean isEmpyreanRainOwner(EntityLivingBase owner) {
        if (!(owner instanceof EntityPlayer)) {
            return true;
        }
        ItemStack held = ((EntityPlayer) owner).getHeldItem();
        return held != null && held.getItem() instanceof ItemStarryCosmosSword
            && !(held.getItem() instanceof ItemHolyJudgment);
    }

    /**
     * Picks the nearest unassigned hostile mob within {@link #CHUNK_TARGET_RADIUS} chunks of the rain center.
     */
    public EntityLivingBase acquireRainTarget() {
        if (worldObj.isRemote) {
            return null;
        }
        List<EntityLivingBase> candidates = StarryEntityMotionUtil
            .collectHostileInChunkAreaAt(worldObj, posX, posZ, CHUNK_TARGET_RADIUS, this);
        EntityLivingBase best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (EntityLivingBase living : candidates) {
            if (living == null || living.isDead) {
                continue;
            }
            int id = living.getEntityId();
            if (assignedTargetIds.contains(id)) {
                continue;
            }
            double dx = living.posX - posX;
            double dz = living.posZ - posZ;
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = living;
            }
        }
        if (best != null) {
            assignedTargetIds.add(best.getEntityId());
        }
        return best;
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
        spawnCooldown = homingRain ? 5 + RANDOM.nextInt(4) : 2 + RANDOM.nextInt(3);

        EntityLivingBase owner = resolveOwner();
        if (homingRain) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double dist = RANDOM.nextDouble() * RADIUS;
            double sx = posX + Math.cos(angle) * dist;
            double sz = posZ + Math.sin(angle) * dist;
            double sy = groundY + 8.0D + RANDOM.nextDouble() * 6.0D;
            EntityStarrySwordRain rain = new EntityStarrySwordRain(worldObj, sx, sy, sz, owner, getEntityId(), true);
            worldObj.spawnEntityInWorld(rain);
            return;
        }

        int batch = 1 + RANDOM.nextInt(2);
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
        homingRain = !tag.hasKey("HomingRain") || tag.getBoolean("HomingRain");
        if (tag.hasKey("Owner")) {
            ownerId = UUID.fromString(tag.getString("Owner"));
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setDouble("GroundY", groundY);
        tag.setBoolean("HomingRain", homingRain);
        if (ownerId != null) {
            tag.setString("Owner", ownerId.toString());
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeDouble(posX);
        buf.writeDouble(posZ);
        buf.writeDouble(groundY);
        buf.writeBoolean(homingRain);
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
        homingRain = buf.readBoolean();
        posY = groundY;
        if (buf.readBoolean()) {
            ownerId = new UUID(buf.readLong(), buf.readLong());
        }
        setPosition(posX, posY, posZ);
    }
}
