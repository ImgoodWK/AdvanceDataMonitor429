package com.imgood.textech.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.imgood.textech.Config;
import com.imgood.textech.items.ItemSuperOrange;
import com.imgood.textech.utils.MatterBallClusterUtil;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles all Super Orange passive abilities:
 * instant mining, configurable drop multiplier with matter clusters, pickup merging, projectile immunity.
 */
public class HandlerSuperOrange {

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack held = event.entityPlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemSuperOrange) {
            event.newSpeed = 1000000.0F;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!Config.superOrangeProjectileImmunityEnabled) return;
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;

        if (!ItemSuperOrange.isDroneActiveForPlayer(player)) return;

        DamageSource source = event.source;
        if (isProjectileDamage(source)) {
            event.setCanceled(true);
            if (player.worldObj.isRemote) {
                player.worldObj
                    .spawnParticle("flame", player.posX, player.posY + player.getEyeHeight(), player.posZ, 0, 0.05, 0);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.harvester == null) return;
        ItemStack orange = ItemSuperOrange.findOrangeStack(event.harvester);
        if (!ItemSuperOrange.isMatterBallFeatureActive(orange)) return;

        int multiplier = ItemSuperOrange.getDropMultiplier(orange);

        List<ItemStack> allMultiplied = new ArrayList<>();
        for (ItemStack drop : event.drops) {
            if (drop == null) continue;
            ItemStack multiplied = drop.copy();
            long scaled = (long) drop.stackSize * (long) multiplier;
            multiplied.stackSize = scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
            allMultiplied.add(multiplied);
        }

        if (allMultiplied.isEmpty()) return;

        List<ItemStack> remainders = MatterBallClusterUtil.insertIntoPlayerClusters(event.harvester, allMultiplied);

        event.drops.clear();
        event.drops.addAll(remainders);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event.entityPlayer == null || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        if (!ItemSuperOrange.isPickupMatterBallActiveForPlayer(event.entityPlayer)) {
            return;
        }
        EntityItem entityItem = event.item;
        if (entityItem == null) {
            return;
        }
        ItemStack picked = entityItem.getEntityItem();
        if (picked == null || picked.stackSize <= 0 || MatterBallClusterUtil.isMatterCluster(picked)) {
            return;
        }

        ArrayList<ItemStack> batch = new ArrayList<>();
        batch.add(picked.copy());
        List<ItemStack> remainders = MatterBallClusterUtil.insertIntoPlayerClusters(event.entityPlayer, batch);
        if (remainders.isEmpty()) {
            event.setCanceled(true);
            entityItem.setDead();
            return;
        }
        ItemStack left = remainders.get(0);
        if (left != null && left.stackSize > 0 && left.stackSize < picked.stackSize) {
            entityItem.getEntityItem().stackSize = left.stackSize;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onItemToss(ItemTossEvent event) {
        if (event.player == null || event.player.worldObj.isRemote) {
            return;
        }
        if (!ItemSuperOrange.isDropMatterBallActiveForPlayer(event.player)) {
            return;
        }
        EntityItem entityItem = event.entityItem;
        if (entityItem == null) {
            return;
        }
        ItemStack tossed = entityItem.getEntityItem();
        if (tossed == null || tossed.stackSize <= 0 || MatterBallClusterUtil.isMatterCluster(tossed)) {
            return;
        }

        ArrayList<ItemStack> batch = new ArrayList<>();
        batch.add(tossed.copy());
        List<ItemStack> remainders = MatterBallClusterUtil.insertIntoPlayerClusters(event.player, batch);
        if (remainders.isEmpty()) {
            event.setCanceled(true);
            return;
        }
        ItemStack left = remainders.get(0);
        if (left != null && left.stackSize > 0 && left.stackSize < tossed.stackSize) {
            entityItem.getEntityItem().stackSize = left.stackSize;
        }
    }

    public static boolean hasSuperOrange(EntityPlayer player) {
        return ItemSuperOrange.hasSuperOrange(player);
    }

    public static boolean isProjectileDamage(DamageSource source) {
        if (source == null) return false;

        if (source.isProjectile()) return true;

        Entity sourceEntity = source.getSourceOfDamage();
        if (sourceEntity instanceof IProjectile) return true;

        if (sourceEntity != null) {
            String className = sourceEntity.getClass()
                .getName()
                .toLowerCase();
            if (className.contains("arrow") || className.contains("fireball")
                || className.contains("snowball")
                || className.contains("throw")
                || className.contains("bullet")
                || className.contains("projectile")
                || className.contains("bolt")
                || className.contains("dart")
                || className.contains("trident")
                || className.contains("shuriken")) {
                return true;
            }

            if (sourceEntity instanceof EntityArrow || sourceEntity instanceof EntityFireball
                || sourceEntity instanceof EntitySnowball
                || sourceEntity instanceof EntityThrowable
                || sourceEntity instanceof EntityFishHook) {
                return true;
            }
        }

        String damageType = source.getDamageType();
        if (damageType != null) {
            String lower = damageType.toLowerCase();
            if (lower.contains("arrow") || lower.contains("projectile")
                || lower.contains("fireball")
                || lower.contains("bullet")
                || lower.contains("bolt")
                || lower.contains("thrown")) {
                return true;
            }
        }

        return false;
    }

    public static boolean isGenericProjectile(Entity entity) {
        if (entity == null) return false;
        String className = entity.getClass()
            .getName()
            .toLowerCase();
        return className.contains("arrow") || className.contains("fireball")
            || className.contains("snowball")
            || className.contains("throw")
            || className.contains("bullet")
            || className.contains("projectile")
            || className.contains("bolt")
            || className.contains("dart")
            || entity instanceof IProjectile;
    }

    public static boolean isThreateningOwner(Entity projectile, EntityPlayer owner) {
        double px = projectile.posX + projectile.motionX;
        double py = projectile.posY + projectile.motionY;
        double pz = projectile.posZ + projectile.motionZ;

        double ox = owner.posX;
        double oy = owner.posY + owner.getEyeHeight();
        double oz = owner.posZ;

        double dx = ox - px;
        double dy = oy - py;
        double dz = oz - pz;
        double distToTrajectory = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double dotProduct = projectile.motionX * (ox - projectile.posX) + projectile.motionY * (oy - projectile.posY)
            + projectile.motionZ * (oz - projectile.posZ);

        return distToTrajectory < 2.5 && dotProduct > 0;
    }
}
