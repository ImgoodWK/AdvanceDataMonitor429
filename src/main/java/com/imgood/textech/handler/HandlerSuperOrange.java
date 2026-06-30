package com.imgood.textech.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.imgood.textech.Config;
import com.imgood.textech.items.ItemSuperOrange;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import fox.spiteful.avaritia.items.ItemMatterCluster;
import fox.spiteful.avaritia.items.ItemStackWrapper;

/**
 * Handles all Super Orange passive abilities:
 * <ul>
 * <li>Instant mining of any block when ItemSuperOrange is held</li>
 * <li>Configurable block drop multiplier, auto-collected into Avaritia Matter Cluster</li>
 * <li>Configurable projectile damage immunity</li>
 * </ul>
 */
public class HandlerSuperOrange {

    /**
     * Maximum total item count a single Matter Cluster can hold.
     * Mirrors {@code ItemMatterCluster.capacity} —the Avaritia cluster
     * capacity is 64 × 256 = 16384.
     */
    private static final int MATTER_CLUSTER_CAPACITY = 16384;

    /**
     * Allow instant break speed when holding ItemSuperOrange (modded-block fallback).
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack held = event.entityPlayer.getHeldItem();
        if (held != null && held.getItem() instanceof ItemSuperOrange) {
            event.newSpeed = 1000000.0F;
        }
    }

    /**
     * Handle projectile immunity when enabled in config.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
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

    /**
     * Handle configurable block drop multiplier for players with ItemSuperOrange.
     * Drops are automatically collected into an existing Avaritia Matter Cluster,
     * or a new cluster is created and added to the inventory when none exists.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.harvester == null) return;
        if (!ItemSuperOrange.isMatterBallActiveForPlayer(event.harvester)) return;

        int multiplier = Math.max(1, Config.superOrangeDropMultiplier);

        List<ItemStack> allMultiplied = new ArrayList<>();
        for (ItemStack drop : event.drops) {
            if (drop == null) continue;
            ItemStack multiplied = drop.copy();
            long scaled = (long) drop.stackSize * (long) multiplier;
            multiplied.stackSize = scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
            allMultiplied.add(multiplied);
        }

        if (allMultiplied.isEmpty()) return;

        List<ItemStack> remainders = insertIntoMatterClusters(event.harvester, allMultiplied);

        event.drops.clear();
        event.drops.addAll(remainders);
    }

    /**
     * Insert a list of ItemStacks into Matter Clusters in the player's inventory.
     * If no cluster exists or existing ones are full, new clusters are created.
     * Returns items that couldn't be inserted.
     */
    private List<ItemStack> insertIntoMatterClusters(EntityPlayer player, List<ItemStack> toInsert) {
        ArrayList<ItemStack> workingList = new ArrayList<>(toInsert);

        InventoryPlayer inv = player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemMatterCluster)) continue;

            workingList = insertIntoCluster(stack, workingList);
            if (workingList.isEmpty()) return workingList;
        }

        while (!workingList.isEmpty()) {
            ItemStack newCluster = createClusterFromDrops(workingList);
            if (newCluster == null) break;

            if (!tryAddToInventory(player, newCluster)) {
                workingList.add(newCluster);
                break;
            }

            workingList = compactStacks(workingList);
        }

        return workingList;
    }

    private ArrayList<ItemStack> compactStacks(ArrayList<ItemStack> items) {
        ArrayList<ItemStack> remainders = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.stackSize > 0) {
                remainders.add(item);
            }
        }
        return remainders;
    }

    private ArrayList<ItemStack> insertIntoCluster(ItemStack cluster, ArrayList<ItemStack> items) {
        Map<ItemStackWrapper, Integer> data = ItemMatterCluster.getClusterData(cluster);
        if (data == null) {
            data = new HashMap<>();
        }

        int currentTotal = 0;
        for (int count : data.values()) {
            currentTotal += count;
        }
        int capacity = MATTER_CLUSTER_CAPACITY;

        for (int idx = 0; idx < items.size(); idx++) {
            ItemStack item = items.get(idx);
            if (item == null || item.stackSize <= 0) continue;

            int space = capacity - currentTotal;
            if (space <= 0) break;

            int toAdd = Math.min(item.stackSize, space);
            ItemStackWrapper wrapper = new ItemStackWrapper(item);

            Integer existing = data.get(wrapper);
            if (existing != null) {
                data.put(wrapper, existing + toAdd);
            } else {
                data.put(wrapper, toAdd);
            }
            currentTotal += toAdd;

            if (toAdd >= item.stackSize) {
                items.set(idx, null);
            } else {
                item.stackSize -= toAdd;
            }
        }

        int newTotal = 0;
        for (int cnt : data.values()) newTotal += cnt;
        ItemMatterCluster.setClusterData(cluster, data, newTotal);

        return compactStacks(items);
    }

    private ItemStack createClusterFromDrops(List<ItemStack> items) {
        Map<ItemStackWrapper, Integer> data = new HashMap<>();
        int total = 0;
        int capacity = MATTER_CLUSTER_CAPACITY;

        for (ItemStack item : items) {
            if (item == null || item.stackSize <= 0) continue;
            int toAdd = Math.min(item.stackSize, capacity - total);
            if (toAdd <= 0) break;

            ItemStackWrapper wrapper = new ItemStackWrapper(item);
            Integer existing = data.get(wrapper);
            if (existing != null) {
                data.put(wrapper, existing + toAdd);
            } else {
                data.put(wrapper, toAdd);
            }
            total += toAdd;
            item.stackSize -= toAdd;
        }

        if (data.isEmpty()) return null;

        return ItemMatterCluster.makeCluster(data);
    }

    /**
     * Attempt to add an ItemStack to the player's inventory.
     * Returns true when the entire stack was added.
     */
    private boolean tryAddToInventory(EntityPlayer player, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return true;

        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack slot = player.inventory.mainInventory[i];
            if (slot != null && slot.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(slot, stack)) {
                int space = slot.getMaxStackSize() - slot.stackSize;
                int add = Math.min(space, stack.stackSize);
                if (add > 0) {
                    slot.stackSize += add;
                    stack.stackSize -= add;
                }
                if (stack.stackSize <= 0) return true;
            }
        }

        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            if (player.inventory.mainInventory[i] == null) {
                player.inventory.mainInventory[i] = stack.copy();
                return true;
            }
        }

        return stack.stackSize <= 0;
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
