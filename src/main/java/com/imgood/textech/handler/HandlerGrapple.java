package com.imgood.textech.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.world.ChunkEvent;

import com.imgood.textech.tileentity.TileEntityGrappleAnchor;

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
        scanChunkForGrappleAnchors(event.getChunk(), event.world.provider.dimensionId);
    }

    @SuppressWarnings("rawtypes")
    private static void scanChunkForGrappleAnchors(Chunk chunk, int dimId) {
        for (Object loaded : chunk.chunkTileEntityMap.values()) {
            if (loaded instanceof TileEntityGrappleAnchor) {
                TileEntityGrappleAnchor anchor = (TileEntityGrappleAnchor) loaded;
                GrappleNodeIndex.INSTANCE.addNode(dimId, anchor.xCoord, anchor.yCoord, anchor.zCoord);
            }
        }
    }
}
