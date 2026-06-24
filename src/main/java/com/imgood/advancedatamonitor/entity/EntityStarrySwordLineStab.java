package com.imgood.advancedatamonitor.entity;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.loader.LoaderItem;
import com.imgood.advancedatamonitor.handler.StarryCosmosSwordConstants;
import com.imgood.advancedatamonitor.handler.StarryCosmosSounds;
import com.imgood.advancedatamonitor.handler.StarryCosmosSwordUtil;
import com.imgood.advancedatamonitor.handler.StarryPlayerLookup;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * Giant vertical sword stab on a single target (left-click line slash), based on {@link EntityStarrySwordSlam}.
 */
public class EntityStarrySwordLineStab extends Entity implements IEntityAdditionalSpawnData {

    public static final int DESCENT_TICKS = 10;
    public static final int LIFETIME = 35;

    private static final int DW_GROUND_Y = 10;
    private static final int DW_START_Y = 11;
    private static final int DW_EMBEDDED_Y = 12;
    private static final int DW_DISPLAY_YAW = 13;
    private static final int DW_RENDER_SCALE = 14;

    private UUID ownerId;
    private int targetEntityId;
    private boolean damageApplied;

    public EntityStarrySwordLineStab(World world) {
        super(world);
        setSize(2.0F, 8.0F);
        noClip = true;
    }

    public EntityStarrySwordLineStab(World world, EntityLivingBase target, EntityLivingBase owner,
        float displayYawDeg) {
        this(world, target, owner, displayYawDeg, StarryCosmosSwordConstants.SCALE_SLAM);
    }

    public EntityStarrySwordLineStab(World world, EntityLivingBase target, EntityLivingBase owner,
        float displayYawDeg, float renderScale) {
        this(world);
        if (target == null) {
            return;
        }
        targetEntityId = target.getEntityId();
        posX = target.posX;
        posZ = target.posZ;
        if (owner != null) {
            ownerId = owner.getUniqueID();
        }

        float scaleRatio = renderScale / StarryCosmosSwordConstants.SCALE_SLAM;
        setSize(2.0F * scaleRatio, 8.0F * scaleRatio);

        float groundY = (float) target.posY;
        float bladeLen = StarryCosmosSwordConstants.slamBladeVisibleLengthBlocks(renderScale);
        float embeddedY = StarryCosmosSwordConstants.slamEmbeddedY(groundY, renderScale);
        float startY = embeddedY + bladeLen + 3.0F * scaleRatio;

        posY = startY;
        dataWatcher.updateObject(DW_GROUND_Y, Float.valueOf(groundY));
        dataWatcher.updateObject(DW_START_Y, Float.valueOf(startY));
        dataWatcher.updateObject(DW_EMBEDDED_Y, Float.valueOf(embeddedY));
        dataWatcher.updateObject(DW_DISPLAY_YAW, Float.valueOf(displayYawDeg));
        dataWatcher.updateObject(DW_RENDER_SCALE, Float.valueOf(renderScale));
        setPosition(posX, posY, posZ);
        if (!world.isRemote) {
            StarryCosmosSounds.playStabFall(world, posX, embeddedY, posZ, renderScale);
        }
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(DW_GROUND_Y, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_START_Y, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_EMBEDDED_Y, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_DISPLAY_YAW, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_RENDER_SCALE, Float.valueOf(StarryCosmosSwordConstants.SCALE_SLAM));
    }

    public float getGroundY() {
        return dataWatcher.getWatchableObjectFloat(DW_GROUND_Y);
    }

    public float getStartY() {
        return dataWatcher.getWatchableObjectFloat(DW_START_Y);
    }

    public float getEmbeddedY() {
        return dataWatcher.getWatchableObjectFloat(DW_EMBEDDED_Y);
    }

    public float getDescentProgress() {
        if (DESCENT_TICKS <= 0) {
            return 1.0F;
        }
        return Math.min(1.0F, (float) ticksExisted / (float) DESCENT_TICKS);
    }

    public boolean isEmbedded() {
        return ticksExisted >= DESCENT_TICKS;
    }

    public ItemStack getDisplayedStack() {
        return new ItemStack(LoaderItem.starryCosmosSword);
    }

