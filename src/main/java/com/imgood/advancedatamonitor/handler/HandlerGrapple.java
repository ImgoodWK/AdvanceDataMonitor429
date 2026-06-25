package com.imgood.advancedatamonitor.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.world.ChunkEvent;

import com.imgood.advancedatamonitor.loader.LoaderBlock;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class HandlerGrapple {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isServer()) {
            return;
        }
        GrapplePlayerState.tick(event.player);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (event.entity.worldObj.isRemote) {
            return;
        }
        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.entity;
        if (!GrapplePlayerState.isAttached(player)) {
            return;
        }
        if (GrapplePlayerState.isTraveling(player)) {
            return;
        }
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.world.isRemote) {
            return;
        }
        Chunk chunk = event.getChunk();
        int dimId = event.world.provider.dimensionId;
        int baseX = chunk.xPosition * 16;
        int baseZ = chunk.zPosition * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    int wx = baseX + x;
                    int wz = baseZ + z;
                    if (event.world.getBlock(wx, y, wz) == LoaderBlock.grappleAnchor) {
                        GrappleNodeIndex.INSTANCE.addNode(dimId, wx, y, wz);
                    }
                }
            }
        }
    }
}
