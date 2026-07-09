package com.imgood.textech.client.worldmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWorldMapCaptureJob;
import com.imgood.textech.webae.network.PacketWorldMapCaptureOffer;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotTileUpload;
import com.imgood.textech.webae.worldmap.WorldMapQualityTier;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;
import com.imgood.textech.webae.worldmap.WorldMapView;
import com.imgood.textech.client.worldmap.journeymap.WorldMapJourneyMapTileReader;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side worker for manual world map snapshot capture (terrain + AE layers).
 */
@SideOnly(Side.CLIENT)
public final class WorldMapSnapshotCaptureWorker {

    private static final WorldMapSnapshotCaptureWorker INSTANCE = new WorldMapSnapshotCaptureWorker();
    private static final int CHUNKS_PER_TICK = 2;

    private final Deque<CaptureChunk> queue = new ArrayDeque<CaptureChunk>();
    private final WorldMapChunkGlRenderer renderer = new WorldMapChunkGlRenderer();

    private String ownerUuid;
    private int networkId;
    private int snapshotVersion;
    private int tilePx = 128;
    private String source = "client_gl";
    private int totalChunks;
    private int completedChunks;
    private boolean active;

    private WorldMapSnapshotCaptureWorker() {}

    public static WorldMapSnapshotCaptureWorker instance() {
        return INSTANCE;
    }

    public void onCaptureOffer(PacketWorldMapCaptureOffer offer) {
        if (offer == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.AQUA + "[WebAE] Map snapshot upload requested. "
                    + EnumChatFormatting.YELLOW + "/admweb worldmap accept "
                    + offer.requestId));
        }
    }

    public void startJob(PacketWorldMapCaptureJob job) {
        if (job == null || job.chunks == null || job.chunks.isEmpty()) {
            return;
        }
        queue.clear();
        ownerUuid = job.ownerUuid;
        networkId = job.networkId;
        snapshotVersion = job.snapshotVersion;
        tilePx = job.tilePx > 0 ? job.tilePx : 128;
        totalChunks = job.chunks.size();
        completedChunks = 0;
        active = true;
        source = WorldMapJourneyMapTileReader.isAvailable() ? "journeymap" : "client_gl";
        for (String entry : job.chunks) {
            int[] parsed = parseChunkEntry(entry);
            if (parsed != null) {
                queue.offerLast(new CaptureChunk(parsed[0], parsed[1], parsed[2]));
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active || queue.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        int budget = Math.max(1, Config.webWorldMapClientHdBudgetPerTick);
        if (budget > CHUNKS_PER_TICK) {
            budget = CHUNKS_PER_TICK;
        }
        for (int i = 0; i < budget && !queue.isEmpty(); i++) {
            CaptureChunk chunk = queue.pollFirst();
            if (chunk == null) {
                break;
            }
            captureAndUpload(mc, chunk);
            completedChunks++;
        }
        if (queue.isEmpty()) {
            finalizeUpload();
            active = false;
        }
    }

    private void captureAndUpload(Minecraft mc, CaptureChunk chunk) {
        byte[] terrain = captureTerrain(mc, chunk.dim, chunk.chunkX, chunk.chunkZ);
        if (terrain != null && terrain.length > 0) {
            WorldMapSnapshotLocalCache.writeTile(
                ownerUuid, networkId, snapshotVersion, WorldMapTileLayer.TERRAIN, chunk.dim, chunk.chunkX,
                chunk.chunkZ, terrain);
            sendTile(WorldMapTileLayer.TERRAIN, chunk.dim, chunk.chunkX, chunk.chunkZ, terrain);
        } else {
            sendMissing(chunk.dim, chunk.chunkX, chunk.chunkZ);
        }
        if (Config.webWorldMapAeOverlayEnabled) {
            byte[] ae = captureAe(mc, chunk.dim, chunk.chunkX, chunk.chunkZ);
            if (ae != null && ae.length > 0) {
                WorldMapSnapshotLocalCache.writeTile(
                    ownerUuid, networkId, snapshotVersion, WorldMapTileLayer.AE, chunk.dim, chunk.chunkX, chunk.chunkZ,
                    ae);
                sendTile(WorldMapTileLayer.AE, chunk.dim, chunk.chunkX, chunk.chunkZ, ae);
            }
        }
    }

    private byte[] captureTerrain(Minecraft mc, int dim, int chunkX, int chunkZ) {
        if (WorldMapJourneyMapTileReader.isAvailable()) {
            byte[] jm = WorldMapJourneyMapTileReader.instance()
                .readChunkTerrain(dim, chunkX, chunkZ, tilePx);
            if (jm != null && jm.length > 0) {
                return jm;
            }
        }
        WorldMapQualityTier tier = WorldMapQualityTier.fromId(Config.worldMapClientFallbackQuality);
        if (tier == null) {
            tier = WorldMapQualityTier.LOW;
        }
        return renderer.renderTerrain(mc, WorldMapView.FLAT, tier, dim, chunkX, chunkZ);
    }

    private byte[] captureAe(Minecraft mc, int dim, int chunkX, int chunkZ) {
        WorldMapQualityTier tier = WorldMapQualityTier.fromId(Config.worldMapClientFallbackQuality);
        if (tier == null) {
            tier = WorldMapQualityTier.LOW;
        }
        return renderer.renderAeOverlay(mc, WorldMapView.FLAT, tier, dim, chunkX, chunkZ);
    }

    private void sendMissing(int dim, int chunkX, int chunkZ) {
        AdvanceDataMonitor.LOG.debug("[WebAE] Snapshot missing terrain dim={} cx={} cz={}", dim, chunkX, chunkZ);
    }

    private void sendTile(String layer, int dim, int chunkX, int chunkZ, byte[] png) {
        PacketWorldMapSnapshotTileUpload packet = new PacketWorldMapSnapshotTileUpload();
        packet.ownerUuid = ownerUuid;
        packet.networkId = networkId;
        packet.snapshotVersion = snapshotVersion;
        packet.layer = layer;
        packet.dim = dim;
        packet.chunkX = chunkX;
        packet.chunkZ = chunkZ;
        packet.png = png;
        packet.finalizeSnapshot = false;
        AdvanceDataMonitor.ADMCHANEL.sendToServer(packet);
    }

    private void finalizeUpload() {
        WorldMapSnapshotLocalCache.writeLocalVersion(ownerUuid, networkId, snapshotVersion);
        PacketWorldMapSnapshotTileUpload packet = new PacketWorldMapSnapshotTileUpload();
        packet.ownerUuid = ownerUuid;
        packet.networkId = networkId;
        packet.snapshotVersion = snapshotVersion;
        packet.finalizeSnapshot = true;
        packet.source = source;
        packet.tilePx = tilePx;
        AdvanceDataMonitor.ADMCHANEL.sendToServer(packet);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.GREEN + "[WebAE] World map snapshot v" + snapshotVersion + " uploaded ("
                    + source + ", " + completedChunks + " chunks)."));
        }
    }

    public boolean isActive() {
        return active;
    }

    public int getCompletedChunks() {
        return completedChunks;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    private static int[] parseChunkEntry(String entry) {
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        int colon = entry.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String pair = entry.substring(colon + 1);
        String[] parts = pair.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[] {
                Integer.parseInt(entry.substring(0, colon).trim()),
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class CaptureChunk {

        final int dim;
        final int chunkX;
        final int chunkZ;

        CaptureChunk(int dim, int chunkX, int chunkZ) {
            this.dim = dim;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
