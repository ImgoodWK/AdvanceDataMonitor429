package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWorldMapCaptureJob;
import com.imgood.textech.webae.network.PacketWorldMapCaptureOffer;
import com.imgood.textech.webae.topology.TopologySnapshot;
import com.imgood.textech.webae.topology.TopologySnapshotStore;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Consent-based world map snapshot capture coordinator (server-side).
 */
public final class WorldMapCaptureCoordinator {

    private static final WorldMapCaptureCoordinator INSTANCE = new WorldMapCaptureCoordinator();

    private final ConcurrentHashMap<String, PendingRequest> pending = new ConcurrentHashMap<String, PendingRequest>();
    private final ConcurrentHashMap<String, ActiveJob> activeJobs = new ConcurrentHashMap<String, ActiveJob>();
    private final ConcurrentHashMap<String, Long> lastRequestMs = new ConcurrentHashMap<String, Long>();

    private WorldMapCaptureCoordinator() {}

    public static WorldMapCaptureCoordinator instance() {
        return INSTANCE;
    }

    public void onServerTick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingRequest>> it = pending.entrySet()
            .iterator();
        while (it.hasNext()) {
            PendingRequest req = it.next()
                .getValue();
            if (req != null && now >= req.expiresAtMs) {
                it.remove();
            }
        }
    }

    public String requestSnapshot(String ownerUuid, int networkId, String requesterUuid, String requesterName,
        boolean fromCommand, boolean ownerIsRequester) {
        if (!Config.webWorldMapEnabled || !Config.webTopologyEnabled) {
            return null;
        }
        long now = System.currentTimeMillis();
        PendingRequest existingPending = getPendingForNetwork(ownerUuid, networkId);
        if (existingPending != null && now < existingPending.expiresAtMs) {
            return existingPending.requestId;
        }

        String cooldownKey = ownerUuid + ":" + networkId;
        Long last = lastRequestMs.get(cooldownKey);
        long cooldownMs = snapshotCooldownMs();
        if (last != null && now - last < cooldownMs) {
            return null;
        }

        TopologySnapshot logical = TopologySnapshotStore.loadSnapshot(ownerUuid, networkId, "logical");
        if (logical == null) {
            return null;
        }
        List<WorldMapMarkerDto> markers = WorldMapMarkerBuilder.fromLogicalSnapshot(logical);
        WorldMapMetaDto meta = WorldMapBoundsBuilder.buildMeta(ownerUuid, networkId, logical, markers);
        if (meta == null || meta.dimensions == null || meta.dimensions.isEmpty()) {
            return null;
        }

        List<String> chunks = WorldMapSnapshotStore.buildChunkList(meta);
        if (chunks.isEmpty()) {
            return null;
        }

        if (fromCommand && ownerIsRequester && Config.worldMapOwnerSkipConsent) {
            return startJobDirect(ownerUuid, networkId, requesterUuid, requesterName, chunks, meta);
        }

        List<EntityPlayerMP> nearby = findNearbyPlayers(meta);
        if (nearby.isEmpty()) {
            return null;
        }

        String requestId = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 12);
        long expires = now + Math.max(30L, Config.worldMapConsentTimeoutSec) * 1000L;
        PendingRequest req = new PendingRequest();
        req.requestId = requestId;
        req.ownerUuid = ownerUuid;
        req.networkId = networkId;
        req.requesterUuid = requesterUuid;
        req.requesterName = requesterName;
        req.expiresAtMs = expires;
        req.chunks = new ArrayList<String>(chunks);
        req.meta = meta;
        pending.put(requestId, req);
        lastRequestMs.put(cooldownKey, now);

        PacketWorldMapCaptureOffer offer = new PacketWorldMapCaptureOffer(
            requestId,
            ownerUuid,
            networkId,
            requesterName,
            chunks.size(),
            expires);
        for (EntityPlayerMP player : nearby) {
            AdvanceDataMonitor.ADMCHANEL.sendTo(offer, player);
            WorldMapConsentChat.sendOffer(player, requestId, networkId, requesterName, chunks.size(), expires);
        }
        return requestId;
    }

    public boolean reject(String requestId, EntityPlayerMP player) {
        if (requestId == null || requestId.isEmpty() || player == null) {
            return false;
        }
        PendingRequest req = pending.get(requestId);
        if (req == null || System.currentTimeMillis() >= req.expiresAtMs) {
            return false;
        }
        player.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("adm.worldmap.consent.rejected")));
        return true;
    }

    public boolean accept(String requestId, EntityPlayerMP player) {
        if (requestId == null || requestId.isEmpty() || player == null) {
            return false;
        }
        PendingRequest req = pending.remove(requestId);
        if (req == null || System.currentTimeMillis() >= req.expiresAtMs) {
            return false;
        }
        if (!isPlayerNearNetwork(player, req.meta)) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "[WebAE] You are too far from the AE network to upload a map snapshot."));
            return false;
        }
        String jobId = startJobDirect(
            req.ownerUuid,
            req.networkId,
            player.getUniqueID()
                .toString(),
            player.getDisplayName(),
            req.chunks,
            req.meta);
        return jobId != null;
    }

    public void onTileUploaded(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ) {
        String jobKey = jobKey(ownerUuid, networkId, version);
        ActiveJob job = activeJobs.get(jobKey);
        if (job == null) {
            return;
        }
        job.completed++;
        if (job.manifest != null) {
            WorldMapSnapshotStore.registerTileInManifest(
                job.manifest,
                layer,
                dim,
                chunkX,
                chunkZ,
                readTileBytes(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ));
        }
    }

    public void onSnapshotComplete(String ownerUuid, int networkId, int version, String source, String sourceStatsJson,
        int tilePx) {
        String jobKey = jobKey(ownerUuid, networkId, version);
        ActiveJob job = activeJobs.remove(jobKey);
        if (job != null && job.manifest != null) {
            applyFinalizeManifest(job.manifest, source, sourceStatsJson, tilePx);
            WorldMapSnapshotStore.finalizeSnapshot(job.manifest);
        } else {
            WorldMapSnapshotManifest manifest = WorldMapSnapshotStore.loadManifest(ownerUuid, networkId, version);
            if (manifest != null) {
                applyFinalizeManifest(manifest, source, sourceStatsJson, tilePx);
                WorldMapSnapshotStore.finalizeSnapshot(manifest);
            }
        }
    }

    private static void applyFinalizeManifest(WorldMapSnapshotManifest manifest, String source, String sourceStatsJson,
        int tilePx) {
        if (manifest == null) {
            return;
        }
        if (source != null && !source.isEmpty()) {
            manifest.source = source;
        }
        if (tilePx > 0) {
            manifest.tilePx = tilePx;
        }
        manifest.sourceStats = parseSourceStats(sourceStatsJson);
        manifest.timestamp = System.currentTimeMillis();
    }

    private static java.util.Map<String, Integer> parseSourceStats(String json) {
        java.util.Map<String, Integer> out = new java.util.HashMap<String, Integer>();
        if (json == null || json.trim()
            .isEmpty() || "{}".equals(json.trim())) {
            return out;
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return out;
        }
        String body = trimmed.substring(1, trimmed.length() - 1)
            .trim();
        if (body.isEmpty()) {
            return out;
        }
        String[] pairs = body.split(",");
        for (String pair : pairs) {
            if (pair == null || pair.trim()
                .isEmpty()) {
                continue;
            }
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = pair.substring(0, colon)
                .trim();
            if (key.startsWith("\"") && key.endsWith("\"") && key.length() >= 2) {
                key = key.substring(1, key.length() - 1);
            }
            try {
                int value = Integer.parseInt(
                    pair.substring(colon + 1)
                        .trim());
                out.put(key, value);
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    public ActiveJob getActiveJob(String ownerUuid, int networkId) {
        int version = WorldMapSnapshotStore.currentVersion(ownerUuid, networkId);
        if (version <= 0) {
            for (ActiveJob job : activeJobs.values()) {
                if (job != null && ownerUuid.equals(job.ownerUuid) && job.networkId == networkId) {
                    return job;
                }
            }
            return null;
        }
        return activeJobs.get(jobKey(ownerUuid, networkId, version));
    }

    public PendingRequest getPendingForNetwork(String ownerUuid, int networkId) {
        long now = System.currentTimeMillis();
        for (PendingRequest req : pending.values()) {
            if (req != null && ownerUuid.equals(req.ownerUuid) && req.networkId == networkId && now < req.expiresAtMs) {
                return req;
            }
        }
        return null;
    }

    /** Pending request ids visible to a nearby online player (for tab completion). */
    public List<String> listPendingForPlayer(EntityPlayerMP player) {
        List<String> out = new ArrayList<String>();
        if (player == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        for (PendingRequest req : pending.values()) {
            if (req == null || now >= req.expiresAtMs || req.requestId == null) {
                continue;
            }
            if (req.meta != null && isPlayerNearNetwork(player, req.meta)) {
                out.add(req.requestId);
            }
        }
        return out;
    }

    /** Latest pending offer for player when request id is omitted from /admweb wm y. */
    public String latestPendingForPlayer(EntityPlayerMP player) {
        List<String> ids = listPendingForPlayer(player);
        if (ids.isEmpty()) {
            return null;
        }
        String latest = null;
        long latestExpires = 0L;
        long now = System.currentTimeMillis();
        for (String id : ids) {
            PendingRequest req = pending.get(id);
            if (req == null || now >= req.expiresAtMs) {
                continue;
            }
            if (req.expiresAtMs >= latestExpires) {
                latestExpires = req.expiresAtMs;
                latest = id;
            }
        }
        return latest;
    }

    public long remainingCooldownMs(String ownerUuid, int networkId) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return 0L;
        }
        String cooldownKey = ownerUuid + ":" + networkId;
        Long last = lastRequestMs.get(cooldownKey);
        if (last == null) {
            return 0L;
        }
        long cooldownMs = snapshotCooldownMs();
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0L, cooldownMs - elapsed);
    }

    public static long snapshotCooldownMs() {
        return Math.max(1000L, Config.worldMapSnapshotCooldownMs);
    }

    public WorldMapSnapshotStatusDto buildStatus(String ownerUuid, int networkId) {
        WorldMapSnapshotStatusDto dto = new WorldMapSnapshotStatusDto();
        dto.networkId = networkId;
        dto.currentVersion = WorldMapSnapshotStore.currentVersion(ownerUuid, networkId);
        WorldMapSnapshotManifest manifest = WorldMapSnapshotStore.loadCurrentManifest(ownerUuid, networkId);
        if (manifest != null) {
            dto.timestamp = manifest.timestamp;
            dto.source = manifest.source;
            dto.tilePx = manifest.tilePx;
            dto.manifest = manifest;
            dto.missingChunks = manifest.missingChunks != null ? manifest.missingChunks.size() : 0;
        }
        PendingRequest pendingReq = getPendingForNetwork(ownerUuid, networkId);
        if (pendingReq != null) {
            dto.captureState = "awaiting_consent";
            dto.requestId = pendingReq.requestId;
            dto.totalChunks = pendingReq.chunks != null ? pendingReq.chunks.size() : 0;
            dto.expiresAtMs = pendingReq.expiresAtMs;
            dto.message = "Waiting for a nearby player to accept upload request.";
            return dto;
        }
        ActiveJob job = getActiveJob(ownerUuid, networkId);
        if (job != null) {
            dto.captureState = "capturing";
            dto.acceptPlayerName = job.playerName;
            dto.totalChunks = job.totalChunks;
            dto.completedChunks = job.completed;
            dto.message = "Snapshot capture in progress.";
            return dto;
        }
        dto.captureState = dto.currentVersion > 0 ? "ready" : "none";
        dto.message = dto.currentVersion > 0 ? "Snapshot available." : "No snapshot yet. Request a manual update.";
        return dto;
    }

    private String startJobDirect(String ownerUuid, int networkId, String playerUuid, String playerName,
        List<String> chunks, WorldMapMetaDto meta) {
        int version = WorldMapSnapshotStore.allocateNextVersion(ownerUuid, networkId);
        WorldMapSnapshotManifest manifest = new WorldMapSnapshotManifest();
        manifest.version = version;
        manifest.timestamp = System.currentTimeMillis();
        manifest.ownerUuid = ownerUuid;
        manifest.networkId = networkId;
        manifest.source = "pending";
        manifest.tilePx = meta != null ? meta.tilePx : 128;
        manifest.layers = new ArrayList<String>();
        manifest.layers.add(WorldMapTileLayer.TERRAIN);
        if (Config.webWorldMapAeOverlayEnabled) {
            manifest.layers.add(WorldMapTileLayer.AE);
        }
        manifest.dimensions = new ArrayList<WorldMapSnapshotManifest.DimensionEntry>();
        if (meta != null && meta.dimensions != null) {
            for (WorldMapMetaDto.DimensionInfo dimInfo : meta.dimensions) {
                if (dimInfo == null) {
                    continue;
                }
                WorldMapSnapshotManifest.DimensionEntry entry = new WorldMapSnapshotManifest.DimensionEntry();
                entry.dim = dimInfo.dim;
                entry.chunks = new ArrayList<String>();
                if (dimInfo.allowedChunks != null) {
                    entry.chunks.addAll(dimInfo.allowedChunks);
                }
                manifest.dimensions.add(entry);
            }
        }
        WorldMapSnapshotStore.saveManifest(manifest);

        ActiveJob job = new ActiveJob();
        job.ownerUuid = ownerUuid;
        job.networkId = networkId;
        job.version = version;
        job.playerUuid = playerUuid;
        job.playerName = playerName;
        job.totalChunks = chunks != null ? chunks.size() : 0;
        job.manifest = manifest;
        activeJobs.put(jobKey(ownerUuid, networkId, version), job);

        EntityPlayerMP target = findPlayerByUuid(playerUuid);
        if (target != null) {
            PacketWorldMapCaptureJob captureJob = new PacketWorldMapCaptureJob(
                ownerUuid,
                networkId,
                version,
                chunks,
                manifest.tilePx);
            captureJob.sourcePriority = Config.worldMapSnapshotSourcePriority;
            AdvanceDataMonitor.ADMCHANEL.sendTo(captureJob, target);
            target.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[WebAE] World map snapshot capture started (v" + version + ")."));
        }
        return String.valueOf(version);
    }

    private static List<EntityPlayerMP> findNearbyPlayers(WorldMapMetaDto meta) {
        List<EntityPlayerMP> result = new ArrayList<EntityPlayerMP>();
        if (meta == null || meta.dimensions == null) {
            return result;
        }
        int radius = Math.max(1, Config.worldMapConsentRadiusChunks);
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getConfigurationManager().playerEntityList;
        for (Object obj : players) {
            if (!(obj instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP) obj;
            if (isPlayerNearNetwork(player, meta, radius)) {
                result.add(player);
            }
        }
        return result;
    }

    private static boolean isPlayerNearNetwork(EntityPlayerMP player, WorldMapMetaDto meta) {
        return isPlayerNearNetwork(player, meta, Math.max(1, Config.worldMapConsentRadiusChunks));
    }

    private static boolean isPlayerNearNetwork(EntityPlayerMP player, WorldMapMetaDto meta, int radiusChunks) {
        if (player == null || meta == null || meta.dimensions == null) {
            return false;
        }
        int pDim = player.worldObj.provider.dimensionId;
        int pcx = player.chunkCoordX;
        int pcz = player.chunkCoordZ;
        for (WorldMapMetaDto.DimensionInfo dimInfo : meta.dimensions) {
            if (dimInfo == null || dimInfo.dim != pDim) {
                continue;
            }
            if (dimInfo.allowedChunks != null && !dimInfo.allowedChunks.isEmpty()) {
                for (String pair : dimInfo.allowedChunks) {
                    int[] coords = parseChunkPair(pair);
                    if (coords == null) {
                        continue;
                    }
                    if (Math.abs(coords[0] - pcx) <= radiusChunks && Math.abs(coords[1] - pcz) <= radiusChunks) {
                        return true;
                    }
                }
                continue;
            }
            if (pcx >= dimInfo.minChunkX - radiusChunks && pcx <= dimInfo.maxChunkX + radiusChunks
                && pcz >= dimInfo.minChunkZ - radiusChunks
                && pcz <= dimInfo.maxChunkZ + radiusChunks) {
                return true;
            }
        }
        return false;
    }

    private static EntityPlayerMP findPlayerByUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : players) {
            if (player != null && uuid.equals(
                player.getUniqueID()
                    .toString())) {
                return player;
            }
        }
        return null;
    }

    private static int[] parseChunkPair(String pair) {
        if (pair == null) {
            return null;
        }
        String[] parts = pair.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String jobKey(String ownerUuid, int networkId, int version) {
        return ownerUuid + ":" + networkId + ":v" + version;
    }

    private static byte[] readTileBytes(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ) {
        java.io.File file = WorldMapSnapshotStore
            .getExistingTile(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ);
        if (file == null) {
            return new byte[0];
        }
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            fis.close();
            if (read > 0) {
                return data;
            }
        } catch (Exception ignored) {}
        return new byte[0];
    }

    public static final class PendingRequest {

        public String requestId;
        public String ownerUuid;
        public int networkId;
        public String requesterUuid;
        public String requesterName;
        public long expiresAtMs;
        public List<String> chunks;
        public WorldMapMetaDto meta;
    }

    public static final class ActiveJob {

        public String ownerUuid;
        public int networkId;
        public int version;
        public String playerUuid;
        public String playerName;
        public int totalChunks;
        public int completed;
        public WorldMapSnapshotManifest manifest;
    }
}
