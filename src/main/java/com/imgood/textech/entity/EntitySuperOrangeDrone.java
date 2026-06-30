package com.imgood.textech.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.imgood.textech.Config;
import com.imgood.textech.handler.HandlerSuperOrange;
import com.imgood.textech.items.ItemSuperOrange;

/**
 * Display names / 显示名称:
 * - EN: Super Orange (companion drone entity, no separate item name)
 * - ZH: 超能砂糖桔（伴随无人机实体，无独立物品名�?
 * Lang keys: item.orange.name (parent item)
 *
 * The original body is despawned and respawned every second near the player's head (follow refresh).
 * Clones track and damage hostile mobs; intercept drones handle projectiles.
 */
public class EntitySuperOrangeDrone extends EntityLivingBase {

    /** Re-spawn the original body every second (despawn + spawn) to follow the player reliably. */
    public static final int RESPAWN_INTERVAL_TICKS = 20;

    /** All drone entities force-despawn after 5 seconds. */
    public static final int MAX_LIFETIME_TICKS = 100;

    private static final double INTERCEPT_SCAN_RANGE = 8.0;
    private static final double INTERCEPT_MOVE_SPEED = 1.35D;
    private static final double INTERCEPT_PREDICT_FACTOR = 0.8D;
    private static final double INTERCEPT_CATCH_DIST = 1.5D;
    private static final double UPWARD_KNOCKBACK = 0.55D;
    private static final double FOLLOW_RADIUS = 3.0D;
    private static final int FOLLOW_RETARGET_INTERVAL = 20;
    private static final double HOSTILE_Y_OFFSET = 1.0D;
    private static final double MOVE_SPEED_FOLLOW = 0.65D;
    private static final double MOVE_SPEED_TRACK = 1.2D;
    private static final int STANDBY_TIME = 40;
    private static final int SPAWN_COOLDOWN = 10;
    private static final int HOSTILE_SCAN_INTERVAL = 5;
    private static final int INTERCEPT_SCAN_INTERVAL = 2;
    private String ownerUUID;
    private boolean isOriginal;
    private boolean isInterceptDrone;
    private int interceptTargetId;
    private int interceptLifetime;
    private int attackTargetId;
    private int attackDamageTimer;
    private int cloneSlot;
    private int lastOwnerDimension;
    private double followOffsetX;
    private double followOffsetZ;
    private int followRetargetTimer;
    private int standbyTimer;
    private int spawnCooldownTimer;
    private List<EntityLivingBase> cachedHostiles = new ArrayList<EntityLivingBase>();

    public EntitySuperOrangeDrone(World world) {
        super(world);
        this.setSize(0.5f, 0.5f);
        this.noClip = true;
        this.isImmuneToFire = true;
        this.isOriginal = true;
        this.isInterceptDrone = false;
        this.interceptTargetId = -1;
        this.interceptLifetime = 0;
        this.attackTargetId = -1;
        this.attackDamageTimer = 0;
        this.cloneSlot = 0;
        this.lastOwnerDimension = 0;
        this.followOffsetX = 0.0D;
        this.followOffsetZ = 0.0D;
        this.followRetargetTimer = FOLLOW_RETARGET_INTERVAL;
        this.standbyTimer = 0;
        this.spawnCooldownTimer = 0;
        this.cachedHostiles.clear();
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        // EntityLivingBase already registers maxHealth and movementSpeed; only override values.
        this.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .setBaseValue(0.5D);
    }

    /**
     * Server-side AI / combat logic runs first, then vanilla {@code super.onUpdate()} applies {@code motion}
     * and syncs position to clients through the entity tracker.
     */
    @Override
    public void onUpdate() {
        if (!worldObj.isRemote) {
            runServerTick();
        }
        super.onUpdate();
    }

    /** Skip gravity, suffocation, and vanilla mob AI; motion is driven by this class. */
    @Override
    public void onLivingUpdate() {
        ++this.entityAge;
    }

