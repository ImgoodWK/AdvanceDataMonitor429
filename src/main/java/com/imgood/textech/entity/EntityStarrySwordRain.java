package com.imgood.textech.entity;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.imgood.textech.handler.StarryCosmosSounds;
import com.imgood.textech.handler.StarryCosmosSwordUtil;
import com.imgood.textech.handler.StarryEntityMotionUtil;
import com.imgood.textech.handler.StarryPlayerLookup;
import com.imgood.textech.loader.LoaderItem;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * Single sword from sword rain. Legacy mode: gravity fall and ground stick.
 * Empyrean homing mode: hover, lock a unique hostile target within 1 chunk, fly tip-first, then damage.
 */
public class EntityStarrySwordRain extends Entity implements IEntityAdditionalSpawnData {

    public static final byte PHASE_FALLING = 0;
    public static final byte PHASE_STUCK = 1;
    public static final byte PHASE_HOVER = 2;
    public static final byte PHASE_HOMING = 3;

    private static final int STICK_TICKS = 20;
    private static final float GRAVITY = 0.09F;
    private static final int HOVER_TICKS = 14;
    private static final double HOMING_SPEED = 1.35D;
    private static final double HOMING_HIT_DISTANCE = 1.25D;

    private static final int DW_PHASE = 10;
    private static final int DW_STICK_PITCH = 11;
    private static final int DW_STICK_ROLL = 12;
    private static final int DW_STICK_YAW = 13;
    private static final int DW_STICK_TIMER = 14;
    private static final int DW_FALL_YAW = 15;
    private static final int DW_TARGET_ID = 16;

    private UUID ownerId;
    private int rainFieldId;
    private boolean homingRain;
    private int targetEntityId;

    public EntityStarrySwordRain(World world) {
        super(world);
        setSize(0.35F, 0.35F);
    }

    public EntityStarrySwordRain(World world, double x, double y, double z, EntityLivingBase owner) {
        this(world, x, y, z, owner, 0, false);
    }

    public EntityStarrySwordRain(World world, double x, double y, double z, EntityLivingBase owner, int rainFieldId,
        boolean homingRain) {
        this(world);
        setPosition(x, y, z);
        this.rainFieldId = rainFieldId;
        this.homingRain = homingRain;
        if (owner != null) {
            ownerId = owner.getUniqueID();
        }
        float fallYaw = world.rand.nextFloat() * 360.0F;
        dataWatcher.updateObject(DW_FALL_YAW, Float.valueOf(fallYaw));
        if (homingRain) {
            motionX = 0.0D;
            motionY = 0.0D;
            motionZ = 0.0D;
            syncPhase(PHASE_HOVER, -1, 0.0F, 0.0F, fallYaw);
        } else {
            motionX = (world.rand.nextDouble() - 0.5D) * 0.12D;
            motionZ = (world.rand.nextDouble() - 0.5D) * 0.12D;
            motionY = -0.25D - world.rand.nextDouble() * 0.35D;
            syncPhase(PHASE_FALLING, -1, 0.0F, 0.0F, fallYaw);
        }
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(DW_PHASE, Byte.valueOf(PHASE_FALLING));
        dataWatcher.addObject(DW_STICK_PITCH, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_STICK_ROLL, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_STICK_YAW, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_STICK_TIMER, Integer.valueOf(-1));
        dataWatcher.addObject(DW_FALL_YAW, Float.valueOf(0.0F));
        dataWatcher.addObject(DW_TARGET_ID, Integer.valueOf(-1));
    }

