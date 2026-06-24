package com.imgood.advancedatamonitor.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.handler.GrapplePlayerState;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Display names / 显示名称:
 * - EN: Grapple Hook (travel carrier entity, no separate item name)
 * - ZH: 挂索器（滑行载体实体，无独立物品名）
 * Lang keys: item.grappleHook.name (parent item)
 *
 * Invisible carrier entity for grapple travel. The player mounts this entity and is moved along a straight line.
 */
public class EntityGrappleSlide extends Entity implements IEntityAdditionalSpawnData {

    private static final double ARRIVE_DISTANCE = 0.35D;

    private double endX;

    private double endY;

    private double endZ;

    private int targetBlockX;

    private int targetBlockY;

    private int targetBlockZ;

    private double totalDistance = 1.0D;

    private double travelSpeed = 1.0D;

    public EntityGrappleSlide(World world) {

        super(world);

        this.setSize(0.125F, 0.125F);

        this.noClip = true;

    }

    public EntityGrappleSlide(World world, EntityPlayer player, double startX, double startY, double startZ,
        double endX,

        double endY, double endZ, int targetBlockX, int targetBlockY, int targetBlockZ, double travelSpeed) {

        this(world);

        this.endX = endX;

        this.endY = endY;

        this.endZ = endZ;

        this.targetBlockX = targetBlockX;

        this.targetBlockY = targetBlockY;

        this.targetBlockZ = targetBlockZ;

        this.travelSpeed = travelSpeed;

        this.setPosition(startX, startY, startZ);

        this.totalDistance = Math.max(

            0.001D,

            MathHelper.sqrt_double(

                (endX - startX) * (endX - startX) + (endY - startY) * (endY - startY)

                    + (endZ - startZ) * (endZ - startZ)));

        applyMotionToward(endX, endY, endZ, this.travelSpeed);

    }

    @Override

    protected void entityInit() {}

    @Override

    public void onUpdate() {

        super.onUpdate();

        Entity rider = this.riddenByEntity;

        if (!(rider instanceof EntityPlayer)) {

            if (!worldObj.isRemote) {

                this.setDead();

            }

            return;

        }

        EntityPlayer player = (EntityPlayer) rider;

        if (worldObj.isRemote) {

            tickClientSlide(player);

            return;

        }

        int chunkX = MathHelper.floor_double(posX) >> 4;

        int chunkZ = MathHelper.floor_double(posZ) >> 4;

        if (!worldObj.getChunkProvider()

            .chunkExists(chunkX, chunkZ)) {

            GrapplePlayerState.detach(player);

            this.setDead();

            return;

        }

        double dx = endX - posX;

        double dy = endY - posY;

        double dz = endZ - posZ;

        double remaining = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);

        if (remaining <= ARRIVE_DISTANCE) {

            GrapplePlayerState.completeTravel(player, targetBlockX, targetBlockY, targetBlockZ);

            this.setDead();

            return;

        }

        applyMotionToward(endX, endY, endZ, travelSpeed);

        this.posX += this.motionX;

        this.posY += this.motionY;

        this.posZ += this.motionZ;

        this.setPosition(this.posX, this.posY, this.posZ);

        player.fallDistance = 0.0F;

        GrapplePlayerState.updateTravelProgress(player, getTravelProgress());

    }

    @SideOnly(Side.CLIENT)

    private void tickClientSlide(EntityPlayer player) {

        double dx = endX - posX;

        double dy = endY - posY;

        double dz = endZ - posZ;

        double remaining = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);

        if (remaining <= ARRIVE_DISTANCE) {

            return;

        }

        applyMotionToward(endX, endY, endZ, travelSpeed);

        this.posX += this.motionX;

        this.posY += this.motionY;

        this.posZ += this.motionZ;

        player.fallDistance = 0.0F;

    }

    public float getTravelProgress() {

        double dx = endX - posX;

        double dy = endY - posY;

        double dz = endZ - posZ;

        double remaining = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);

        float progress = (float) ((totalDistance - remaining) / totalDistance);

        if (progress < 0.0F) {

            return 0.0F;

        }

        if (progress > 1.0F) {

            return 1.0F;

        }

        return progress;

    }

    private void applyMotionToward(double tx, double ty, double tz, double speed) {

        double dx = tx - posX;

        double dy = ty - posY;

        double dz = tz - posZ;

        double len = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);

        if (len < 0.001D) {

            this.motionX = 0.0D;

            this.motionY = 0.0D;

            this.motionZ = 0.0D;

            return;

        }

        double scale = speed / len;

        if (scale > 1.0D) {

            scale = 1.0D;

        }

        this.motionX = dx * scale;

        this.motionY = dy * scale;

        this.motionZ = dz * scale;

    }

    @Override

    public void writeSpawnData(ByteBuf buffer) {

        buffer.writeDouble(endX);

        buffer.writeDouble(endY);

        buffer.writeDouble(endZ);

        buffer.writeInt(targetBlockX);

        buffer.writeInt(targetBlockY);

        buffer.writeInt(targetBlockZ);

        buffer.writeDouble(totalDistance);

        buffer.writeDouble(travelSpeed);

    }

    @Override

    public void readSpawnData(ByteBuf buffer) {

        endX = buffer.readDouble();

        endY = buffer.readDouble();

        endZ = buffer.readDouble();

        targetBlockX = buffer.readInt();

        targetBlockY = buffer.readInt();

        targetBlockZ = buffer.readInt();

        totalDistance = buffer.readDouble();

        travelSpeed = buffer.readDouble();

    }

    @Override

    public boolean shouldRiderSit() {

        return false;

    }

    @Override

    public double getMountedYOffset() {

        return 0.0D;

    }

    @Override

    public boolean isInvisible() {

        return true;

    }

    @Override

    public boolean canRenderOnFire() {

        return false;

    }

    @Override

    public boolean isPushedByWater() {

        return false;

    }

    @Override

    public boolean canBeCollidedWith() {

        return false;

    }

    @Override

    public float getCollisionBorderSize() {

        return 0.0F;

    }

    @SideOnly(Side.CLIENT)

    @Override

    public float getShadowSize() {

        return 0.0F;

    }

    @Override

    protected void readEntityFromNBT(NBTTagCompound tag) {}

    @Override

    protected void writeEntityToNBT(NBTTagCompound tag) {}

    public void dismountPlayer(EntityPlayerMP player) {

        if (player != null && player.ridingEntity == this) {

            player.mountEntity(null);

        }

    }

}
