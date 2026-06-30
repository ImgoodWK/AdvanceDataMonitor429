package com.imgood.textech.entity;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.imgood.textech.handler.StarryCosmosSwordConstants;
import com.imgood.textech.handler.StarryEntityMotionUtil;
import com.imgood.textech.handler.StarryPlayerLookup;
import com.imgood.textech.handler.ThrownImpactPoint;
import com.imgood.textech.loader.LoaderItem;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * Giant sword descends over one second, embeds halfway into the ground, persists until sword rain ends.
 */
public class EntityStarrySwordSlam extends Entity implements IEntityAdditionalSpawnData {

    public static final int DESCENT_TICKS = 20;
    public static final float KILL_RADIUS = 10.0F;

    private static final int DW_GROUND_Y = 10;
    private static final int DW_START_Y = 11;
    private static final int DW_EMBEDDED_Y = 12;
    private static final int DW_LIFETIME = 13;
    private static final int DW_DISPLAY_YAW = 14;

    private UUID ownerId;
    private boolean damageApplied;

    public EntityStarrySwordSlam(World world) {
        super(world);
        setSize(2.0F, 8.0F);
        noClip = true;
    }

    public EntityStarrySwordSlam(World world, ThrownImpactPoint impact, EntityLivingBase owner, int lifetimeTicks,
        float displayYawDeg) {
        this(world);
        posX = impact.x;
        posZ = impact.z;
        if (owner != null) {
            ownerId = owner.getUniqueID();
        }

        float bladeLen = StarryCosmosSwordConstants.slamBladeVisibleLengthBlocks();
        float embeddedY = StarryCosmosSwordConstants.slamEmbeddedY((float) impact.groundY);
        float startY = embeddedY + bladeLen + 4.0F;

        posY = startY;
        dataWatcher.updateObject(DW_GROUND_Y, Float.valueOf((float) impact.groundY));
        dataWatcher.updateObject(DW_START_Y, Float.valueOf(startY));
        dataWatcher.updateObject(DW_EMBEDDED_Y, Float.valueOf(embeddedY));
        dataWatcher.updateObject(DW_LIFETIME, Integer.valueOf(lifetimeTicks));
        dataWatcher.updateObject(DW_DISPLAY_YAW, Float.valueOf(displayYawDeg));
        setPosition(posX, posY, posZ);
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(DW_GROUND_Y, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_START_Y, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_EMBEDDED_Y, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_LIFETIME, Integer.valueOf(EntityStarrySwordRainField.DURATION));
        dataWatcher.addObject(DW_DISPLAY_YAW, Float.valueOf(0.0F));
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

    public int getLifetimeTicks() {
        return dataWatcher.getWatchableObjectInt(DW_LIFETIME);
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
                EntityLivingBase owner = resolveOwner();
                StarryEntityMotionUtil
                    .killLivingInRadius(worldObj, posX, getGroundY(), posZ, KILL_RADIUS, owner, this, true);
            }
            if (ticksExisted >= getLifetimeTicks()) {
                setDead();
            }
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
        dataWatcher.updateObject(DW_GROUND_Y, Float.valueOf(tag.getFloat("GroundY")));
        dataWatcher.updateObject(DW_START_Y, Float.valueOf(tag.getFloat("StartY")));
        dataWatcher.updateObject(DW_EMBEDDED_Y, Float.valueOf(tag.getFloat("EmbeddedY")));
        dataWatcher.updateObject(DW_LIFETIME, Integer.valueOf(tag.getInteger("Lifetime")));
        dataWatcher.updateObject(DW_DISPLAY_YAW, Float.valueOf(tag.getFloat("DisplayYaw")));
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
        tag.setInteger("Lifetime", getLifetimeTicks());
        tag.setFloat("DisplayYaw", getDisplayYaw());
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
        buf.writeInt(getLifetimeTicks());
        buf.writeFloat(getDisplayYaw());
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
        dataWatcher.updateObject(DW_LIFETIME, Integer.valueOf(buf.readInt()));
        dataWatcher.updateObject(DW_DISPLAY_YAW, Float.valueOf(buf.readFloat()));
        if (buf.readBoolean()) {
            ownerId = new UUID(buf.readLong(), buf.readLong());
        }
        posY = getStartY();
        setPosition(posX, posY, posZ);
    }
}