    private void syncPhase(byte phase, int stickTimer, float pitch, float roll, float yaw) {
        dataWatcher.updateObject(DW_PHASE, Byte.valueOf(phase));
        dataWatcher.updateObject(DW_STICK_TIMER, Integer.valueOf(stickTimer));
        dataWatcher.updateObject(DW_STICK_PITCH, Float.valueOf(pitch));
        dataWatcher.updateObject(DW_STICK_ROLL, Float.valueOf(roll));
        dataWatcher.updateObject(DW_STICK_YAW, Float.valueOf(yaw));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        byte phase = dataWatcher.getWatchableObjectByte(DW_PHASE);

        if (homingRain) {
            onUpdateHomingRain(phase);
            return;
        }

        int stickTimer = dataWatcher.getWatchableObjectInt(DW_STICK_TIMER);

        if (phase == PHASE_STUCK) {
            stickTimer++;
            dataWatcher.updateObject(DW_STICK_TIMER, Integer.valueOf(stickTimer));
            if (!worldObj.isRemote) {
                EntityLivingBase owner = resolveOwner();
                if (owner != null) {
                    StarryEntityMotionUtil
                        .killLivingInBox(worldObj, boundingBox.expand(0.4D, 0.8D, 0.4D), owner, this, true);
                }
            }
            if (stickTimer >= STICK_TICKS) {
                setDead();
            }
            return;
        }

        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;

        motionY -= GRAVITY;
        moveEntity(motionX, motionY, motionZ);

        if (!worldObj.isRemote) {
            int bx = MathHelper.floor_double(posX);
            int by = MathHelper.floor_double(posY - 0.15D);
            int bz = MathHelper.floor_double(posZ);
            boolean hitGround = onGround || posY <= 0.0D || StarryEntityMotionUtil.isSolidAt(worldObj, bx, by, bz);
            if (hitGround) {
                if (StarryEntityMotionUtil.isSolidAt(worldObj, bx, by, bz)) {
                    posY = by + StarryEntityMotionUtil.GROUND_SURFACE_OFFSET;
                    setPosition(posX, posY, posZ);
                }
                stickIntoGround();
            } else {
                EntityLivingBase owner = resolveOwner();
                if (owner != null) {
                    AxisAlignedBB box = boundingBox.expand(0.35D, 0.6D, 0.35D);
                    StarryEntityMotionUtil.killLivingInBox(worldObj, box, owner, this, true);
                }
            }
        }

        if (ticksExisted > 160) {
            setDead();
        }
    }

