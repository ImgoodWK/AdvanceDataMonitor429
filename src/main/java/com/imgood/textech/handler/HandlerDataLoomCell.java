package com.imgood.textech.handler;

import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;

import com.imgood.textech.items.cell.DataLoomCellIndex;

import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Keeps the data loom cell index in sync with loaded chunks —no AE hooks involved.
 */
public class HandlerDataLoomCell {

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.world.isRemote) {
            return;
        }
        Chunk chunk = event.getChunk();
        scanChunkTileEntities(chunk);
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.world.isRemote) {
            return;
        }
        Chunk chunk = event.getChunk();
        DataLoomCellIndex.INSTANCE.removeChunk(event.world.provider.dimensionId, chunk.xPosition, chunk.zPosition);
    }

    @SuppressWarnings("rawtypes")
    private void scanChunkTileEntities(Chunk chunk) {
        for (Object loaded : chunk.chunkTileEntityMap.values()) {
            if (loaded instanceof TileDrive) {
                DataLoomCellIndex.INSTANCE.registerDrive((TileDrive) loaded);
            } else if (loaded instanceof TileChest) {
                DataLoomCellIndex.INSTANCE.registerChest((TileChest) loaded);
            }
        }
    }
}
