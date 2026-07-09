package com.imgood.textech.webae.worldmap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.imgood.textech.webae.worldmap.dynmap.WorldMapDynmapChunkCropper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Per-chunk terrain capture chain for server-side resolution (Dynmap FS + JourneyMap FS).
 * Client capture uses {@code com.imgood.textech.client.worldmap.WorldMapTerrainCaptureChainClient}.
 */
public final class WorldMapTerrainCaptureChain {

    private WorldMapTerrainCaptureChain() {}

    public static WorldMapTerrainCaptureResult captureTerrain(
        WorldMapView view,
        int dim,
        int chunkX,
        int chunkZ,
        int tilePx,
        Side side) {
        if (side == Side.CLIENT) {
            return null;
        }
        String viewId = view != null ? view.id : WorldMapView.FLAT.id;
        for (WorldMapTerrainSourceId sourceId : WorldMapTerrainSourcePriority.resolved()) {
            WorldMapTerrainCaptureResult result = tryServerSource(sourceId, viewId, dim, chunkX, chunkZ, tilePx);
            if (result != null && result.isValid()) {
                return result;
            }
        }
        return null;
    }

    private static WorldMapTerrainCaptureResult tryServerSource(
        WorldMapTerrainSourceId sourceId,
        String viewId,
        int dim,
        int chunkX,
        int chunkZ,
        int tilePx) {
        if (sourceId == WorldMapTerrainSourceId.DYNMAP) {
            byte[] dynmap = WorldMapDynmapChunkCropper.cropChunkPng(viewId, dim, chunkX, chunkZ, tilePx);
            if (dynmap != null && dynmap.length > 0) {
                return new WorldMapTerrainCaptureResult(dynmap, WorldMapTerrainSourceId.DYNMAP);
            }
            return null;
        }
        if (sourceId == WorldMapTerrainSourceId.JOURNEYMAP) {
            ServerWorldContext ctx = resolveServerWorldContext();
            if (ctx == null) {
                return null;
            }
            byte[] jm = WorldMapJourneyMapFsReader.instance()
                .readChunkTerrain(ctx.multiplayer, ctx.worldName, dim, chunkX, chunkZ, tilePx);
            if (jm != null && jm.length > 0) {
                return new WorldMapTerrainCaptureResult(jm, WorldMapTerrainSourceId.JOURNEYMAP);
            }
            return null;
        }
        return null;
    }

    private static ServerWorldContext resolveServerWorldContext() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return null;
        }
        boolean multiplayer = !server.isSinglePlayer();
        String worldName = "world";
        if (server.worldServers != null && server.worldServers.length > 0 && server.worldServers[0] != null) {
            WorldServer overworld = server.worldServers[0];
            if (overworld.getWorldInfo() != null && overworld.getWorldInfo().getWorldName() != null) {
                worldName = overworld.getWorldInfo().getWorldName();
            }
        }
        ServerWorldContext ctx = new ServerWorldContext();
        ctx.multiplayer = multiplayer;
        ctx.worldName = worldName;
        return ctx;
    }

    private static final class ServerWorldContext {

        boolean multiplayer;
        String worldName;
    }
}