    private void onUpdateHomingRain(byte phase) {
        if (phase == PHASE_HOVER) {
            motionX = 0.0D;
            motionY = 0.0D;
            motionZ = 0.0D;
            if (!worldObj.isRemote && ticksExisted >= HOVER_TICKS) {
                beginHoming();
            }
            return;
        }

        if (phase == PHASE_HOMING) {
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;

            if (worldObj.isRemote) {
                return;
            }

            EntityLivingBase target = resolveTarget();
            if (target == null || target.isDead) {
                setDead();
                return;
            }

            double tx = target.posX;
            double ty = target.posY + target.height * 0.5D;
            double tz = target.posZ;
            double dx = tx - posX;
            double dy = ty - posY;
            double dz = tz - posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < HOMING_HIT_DISTANCE) {
                applyHomingImpact(target);
                setDead();
                return;
            }

            motionX = dx / dist * HOMING_SPEED;
            motionY = dy / dist * HOMING_SPEED;
            motionZ = dz / dist * HOMING_SPEED;
            moveEntity(motionX, motionY, motionZ);

            float yaw = (float) (Math.atan2(dx, dz) * 180.0D / Math.PI);
            dataWatcher.updateObject(DW_FALL_YAW, Float.valueOf(yaw));

            if (ticksExisted > 160) {
                setDead();
            }
        }
    }

    private void beginHoming() {
        EntityStarrySwordRainField field = resolveRainField();
        EntityLivingBase target = field != null ? field.acquireRainTarget() : null;
        if (target == null) {
            setDead();
            return;
        }
        targetEntityId = target.getEntityId();
        dataWatcher.updateObject(DW_TARGET_ID, Integer.valueOf(targetEntityId));
        float yaw = (float) (Math.atan2(target.posX - posX, target.posZ - posZ) * 180.0D / Math.PI);
        syncPhase(PHASE_HOMING, -1, 0.0F, 0.0F, yaw);
        dataWatcher.updateObject(DW_FALL_YAW, Float.valueOf(yaw));
    }

    private void applyHomingImpact(EntityLivingBase target) {
        EntityLivingBase owner = resolveOwner();
        if (owner == null || target.isDead) {
            return;
        }
        StarryCosmosSwordUtil.applyDamage(target, owner, StarryCosmosSwordUtil.StarryCosmosAttackKind.DEFAULT);
        StarryCosmosSounds.playRainSwordImpact(worldObj, target.posX, target.posY, target.posZ, worldObj.rand);
        worldObj.spawnParticle(
            "magicCrit",
            target.posX,
            target.posY + target.height * 0.5D,
            target.posZ,
            0.0D,
            0.15D,
            0.0D);
    }

    private EntityStarrySwordRainField resolveRainField() {
        if (rainFieldId <= 0) {
            return null;
        }
        Entity entity = worldObj.getEntityByID(rainFieldId);
        if (entity instanceof EntityStarrySwordRainField) {
            return (EntityStarrySwordRainField) entity;
        }
        return null;
    }

    private EntityLivingBase resolveTarget() {
        int id = targetEntityId > 0 ? targetEntityId : dataWatcher.getWatchableObjectInt(DW_TARGET_ID);
        if (id <= 0) {
            return null;
        }
        Entity entity = worldObj.getEntityByID(id);
        if (entity instanceof EntityLivingBase) {
            return (EntityLivingBase) entity;
        }
        return null;
    }

    private void stickIntoGround() {
        motionX = 0.0D;
        motionY = 0.0D;
        motionZ = 0.0D;
        StarryCosmosSounds.playRainSwordImpact(worldObj, posX, posY, posZ, worldObj.rand);
        float pitchFromVertical = 5.0F + worldObj.rand.nextFloat() * 35.0F;
        float roll = (worldObj.rand.nextFloat() - 0.5F) * 30.0F;
        float yaw = worldObj.rand.nextFloat() * 360.0F;
        syncPhase(PHASE_STUCK, 0, pitchFromVertical, roll, yaw);
    }

    private EntityLivingBase resolveOwner() {
        if (ownerId == null) {
            return null;
        }
        return StarryPlayerLookup.findPlayer(worldObj, ownerId);
    }

    public boolean isStuck() {
        return dataWatcher.getWatchableObjectByte(DW_PHASE) == PHASE_STUCK;
    }

    public float getStickPitch() {
        return dataWatcher.getWatchableObjectFloat(DW_STICK_PITCH);
    }

    public float getStickRoll() {
        return dataWatcher.getWatchableObjectFloat(DW_STICK_ROLL);
    }

    public float getStickYaw() {
        return dataWatcher.getWatchableObjectFloat(DW_STICK_YAW);
    }

    public float getFallYaw() {
        return dataWatcher.getWatchableObjectFloat(DW_FALL_YAW);
    }

    public ItemStack getDisplayedStack() {
        return new ItemStack(LoaderItem.starryCosmosSword);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        syncPhase(
            tag.getByte("Phase"),
            tag.getInteger("StickTimer"),
            tag.getFloat("StickPitch"),
            tag.getFloat("StickRoll"),
            tag.getFloat("StickYaw"));
        dataWatcher.updateObject(DW_FALL_YAW, Float.valueOf(tag.getFloat("FallYaw")));
        dataWatcher.updateObject(DW_TARGET_ID, Integer.valueOf(tag.getInteger("TargetId")));
        rainFieldId = tag.getInteger("RainFieldId");
        homingRain = tag.getBoolean("HomingRain");
        targetEntityId = tag.getInteger("TargetId");
        if (tag.hasKey("Owner")) {
            ownerId = UUID.fromString(tag.getString("Owner"));
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setByte("Phase", dataWatcher.getWatchableObjectByte(DW_PHASE));
        tag.setInteger("StickTimer", dataWatcher.getWatchableObjectInt(DW_STICK_TIMER));
        tag.setFloat("StickPitch", getStickPitch());
        tag.setFloat("StickRoll", getStickRoll());
        tag.setFloat("StickYaw", getStickYaw());
        tag.setFloat("FallYaw", getFallYaw());
        tag.setInteger("TargetId", dataWatcher.getWatchableObjectInt(DW_TARGET_ID));
        tag.setInteger("RainFieldId", rainFieldId);
        tag.setBoolean("HomingRain", homingRain);
        if (ownerId != null) {
            tag.setString("Owner", ownerId.toString());
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeByte(dataWatcher.getWatchableObjectByte(DW_PHASE));
        buf.writeFloat(getStickPitch());
        buf.writeFloat(getStickRoll());
        buf.writeFloat(getStickYaw());
        buf.writeInt(dataWatcher.getWatchableObjectInt(DW_STICK_TIMER));
        buf.writeFloat(getFallYaw());
        buf.writeInt(dataWatcher.getWatchableObjectInt(DW_TARGET_ID));
        buf.writeInt(rainFieldId);
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
        byte phase = buf.readByte();
        float pitch = buf.readFloat();
        float roll = buf.readFloat();
        float yaw = buf.readFloat();
        int timer = buf.readInt();
        float fallYaw = buf.readFloat();
        int targetId = buf.readInt();
        rainFieldId = buf.readInt();
        homingRain = buf.readBoolean();
        syncPhase(phase, timer, pitch, roll, yaw);
        dataWatcher.updateObject(DW_FALL_YAW, Float.valueOf(fallYaw));
        dataWatcher.updateObject(DW_TARGET_ID, Integer.valueOf(targetId));
        targetEntityId = targetId;
        if (buf.readBoolean()) {
            ownerId = new UUID(buf.readLong(), buf.readLong());
        }
    }
}
