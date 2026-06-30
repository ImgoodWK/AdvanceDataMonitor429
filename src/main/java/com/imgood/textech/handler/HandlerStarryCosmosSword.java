package com.imgood.textech.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.imgood.textech.items.ItemHolyJudgment;
import com.imgood.textech.items.ItemStarryCosmosSword;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Left-click skills: Empyrean Holy Judgment line slash; Holy Judgment area true damage (no giant stab).
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
        if (!isHoldingStarrySword(event.entityPlayer)) {
            return;
        }
        trySpawnLeftClickSkill(event.entityPlayer);
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
        if (!isHoldingStarrySword(player)) {
            return;
        }
        trySpawnLeftClickSkill(player);
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

        if (!isHoldingStarrySword(player)) {
            lastSwingProgress.remove(player.getUniqueID());
            return;
        }

        int current = player.swingProgressInt;
        Integer previous = lastSwingProgress.get(player.getUniqueID());
        if (previous != null && previous == -1 && current == 0 && player.isSwingInProgress) {
            trySpawnLeftClickSkill(player);
        }
        lastSwingProgress.put(player.getUniqueID(), current);
    }

    private void trySpawnLeftClickSkill(EntityPlayer player) {
        if (!isHoldingStarrySword(player)) {
            return;
        }
        int now = player.ticksExisted;
        UUID id = player.getUniqueID();
        Integer last = lastWaveTick.get(id);
        if (last != null && now - last < SLASH_COOLDOWN_TICKS) {
            return;
        }
        lastWaveTick.put(id, now);
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemHolyJudgment) {
            ItemHolyJudgment.spawnLeftClickAreaJudgment(player);
        } else {
            ItemStarryCosmosSword.spawnLineSlash(player);
        }
    }

    private boolean isHoldingStarrySword(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() instanceof ItemStarryCosmosSword;
    }
}