    @Override
    public void moveEntityWithHeading(float strafe, float forward) {}

    @Override
    protected void updateFallState(double distanceFallen, boolean onGround) {}

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    private void runServerTick() {
        if (ticksExisted >= MAX_LIFETIME_TICKS) {
            setDead();
            return;
        }
        if (isInterceptDrone) {
            updateInterceptDrone();
            return;
        }
        EntityPlayer owner = getOwner();
        if (owner == null || !isOwnerValid(owner)) {
            setDead();
            return;
        }
        checkOwnerDimension(owner);
        if (ticksExisted % HOSTILE_SCAN_INTERVAL == 0) {
            cachedHostiles = findHostileMobs(owner, Config.superOrangeDroneAttackRange);
        }
        boolean hasHostiles = !cachedHostiles.isEmpty();
        if (isOriginal) {
            updateOriginalDrone(owner, hasHostiles);
        } else {
            updateCloneDrone(owner, hasHostiles);
        }
    }

    private void updateOriginalDrone(EntityPlayer owner, boolean hasHostiles) {
        if (ticksExisted % INTERCEPT_SCAN_INTERVAL == 0) {
            spawnInterceptDronesForThreats(owner);
        }
        if (spawnCooldownTimer > 0) {
            spawnCooldownTimer--;
        }
        if (hasHostiles) {
            manageClones(cachedHostiles.size());
        }
        motionX = 0.0D;
        motionY = 0.0D;
        motionZ = 0.0D;
    }

    private void updateCloneDrone(EntityPlayer owner, boolean hasHostiles) {
        if (hasHostiles) {
            standbyTimer = 0;
        } else if (standbyTimer == 0) {
            standbyTimer = STANDBY_TIME;
        }
        if (standbyTimer > 0) {
            standbyTimer--;
            if (standbyTimer <= 0) {
                setDead();
                return;
            }
            moveFollowOwner(owner);
            return;
        }
        if (hasHostiles) {
            assignTarget(cachedHostiles, owner);
        } else {
            attackTargetId = -1;
            attackDamageTimer = 0;
        }
        if (attackTargetId > 0) {
            tryAttackCurrentTarget(Config.superOrangeDroneAttackRange);
        }
        EntityLivingBase target = resolveAttackTarget();
        if (target != null) {
            moveTrackHostile(target);
        } else {
            moveFollowOwner(owner);
        }
    }

    private void moveFollowOwner(EntityPlayer owner) {
        tickFollowRetarget();
        applyMotionToward(
            owner.posX + followOffsetX,
            owner.posY + owner.getEyeHeight() + Config.superOrangeDroneFollowHeight,
            owner.posZ + followOffsetZ,
            MOVE_SPEED_FOLLOW);
    }

    private void moveTrackHostile(EntityLivingBase hostile) {
        applyMotionToward(hostile.posX, hostile.posY + HOSTILE_Y_OFFSET, hostile.posZ, MOVE_SPEED_TRACK);
    }

    private void checkOwnerDimension(EntityPlayer owner) {
        if (owner != null && owner.isEntityAlive() && owner.dimension != lastOwnerDimension) {
            teleportToOwner(owner);
            lastOwnerDimension = owner.dimension;
        }
    }

    private void teleportToOwner(EntityPlayer owner) {
        if (owner != null && owner.isEntityAlive()) {
            placeNearPlayerHead(owner);
        }
    }

    /** Random horizontal offset within 3 blocks, at eye height (+ optional config offset). */
    private void placeNearPlayerHead(EntityPlayer player) {
        randomizeFollowOffsets();
        teleportTo(
            player.posX + followOffsetX,
            player.posY + player.getEyeHeight() + Config.superOrangeDroneFollowHeight,
            player.posZ + followOffsetZ);
    }

