package com.imgood.advancedatamonitor.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.handler.StarryCosmosSounds;
import com.imgood.advancedatamonitor.handler.StarryEntityMotionUtil;
import com.imgood.advancedatamonitor.handler.StarryPlayerLookup;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * Display names / 显示名称:
 * - EN: Empyrean Holy Judgment (line slash skill entity)
 * - ZH: 至高天圣裁（左键剑波技能实体）
 * Lang keys: item.starryCosmosSword.name (parent item)
 *
 * Left-click line slash: particle beam along aim, spawns vertical giant swords on targets in range.
 */
public class EntityStarrySwordLineSlash extends Entity implements IEntityAdditionalSpawnData {

    public static final int MAX_LIFE = 18;
    public static final float SLASH_RANGE = 14.0F;
    public static final double LINE_HIT_RADIUS = 1.75D;

    private UUID ownerId;
    private double originX;
    private double originY;
    private double originZ;
    private double dirX;
    private double dirY = 0.0D;
    private double dirZ = 1.0D;
    private float slashRange = SLASH_RANGE;
    private boolean stabsSpawned;

    public EntityStarrySwordLineSlash(World world) {
        super(world);
        setSize(0.1F, 0.1F);
        noClip = true;
    }

    public EntityStarrySwordLineSlash(World world, EntityLivingBase owner) {
        this(world);
        if (owner == null) {
            return;
        }
        ownerId = owner.getUniqueID();

        Vec3 look = owner.getLookVec();
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

        originX = owner.posX + dirX * 0.6D;
        originY = owner.posY + owner.getEyeHeight() * 0.45D;
        originZ = owner.posZ + dirZ * 0.6D;
        posX = originX;
        posY = originY;
        posZ = originZ;
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (ticksExisted > MAX_LIFE) {
            setDead();
            return;
        }

        if (worldObj.isRemote) {
            spawnBeamParticles();
            return;
        }

        if (!stabsSpawned) {
            stabsSpawned = true;
            spawnTargetStabs();
        }
    }

    private void spawnBeamParticles() {
        int samples = 6;
        for (int i = 0; i < samples; i++) {
            double t = worldObj.rand.nextDouble() * slashRange;
            double px = originX + dirX * t;
            double py = originY + dirY * t + (worldObj.rand.nextDouble() - 0.5D) * 0.35D;
            double pz = originZ + dirZ * t;
            double drift = 0.04D + worldObj.rand.nextDouble() * 0.06D;
            worldObj.spawnParticle("witchMagic", px, py, pz, dirX * drift, dirY * drift * 0.5D + 0.02D, dirZ * drift);
            worldObj.spawnParticle("portal", px, py + 0.15D, pz, -dirX * 0.02D, 0.05D, -dirZ * 0.02D);
            if (i % 2 == 0) {
                worldObj.spawnParticle("magicCrit", px, py, pz, 0.0D, 0.08D, 0.0D);
            }
        }
    }

    private void spawnTargetStabs() {
        EntityLivingBase owner = resolveOwner();
        if (owner == null) {
            return;
        }

        StarryCosmosSounds.playSlash(worldObj, originX, originY, originZ);

        List<EntityLivingBase> targets = StarryEntityMotionUtil.collectLivingAlongLine(
            worldObj,
            originX,
            originY,
            originZ,
            dirX,
            dirY,
            dirZ,
            slashRange,
            LINE_HIT_RADIUS,
            owner,
            this);

        Set<Integer> seen = new HashSet<Integer>();
        float displayYaw = (float) (Math.atan2(dirX, dirZ) * 180.0D / Math.PI);
        for (EntityLivingBase target : targets) {
            if (target == null || target.isDead) {
                continue;
            }
            int id = target.getEntityId();
            if (!seen.add(id)) {
                continue;
            }
            worldObj.spawnEntityInWorld(new EntityStarrySwordLineStab(worldObj, target, owner, displayYaw));
        }
    }

    private EntityLivingBase resolveOwner() {
        if (ownerId == null) {
            return null;
        }
        return StarryPlayerLookup.findPlayer(worldObj, ownerId);
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

    public float getSlashRange() {
        return slashRange;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        originX = tag.getDouble("OriginX");
        originY = tag.getDouble("OriginY");
        originZ = tag.getDouble("OriginZ");
        dirX = tag.getDouble("DirX");
        dirY = tag.getDouble("DirY");
        dirZ = tag.getDouble("DirZ");
        slashRange = tag.getFloat("Range");
        stabsSpawned = tag.getBoolean("StabsSpawned");
        if (tag.hasKey("Owner")) {
            ownerId = UUID.fromString(tag.getString("Owner"));
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setDouble("OriginX", originX);
        tag.setDouble("OriginY", originY);
        tag.setDouble("OriginZ", originZ);
        tag.setDouble("DirX", dirX);
        tag.setDouble("DirY", dirY);
        tag.setDouble("DirZ", dirZ);
        tag.setFloat("Range", slashRange);
        tag.setBoolean("StabsSpawned", stabsSpawned);
        if (ownerId != null) {
            tag.setString("Owner", ownerId.toString());
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeDouble(dirX);
        buf.writeDouble(dirY);
        buf.writeDouble(dirZ);
        buf.writeFloat(slashRange);
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
        originX = buf.readDouble();
        originY = buf.readDouble();
        originZ = buf.readDouble();
        dirX = buf.readDouble();
        dirY = buf.readDouble();
        dirZ = buf.readDouble();
        slashRange = buf.readFloat();
        posX = originX;
        posY = originY;
        posZ = originZ;
        if (buf.readBoolean()) {
            ownerId = new UUID(buf.readLong(), buf.readLong());
        }
        setPosition(posX, posY, posZ);
    }
}
