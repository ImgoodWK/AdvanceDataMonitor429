package com.imgood.textech.client.worldmap;

import net.minecraft.client.Minecraft;

import com.imgood.textech.client.worldmap.dynmap.WorldMapDynmapClientFetcher;
import com.imgood.textech.client.worldmap.journeymap.WorldMapJourneyMapTileReader;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapTerrainCaptureResult;
import com.imgood.textech.webae.worldmap.WorldMapTerrainSourceId;
import com.imgood.textech.webae.worldmap.WorldMapTerrainSourcePriority;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side per-chunk terrain capture chain (Dynmap → JourneyMap → GL).
 */
@SideOnly(Side.CLIENT)
public final class WorldMapTerrainCaptureChainClient {

    private final WorldMapChunkGlRenderer renderer = new WorldMapChunkGlRenderer();

    private WorldMapTerrainCaptureChainClient() {}

    public static WorldMapTerrainCaptureResult captureTerrain(
        Minecraft mc,
        WorldMapView view,
        int dim,
        int chunkX,
        int chunkZ,
        int tilePx,
        WorldMapQualityTier glTier) {
        WorldMapQualityTier tier = glTier != null ? glTier : WorldMapQualityTier.fromTilePx(tilePx);
        for (WorldMapTerrainSourceId sourceId : WorldMapTerrainSourcePriority.resolved()) {
            WorldMapTerrainCaptureResult result = tryClientSource(mc, sourceId, view, dim, chunkX, chunkZ, tilePx, tier);
            if (result != null && result.isValid()) {
                return result;
            }
        }
        return null;
    }

    private static WorldMapTerrainCaptureResult tryClientSource(
        Minecraft mc,
        WorldMapTerrainSourceId sourceId,
        WorldMapView view,
        int dim,
        int chunkX,
        int chunkZ,
        int tilePx,
        WorldMapQualityTier glTier) {
        if (sourceId == WorldMapTerrainSourceId.DYNMAP) {
            return WorldMapDynmapClientFetcher.instance().capture(view, dim, chunkX, chunkZ, tilePx);
        }
        if (sourceId == WorldMapTerrainSourceId.JOURNEYMAP) {
            if (!WorldMapJourneyMapTileReader.isAvailable()) {
                return null;
            }
            byte[] jm = WorldMapJourneyMapTileReader.instance()
                .readChunkTerrain(dim, chunkX, chunkZ, tilePx);
            if (jm != null && jm.length > 0) {
                return new WorldMapTerrainCaptureResult(jm, WorldMapTerrainSourceId.JOURNEYMAP);
            }
            return null;
        }
        if (sourceId == WorldMapTerrainSourceId.CLIENT_GL) {
            if (mc == null) {
                return null;
            }
            WorldMapChunkGlRenderer renderer = new WorldMapChunkGlRenderer();
            byte[] gl = renderer.renderTerrain(mc, view != null ? view : WorldMapView.FLAT, glTier, dim, chunkX, chunkZ);
            if (gl != null && gl.length > 0) {
                return new WorldMapTerrainCaptureResult(gl, WorldMapTerrainSourceId.CLIENT_GL);
            }
        }
        return null;
    }
}
