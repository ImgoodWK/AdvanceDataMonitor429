package com.imgood.advancedatamonitor.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.items.ItemOrange;

/**
 * Companion drone — follows owner, attacks hostile mobs, stays within player's view
 * while avoiding crosshair. Placeholder model: simple white cube.
 */
public class EntityDrone extends EntityCreature {

    private static final double FOLLOW_DISTANCE = 3.0;
    private static final int ATTACK_COOLDOWN_TICKS = 10;
    private static final float MOVE_SPEED = 0.35f;
    private static final float ATTACK_DAMAGE = 8.0f;

    private int attackCooldown;
    private String ownerUUID;

    public EntityDrone(World world) {
        super(world);
        this.setSize(0.5f, 0.5f);
        this.noClip = false;
        this.attackCooldown = 0;

        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIFollowOwner(this, FOLLOW_DISTANCE));
        this.tasks.addTask(2, new EntityAIAttackOnCollide(this, 1.0, false));
        this.tasks.addTask(3, new EntityAILookIdle(this));

        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityMob.class, 0, true));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(20.0);
        this.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .setBaseValue(MOVE_SPEED);
        this.getEntityAttribute(SharedMonsterAttributes.attackDamage)
            .setBaseValue(ATTACK_DAMAGE);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (attackCooldown > 0) {
            attackCooldown--;
        }
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        if (attackCooldown > 0) return false;
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.attackDamage)
            .getAttributeValue();
        return target.attackEntityFrom(DamageSource.causeMobDamage(this), damage);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected String getLivingSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return "dig.stone";
    }

    @Override
    protected String getDeathSound() {
        return "dig.stone";
    }

    @Override
    protected float getSoundVolume() {
        return 0.4f;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setInteger("attackCooldown", attackCooldown);
        if (ownerUUID != null) {
            nbt.setString("ownerUUID", ownerUUID);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        attackCooldown = nbt.getInteger("attackCooldown");
        if (nbt.hasKey("ownerUUID")) {
            ownerUUID = nbt.getString("ownerUUID");
        }
    }

    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {
        if (!this.worldObj.isRemote) {
            int xp = this.rand.nextInt(3) + 1;
            this.worldObj.spawnEntityInWorld(new EntityXPOrb(this.worldObj, this.posX, this.posY, this.posZ, xp));
        }
    }

    /**
     * Checks if owner still has an ItemOrange. If not, drone self-destructs.
     */
    private boolean isOwnerValid(EntityPlayer owner) {
        if (owner == null || owner.isDead) return false;
        for (int i = 0; i < owner.inventory.getSizeInventory(); i++) {
            ItemStack stack = owner.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemOrange) {
                return true;
            }
        }
        return false;
    }

    // ──────────── Owner UUID ────────────

    public void setOwnerUUID(String uuid) {
        this.ownerUUID = uuid;
    }

    public String getOwnerUUID() {
        return ownerUUID;
    }

    // ──────────── Spawn / Despawn helpers ────────────

    /**
     * Count how many drones the given player already owns.
     */
    public static int countOwnedDrones(World world, String playerUUID) {
        int count = 0;
        for (Object obj : world.loadedEntityList) {
            if (obj instanceof EntityDrone) {
                EntityDrone d = (EntityDrone) obj;
                if (!d.isDead && playerUUID.equals(d.ownerUUID)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Spawn a drone for the given player at their position.
     */
    public static void spawnForPlayer(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) return;
        String uuid = player.getUniqueID()
            .toString();
        if (countOwnedDrones(player.worldObj, uuid) >= 1) return;

        EntityDrone drone = new EntityDrone(player.worldObj);
        drone.setOwnerUUID(uuid);
        drone.setPosition(player.posX, player.posY + player.getEyeHeight() + 0.5, player.posZ);
        player.worldObj.spawnEntityInWorld(drone);
    }

    // ─────────────────────────────────────────────────────────
    // AI: Follow owner while avoiding crosshair
    // ─────────────────────────────────────────────────────────

    private static class EntityAIFollowOwner extends EntityAIBase {

        private final EntityDrone drone;
        private final double followDistSq;
        private EntityPlayer owner;
        private int recheckTimer;

        public EntityAIFollowOwner(EntityDrone drone, double followDistance) {
            this.drone = drone;
            this.followDistSq = followDistance * followDistance;
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            if (drone.ownerUUID == null || drone.ownerUUID.isEmpty()) {
                drone.setDead();
                return false;
            }
            // Find the specific owner by UUID
            EntityPlayer candidate = findOwnerByUUID(drone.ownerUUID);
            if (candidate == null || !drone.isOwnerValid(candidate)) {
                drone.setDead();
                return false;
            }
            this.owner = candidate;
            double dx = drone.posX - owner.posX;
            double dy = drone.posY - owner.posY;
            double dz = drone.posZ - owner.posZ;
            return dx * dx + dy * dy + dz * dz > followDistSq;
        }

        private EntityPlayer findOwnerByUUID(String uuid) {
            for (Object obj : drone.worldObj.playerEntities) {
                EntityPlayer p = (EntityPlayer) obj;
                if (!p.isDead && uuid.equals(
                    p.getUniqueID()
                        .toString())) {
                    return p;
                }
            }
            return null;
        }

        @Override
        public void startExecuting() {
            recheckTimer = 0;
        }

        @Override
        public boolean continueExecuting() {
            return owner != null && drone.getDistanceSqToEntity(owner) > followDistSq && drone.isOwnerValid(owner);
        }

        @Override
        public void resetTask() {
            owner = null;
        }

        @Override
        public void updateTask() {
            if (owner == null) return;

            // Position the drone in front of the player, offset above and to the side
            float playerYaw = owner.rotationYaw;
            float playerPitch = owner.rotationPitch;

            // Target position: ~1.5 blocks in front of player's face, slightly above
            double offsetX = -MathHelper.sin(playerYaw * (float) Math.PI / 180.0f) * 1.5;
            double offsetZ = MathHelper.cos(playerYaw * (float) Math.PI / 180.0f) * 1.5;
            double offsetY = 0.6;

            // Float to the side in alternate pattern
            double sideOffset = Math.cos(drone.ticksExisted * 0.02) * 0.5;
            offsetX += MathHelper.cos(playerYaw * (float) Math.PI / 180.0f) * sideOffset;
            offsetZ += MathHelper.sin(playerYaw * (float) Math.PI / 180.0f) * sideOffset;

            double targetX = owner.posX + offsetX;
            double targetY = owner.posY + owner.getEyeHeight() + offsetY;
            double targetZ = owner.posZ + offsetZ;

            // Smooth movement toward target
            double dx = targetX - drone.posX;
            double dy = targetY - drone.posY;
            double dz = targetZ - drone.posZ;

            drone.motionX = dx * 0.15;
            drone.motionY = dy * 0.15 + 0.02;
            drone.motionZ = dz * 0.15;

            // Face owner
            drone.getLookHelper()
                .setLookPosition(owner.posX, owner.posY + owner.getEyeHeight(), owner.posZ, 30.0f, 30.0f);

            // Keep drone roughly within FOV and avoid crosshair
            if (recheckTimer++ % 20 == 0) {
                adjustPositionForFov(owner);
            }
        }

        /**
         * Fine-tune position to stay within player's FOV and avoid crosshair center.
         */
        private void adjustPositionForFov(EntityPlayer owner) {
            float yaw = owner.rotationYaw;
            float pitch = owner.rotationPitch;

            // Check if drone is near crosshair
            double droneDirX = drone.posX - owner.posX;
            double droneDirY = (drone.posY - (owner.posY + owner.getEyeHeight()));
            double droneDirZ = drone.posZ - owner.posZ;
            double droneDist = Math.sqrt(droneDirX * droneDirX + droneDirY * droneDirY + droneDirZ * droneDirZ);

            if (droneDist < 0.5) return;

            double droneDirXN = droneDirX / droneDist;
            double droneDirZN = droneDirZ / droneDist;

            float lookDirX = -MathHelper.sin(yaw * (float) Math.PI / 180.0f);
            float lookDirZ = MathHelper.cos(yaw * (float) Math.PI / 180.0f);

            double dotForward = droneDirXN * lookDirX + droneDirZN * lookDirZ;

            // If drone is too close to crosshair (within ~15 degrees), nudge it sideways
            if (dotForward > 0.965) {
                float rightX = -MathHelper.sin((yaw + 90) * (float) Math.PI / 180.0f);
                float rightZ = MathHelper.cos((yaw + 90) * (float) Math.PI / 180.0f);
                double nudge = drone.ticksExisted % 40 < 20 ? 0.8 : -0.8;
                drone.posX += rightX * nudge * 0.1;
                drone.posZ += rightZ * nudge * 0.1;
            }
        }
    }
}
