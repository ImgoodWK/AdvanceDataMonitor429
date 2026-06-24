package com.imgood.advancedatamonitor.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.imgood.advancedatamonitor.items.ItemStarryCosmosSword;
import com.imgood.advancedatamonitor.loader.LoaderItem;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Spawns line slash when the player attacks with the Starry Cosmos Sword.
 */
public class HandlerStarryCosmosSword {

    private static final int SLASH_COOLDOWN_TICKS = 5;

    private final Map<UUID, Integer> lastSwingProgress = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> lastWaveTick = new HashMap<UUID, Integer>();

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.entityPlayer.worldObj.isRemote) {
            return;
        }
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        trySpawnSlash(event.entityPlayer);
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.source == null || !(event.source.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.source.getEntity();
        if (player.worldObj.isRemote) {
            return;
        }
        if (!isHoldingSword(player)) {
            return;
        }
        trySpawnSlash(player);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (player.worldObj.isRemote) {
            return;
        }

        if (!isHoldingSword(player)) {
            lastSwingProgress.remove(player.getUniqueID());
            return;
        }

        int current = player.swingProgressInt;
        Integer previous = lastSwingProgress.get(player.getUniqueID());
        if (previous != null && previous == -1 && current == 0 && player.isSwingInProgress) {
            trySpawnSlash(player);
        }
        lastSwingProgress.put(player.getUniqueID(), current);
    }

    private void trySpawnSlash(EntityPlayer player) {
        if (!isHoldingSword(player)) {
            return;
        }
        int now = player.ticksExisted;
        UUID id = player.getUniqueID();
        Integer last = lastWaveTick.get(id);
        if (last != null && now - last < SLASH_COOLDOWN_TICKS) {
            return;
        }
        lastWaveTick.put(id, now);
        ItemStarryCosmosSword.spawnLineSlash(player);
    }

    private boolean isHoldingSword(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() == LoaderItem.starryCosmosSword;
    }
}