    private void teleportTo(double x, double y, double z) {
        motionX = 0.0D;
        motionY = 0.0D;
        motionZ = 0.0D;
        setPositionAndUpdate(x, y, z);
    }

    private void randomizeFollowOffsets() {
        double angle = worldObj.rand.nextDouble() * Math.PI * 2.0D;
        double radius = worldObj.rand.nextDouble() * FOLLOW_RADIUS;
        followOffsetX = Math.cos(angle) * radius;
        followOffsetZ = Math.sin(angle) * radius;
        followRetargetTimer = FOLLOW_RETARGET_INTERVAL;
    }

    private void tickFollowRetarget() {
        followRetargetTimer--;
        if (followRetargetTimer <= 0) {
            randomizeFollowOffsets();
        }
    }

    /**
     * Set per-tick velocity so vanilla {@code Entity.moveEntity} can apply it and the entity tracker syncs it.
     */
    private void applyMotionToward(double targetX, double targetY, double targetZ, double speed) {
        double dx = targetX - posX;
        double dy = targetY - posY;
        double dz = targetZ - posZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= 0.05D) {
            motionX = 0.0D;
            motionY = 0.0D;
            motionZ = 0.0D;
            return;
        }
        if (dist <= speed) {
            motionX = dx;
            motionY = dy;
            motionZ = dz;
            return;
        }
        double scale = speed / dist;
        motionX = dx * scale;
        motionY = dy * scale;
        motionZ = dz * scale;
    }

    private EntityLivingBase resolveAttackTarget() {
        if (attackTargetId <= 0) return null;
        Entity entity = worldObj.getEntityByID(attackTargetId);
        if (entity instanceof EntityLivingBase && !entity.isDead && entity instanceof IMob) {
            return (EntityLivingBase) entity;
        }
        return null;
    }

    private boolean isTargetWithinAttackRange(EntityLivingBase target, double attackRange) {
        return getDistanceSqToEntity(target) <= attackRange * attackRange;
    }

    @SuppressWarnings("unchecked")
    private void spawnInterceptDronesForThreats(EntityPlayer owner) {
        AxisAlignedBB scanArea = owner.boundingBox
            .expand(INTERCEPT_SCAN_RANGE, INTERCEPT_SCAN_RANGE, INTERCEPT_SCAN_RANGE);
        List<Entity> nearby = worldObj.getEntitiesWithinAABB(Entity.class, scanArea);
        for (Entity entity : nearby) {
            if (entity.isDead || entity == owner) continue;
            if (!HandlerSuperOrange.isGenericProjectile(entity)) continue;
            if (!HandlerSuperOrange.isThreateningOwner(entity, owner)) continue;
            if (hasInterceptDroneForProjectile(entity.getEntityId())) continue;
            spawnInterceptDrone(owner, entity);
        }
    }

    private boolean hasInterceptDroneForProjectile(int projectileId) {
        for (Object obj : worldObj.loadedEntityList) {
            if (obj instanceof EntitySuperOrangeDrone) {
                EntitySuperOrangeDrone drone = (EntitySuperOrangeDrone) obj;
                if (!drone.isDead && drone.isInterceptDrone && drone.interceptTargetId == projectileId) {
                    return true;
                }
            }
        }
        return false;
    }

    private void spawnInterceptDrone(EntityPlayer owner, Entity projectile) {
        EntitySuperOrangeDrone intercept = new EntitySuperOrangeDrone(worldObj);
        intercept.setOwnerUUID(ownerUUID);
        intercept.isOriginal = false;
        intercept.isInterceptDrone = true;
        intercept.interceptTargetId = projectile.getEntityId();
        intercept.interceptLifetime = 0;
        intercept.lastOwnerDimension = owner.dimension;
        intercept.teleportToOwner(owner);
        worldObj.spawnEntityInWorld(intercept);
    }

    private void updateInterceptDrone() {
        interceptLifetime++;
        Entity projectile = worldObj.getEntityByID(interceptTargetId);
        if (projectile == null || projectile.isDead) {
            setDead();
            return;
        }
        double predict = INTERCEPT_PREDICT_FACTOR;
        applyMotionToward(
            projectile.posX + projectile.motionX * predict,
            projectile.posY + projectile.motionY * predict,
            projectile.posZ + projectile.motionZ * predict,
            INTERCEPT_MOVE_SPEED);
        double dx = projectile.posX - posX;
        double dy = projectile.posY - posY;
        double dz = projectile.posZ - posZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean intercepted = dist < INTERCEPT_CATCH_DIST;
        if (intercepted) {
            projectile.setDead();
            worldObj.spawnParticle("flame", posX, posY, posZ, 0, 0.05, 0);
        }
        if (intercepted) {
            setDead();
        }
    }

    @SuppressWarnings("unchecked")
    private List<EntityLivingBase> findHostileMobs(EntityPlayer owner, double range) {
        List<EntityLivingBase> result = new ArrayList<EntityLivingBase>();
        AxisAlignedBB scanArea = owner.boundingBox.expand(range, range, range);
        List<EntityLivingBase> nearby = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, scanArea);
        for (EntityLivingBase entity : nearby) {
            if (entity.isDead) continue;
            if (!(entity instanceof IMob)) continue;
            result.add(entity);
        }
        return result;
    }

    private void assignTarget(List<EntityLivingBase> hostiles, EntityPlayer owner) {
        if (attackTargetId > 0) {
            Entity currentTarget = worldObj.getEntityByID(attackTargetId);
            if (currentTarget instanceof IMob && !currentTarget.isDead) {
                return;
            }
        }
        Set<Integer> claimed = getClaimedTargetIds();
        EntityLivingBase best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityLivingBase hostile : hostiles) {
            if (claimed.contains(hostile.getEntityId())) continue;
            double dist = owner.getDistanceSqToEntity(hostile);
            if (dist < bestDist) {
                bestDist = dist;
                best = hostile;
            }
        }
        if (best != null) {
            attackTargetId = best.getEntityId();
            attackDamageTimer = 0;
            standbyTimer = 0;
        } else {
            attackTargetId = -1;
        }
    }

    private Set<Integer> getClaimedTargetIds() {
        Set<Integer> ids = new HashSet<Integer>();
        for (Object obj : worldObj.loadedEntityList) {
            if (obj instanceof EntitySuperOrangeDrone) {
                EntitySuperOrangeDrone d = (EntitySuperOrangeDrone) obj;
                if (!d.isDead && !d.isInterceptDrone
                    && !d.isOriginal
                    && ownerUUID != null
                    && ownerUUID.equals(d.ownerUUID)
                    && d != this
                    && d.attackTargetId > 0) {
                    ids.add(d.attackTargetId);
                }
            }
        }
        return ids;
    }

    private int getAttackIntervalTicks() {
        int attacksPerSecond = Config.superOrangeDroneAttacksPerSecond;
        if (attacksPerSecond <= 0) attacksPerSecond = 1;
        return Math.max(1, 20 / attacksPerSecond);
    }

    private void tryAttackCurrentTarget(double attackRange) {
        EntityLivingBase target = resolveAttackTarget();
        if (target == null) {
            attackTargetId = -1;
            attackDamageTimer = 0;
            return;
        }
        if (!isTargetWithinAttackRange(target, attackRange)) {
            return;
        }
        attackDamageTimer++;
        if (attackDamageTimer < getAttackIntervalTicks()) return;
        attackDamageTimer = 0;
        float damage = (float) Config.superOrangeDroneAttackDamage;
        DamageSource source = new DamageSource("superOrangeDrone").setDamageBypassesArmor();
        target.attackEntityFrom(source, damage);
        applyUpwardKnockback(target);
        worldObj.spawnParticle("crit", target.posX, target.posY + target.height * 0.5, target.posZ, 0.1, 0.1, 0.1);
        worldObj.spawnParticle("magicCrit", target.posX, target.posY + target.height * 0.5, target.posZ, 0.1, 0.1, 0.1);
    }

    private void applyUpwardKnockback(EntityLivingBase target) {
        target.motionY = UPWARD_KNOCKBACK;
        target.velocityChanged = true;
        target.fallDistance = 0.0F;
        target.isAirBorne = true;
    }

    private void manageClones(int hostileCount) {
        if (spawnCooldownTimer > 0) return;
        int currentClones = countOwnedCloneDrones(worldObj, ownerUUID);
        int maxClones = Config.superOrangeDroneMaxClones;
        int desiredClones = Math.min(hostileCount, maxClones);
        int needed = desiredClones - currentClones;
        if (needed > 0) {
            for (int i = 0; i < needed && countOwnedCloneDrones(worldObj, ownerUUID) < maxClones; i++) {
                spawnClone(currentClones + i + 1);
                spawnCooldownTimer = SPAWN_COOLDOWN;
            }
        }
    }

    private void spawnClone(int slot) {
        EntitySuperOrangeDrone clone = new EntitySuperOrangeDrone(worldObj);
        clone.setOwnerUUID(ownerUUID);
        clone.isOriginal = false;
        clone.isInterceptDrone = false;
        clone.cloneSlot = slot;
        EntityPlayer owner = getOwner();
        if (owner != null) {
            clone.lastOwnerDimension = owner.dimension;
            clone.teleportToOwner(owner);
        } else {
            clone.teleportTo(posX, posY, posZ);
        }
        worldObj.spawnEntityInWorld(clone);
    }

    public void setOwnerUUID(String uuid) {
        if (uuid == null) {
            this.ownerUUID = "";
        } else {
            this.ownerUUID = uuid;
        }
    }

    public void bindOwner(EntityPlayer player) {
        if (player == null) return;
        setOwnerUUID(
            player.getUniqueID()
                .toString());
        lastOwnerDimension = player.dimension;
    }

    public String getOwnerUUID() {
        return ownerUUID;
    }

    private EntityPlayer getOwner() {
        if (ownerUUID == null || ownerUUID.isEmpty()) return null;
        for (Object obj : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) obj;
            if (!p.isDead && ownerUUID.equals(
                p.getUniqueID()
                    .toString())) {
                return p;
            }
        }
        return null;
    }

    private boolean isOwnerValid(EntityPlayer owner) {
        if (owner == null || owner.isDead) return false;
        return ItemSuperOrange.isDroneActiveForPlayer(owner);
    }

    public static int countOwnedCombatDrones(World world, String playerUUID) {
        int count = 0;
        for (Object obj : world.loadedEntityList) {
            if (obj instanceof EntitySuperOrangeDrone) {
                EntitySuperOrangeDrone d = (EntitySuperOrangeDrone) obj;
                if (!d.isDead && !d.isInterceptDrone && playerUUID.equals(d.ownerUUID)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countOwnedCloneDrones(World world, String playerUUID) {
        int count = 0;
        for (Object obj : world.loadedEntityList) {
            if (obj instanceof EntitySuperOrangeDrone) {
                EntitySuperOrangeDrone d = (EntitySuperOrangeDrone) obj;
                if (!d.isDead && !d.isInterceptDrone && !d.isOriginal && playerUUID.equals(d.ownerUUID)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countOwnedDrones(World world, String playerUUID) {
        return countOwnedCombatDrones(world, playerUUID);
    }

    public static boolean hasOriginalDrone(World world, String playerUUID) {
        for (Object obj : world.loadedEntityList) {
            if (obj instanceof EntitySuperOrangeDrone) {
                EntitySuperOrangeDrone d = (EntitySuperOrangeDrone) obj;
                if (!d.isDead && d.isOriginal && !d.isInterceptDrone && playerUUID.equals(d.ownerUUID)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void despawnOriginalDrone(World world, String playerUUID) {
        for (Object obj : world.loadedEntityList) {
            if (obj instanceof EntitySuperOrangeDrone) {
                EntitySuperOrangeDrone drone = (EntitySuperOrangeDrone) obj;
                if (!drone.isDead && drone.isOriginal
                    && !drone.isInterceptDrone
                    && playerUUID.equals(drone.getOwnerUUID())) {
                    drone.setDead();
                }
            }
        }
    }

    /** Despawn and immediately respawn the original body near the player's head (follow refresh). */
    public static void refreshOriginalDrone(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) return;
        if (!ItemSuperOrange.isDroneActiveForPlayer(player)) return;
        String uuid = player.getUniqueID()
            .toString();
        despawnOriginalDrone(player.worldObj, uuid);
        spawnForPlayer(player);
    }

    public static void spawnForPlayer(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) return;
        if (!ItemSuperOrange.isDroneActiveForPlayer(player)) return;
        String uuid = player.getUniqueID()
            .toString();
        if (hasOriginalDrone(player.worldObj, uuid)) return;

        EntitySuperOrangeDrone drone = new EntitySuperOrangeDrone(player.worldObj);
        drone.bindOwner(player);
        drone.placeNearPlayerHead(player);
        player.worldObj.spawnEntityInWorld(drone);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setBoolean("isOriginal", isOriginal);
        nbt.setBoolean("isInterceptDrone", isInterceptDrone);
        nbt.setInteger("interceptTargetId", interceptTargetId);
        nbt.setInteger("interceptLifetime", interceptLifetime);
        nbt.setInteger("attackTargetId", attackTargetId);
        nbt.setInteger("attackDamageTimer", attackDamageTimer);
        nbt.setInteger("cloneSlot", cloneSlot);
        nbt.setInteger("lastOwnerDimension", lastOwnerDimension);
        nbt.setDouble("followOffsetX", followOffsetX);
        nbt.setDouble("followOffsetZ", followOffsetZ);
        nbt.setInteger("followRetargetTimer", followRetargetTimer);
        nbt.setInteger("standbyTimer", standbyTimer);
        nbt.setInteger("spawnCooldownTimer", spawnCooldownTimer);
        if (ownerUUID != null) {
            nbt.setString("ownerUUID", ownerUUID);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        isOriginal = nbt.getBoolean("isOriginal");
        isInterceptDrone = nbt.getBoolean("isInterceptDrone");
        interceptTargetId = nbt.getInteger("interceptTargetId");
        interceptLifetime = nbt.getInteger("interceptLifetime");
        attackTargetId = nbt.getInteger("attackTargetId");
        attackDamageTimer = nbt.getInteger("attackDamageTimer");
        cloneSlot = nbt.getInteger("cloneSlot");
        lastOwnerDimension = nbt.getInteger("lastOwnerDimension");
        followOffsetX = nbt.hasKey("followOffsetX") ? nbt.getDouble("followOffsetX") : 0.0D;
        followOffsetZ = nbt.hasKey("followOffsetZ") ? nbt.getDouble("followOffsetZ") : 0.0D;
        followRetargetTimer = nbt.hasKey("followRetargetTimer") ? nbt.getInteger("followRetargetTimer")
            : FOLLOW_RETARGET_INTERVAL;
        standbyTimer = nbt.hasKey("standbyTimer") ? nbt.getInteger("standbyTimer") : 0;
        spawnCooldownTimer = nbt.hasKey("spawnCooldownTimer") ? nbt.getInteger("spawnCooldownTimer") : 0;
        if (nbt.hasKey("ownerUUID")) {
            ownerUUID = nbt.getString("ownerUUID");
        }
    }

    @Override
    public ItemStack getHeldItem() {
        return null;
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        return null;
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[0];
    }
}
