package com.imgood.textech.handler;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.loader.LoaderItem;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles giving the manual item to players when they first join a world.
 */
public class HandlerPlayerJoin {

    public static final String NBT_MANUAL_RECEIVED = "ADM_ManualReceived";

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote) return;
        if (!(event.entity instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.entity;
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(NBT_MANUAL_RECEIVED) || !data.getBoolean(NBT_MANUAL_RECEIVED)) {
            ItemStack manual = new ItemStack(LoaderItem.manual);
            if (manual == null) return;

            if (!player.inventory.addItemStackToInventory(manual)) {
                // Inventory full, drop at player's feet
                EntityItem dropped = new EntityItem(event.world, player.posX, player.posY, player.posZ, manual);
                dropped.delayBeforeCanPickup = 0;
                event.world.spawnEntityInWorld(dropped);
            }

            data.setBoolean(NBT_MANUAL_RECEIVED, true);
            AdvanceDataMonitor.LOG.info("Gave AdvanceDataMonitor manual to player {}", player.getDisplayName());
        }
    }
}