    public float getDisplayYaw() {
        return dataWatcher.getWatchableObjectFloat(DW_DISPLAY_YAW);
    }

    public float getRenderScale() {
        return dataWatcher.getWatchableObjectFloat(DW_RENDER_SCALE);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        float startY = getStartY();
        float embeddedY = getEmbeddedY();
        prevPosY = posY;

        if (ticksExisted >= DESCENT_TICKS) {
            posY = embeddedY;
        } else {
            float progress = getDescentProgress();
            posY = startY - (startY - embeddedY) * progress;
        }
        setPosition(posX, posY, posZ);

        if (!worldObj.isRemote) {
            if (ticksExisted >= DESCENT_TICKS && !damageApplied) {
                damageApplied = true;
                applyTargetDamage();
            }
            if (ticksExisted >= LIFETIME) {
                setDead();
            }
        }
    }

    private void applyTargetDamage() {
        EntityLivingBase owner = resolveOwner();
        Entity entity = worldObj.getEntityByID(targetEntityId);
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        if (target.isDead || target == owner) {
            return;
        }
        StarryCosmosSwordUtil.instantKill(target, owner);
        StarryCosmosSounds.playStabImpact(worldObj, target.posX, target.posY, target.posZ, getRenderScale());
        worldObj.spawnParticle(
            "magicCrit",
            target.posX,
            target.posY + target.height * 0.5D,
            target.posZ,
            0.0D,
            0.15D,
            0.0D);
    }

    private EntityLivingBase resolveOwner() {
        if (ownerId == null) {
            return null;
        }
        return StarryPlayerLookup.findPlayer(worldObj, ownerId);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        dataWatcher.updateObject(DW_GROUND_Y, Float.valueOf(tag.getFloat("GroundY")));
        dataWatcher.updateObject(DW_START_Y, Float.valueOf(tag.getFloat("StartY")));
        dataWatcher.updateObject(DW_EMBEDDED_Y, Float.valueOf(tag.getFloat("EmbeddedY")));
        dataWatcher.updateObject(DW_DISPLAY_YAW, Float.valueOf(tag.getFloat("DisplayYaw")));
        dataWatcher.updateObject(
            DW_RENDER_SCALE,
            Float.valueOf(tag.hasKey("RenderScale") ? tag.getFloat("RenderScale")
                : StarryCosmosSwordConstants.SCALE_SLAM));
        targetEntityId = tag.getInteger("TargetId");
        damageApplied = tag.getBoolean("DamageApplied");
        if (tag.hasKey("Owner")) {
            ownerId = UUID.fromString(tag.getString("Owner"));
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setFloat("GroundY", getGroundY());
        tag.setFloat("StartY", getStartY());
        tag.setFloat("EmbeddedY", getEmbeddedY());
        tag.setFloat("DisplayYaw", getDisplayYaw());
        tag.setFloat("RenderScale", getRenderScale());
        tag.setInteger("TargetId", targetEntityId);
        tag.setBoolean("DamageApplied", damageApplied);
        if (ownerId != null) {
            tag.setString("Owner", ownerId.toString());
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeDouble(posX);
        buf.writeDouble(posZ);
        buf.writeFloat(getGroundY());
        buf.writeFloat(getStartY());
        buf.writeFloat(getEmbeddedY());
        buf.writeFloat(getDisplayYaw());
        buf.writeFloat(getRenderScale());
        buf.writeInt(targetEntityId);
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
        dataWatcher.updateObject(DW_GROUND_Y, Float.valueOf(buf.readFloat()));
        dataWatcher.updateObject(DW_START_Y, Float.valueOf(buf.readFloat()));
        dataWatcher.updateObject(DW_EMBEDDED_Y, Float.valueOf(buf.readFloat()));
        dataWatcher.updateObject(DW_DISPLAY_YAW, Float.valueOf(buf.readFloat()));
        dataWatcher.updateObject(DW_RENDER_SCALE, Float.valueOf(buf.readFloat()));
        targetEntityId = buf.readInt();
        if (buf.readBoolean()) {
            ownerId = new UUID(buf.readLong(), buf.readLong());
        }
        posY = getStartY();
        setPosition(posX, posY, posZ);
    }
}
