package com.imgood.textech.handler;

import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;

import com.imgood.textech.webae.context.NetworkRegistry;
import com.imgood.textech.webae.context.TileEntityIndex;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Keeps {@link TileEntityIndex} and {@link NetworkRegistry} in sync with chunk load/unload.
 */
public class HandlerWebTileEntityIndex {

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        Chunk chunk = event.getChunk();
        int dim = event.world.provider.dimensionId;
        TileEntityIndex.onChunkLoad(chunk, dim);
        NetworkRegistry.onChunkLoad(chunk, dim);
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        Chunk chunk = event.getChunk();
        int dim = event.world.provider.dimensionId;
        NetworkRegistry.onChunkUnload(chunk, dim);
        TileEntityIndex.onChunkUnload(chunk, dim);
    }
}
