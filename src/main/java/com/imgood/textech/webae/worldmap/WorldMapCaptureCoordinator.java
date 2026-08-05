package com.imgood.textech.webae.worldmap;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
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
    static final int MAX_ACTIVE_JOBS = 32;
    static final long ACTIVE_JOB_IDLE_TTL_MS = 90L * 60L * 1000L;
    static final long ACTIVE_JOB_ABSOLUTE_TTL_MS = 2L * 60L * 60L * 1000L;
    private static final int MAX_SOURCE_STATS_KEYS = 32;

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
            Map.Entry<String, PendingRequest> entry = it.next();
            PendingRequest req = entry.getValue();
            if (req == null) {
                pending.remove(entry.getKey());
            } else if (now >= req.expiresAtMs) {
                pending.remove(entry.getKey(), req);
            }
        }
        pruneActiveJobs(now);
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
        // Capture setup and this bounded copy are derived from the same logical
        // topology object; the copy survives a consent delay safely.
        WorldMapLogicalIndex logicalIndex = WorldMapLogicalIndex.fromSnapshot(logical, 0);
        WorldMapMetaDto meta = WorldMapBoundsBuilder.buildMeta(ownerUuid, networkId, logical, markers);
        if (meta == null || meta.dimensions == null || meta.dimensions.isEmpty()) {
            return null;
        }

        List<String> chunks = WorldMapSnapshotStore.buildChunkList(meta);
        if (chunks.isEmpty()) {
            return null;
        }

        if (fromCommand && ownerIsRequester && Config.worldMapOwnerSkipConsent) {
            String jobId = startJobDirect(
                ownerUuid,
                networkId,
                requesterUuid,
                requesterName,
                chunks,
                meta,
                logicalIndex);
            if (jobId != null) {
                lastRequestMs.put(cooldownKey, now);
            }
            return jobId;
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
        req.logicalIndex = WorldMapLogicalIndex.copyOf(logicalIndex);
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
            req.meta,
            req.logicalIndex);
        return jobId != null;
    }

    public boolean isActiveJobPlayer(String ownerUuid, int networkId, int version, EntityPlayerMP player) {
        if (player == null || ownerUuid == null || ownerUuid.isEmpty()) {
            return false;
        }
        ActiveJob job = activeJobs.get(jobKey(ownerUuid, networkId, version));
        return job != null && !isActiveJobExpired(job, System.currentTimeMillis()) && job.playerUuid != null
            && job.playerUuid.equals(
            player.getUniqueID()
                .toString());
    }

    public boolean hasActiveJob(String ownerUuid, int networkId, int version) {
        if (ownerUuid == null) {
            return false;
        }
        ActiveJob job = activeJobs.get(jobKey(ownerUuid, networkId, version));
        return job != null && !isActiveJobExpired(job, System.currentTimeMillis());
    }

    public boolean isActiveJobPlayerForNetwork(String ownerUuid, int networkId, EntityPlayerMP player) {
        if (player == null || ownerUuid == null) {
            return false;
        }
        String actorUuid = player.getUniqueID()
            .toString();
        long now = System.currentTimeMillis();
        for (ActiveJob job : activeJobs.values()) {
            if (job != null && ownerUuid.equals(job.ownerUuid) && job.networkId == networkId
                && actorUuid.equals(job.playerUuid) && !isActiveJobExpired(job, now)) {
                return true;
            }
        }
        return false;
    }

    public boolean isExpectedTile(String ownerUuid, int networkId, int version, String layer, int dim, int chunkX,
        int chunkZ) {
        ActiveJob job = activeJobs.get(jobKey(ownerUuid, networkId, version));
        return !isActiveJobExpired(job, System.currentTimeMillis())
            && isExpectedTile(job, layer, dim, chunkX, chunkZ);
    }

    public boolean onTileUploaded(EntityPlayerMP player, String ownerUuid, int networkId, int version, String layer,
        int dim, int chunkX, int chunkZ) {
        String jobKey = jobKey(ownerUuid, networkId, version);
        ActiveJob job = activeJobs.get(jobKey);
        boolean authorizedActor = player != null && (WorldMapPacketAuthorization.canOperateOwner(player, ownerUuid)
            || isActiveJobPlayer(ownerUuid, networkId, version, player));
        if (job == null || isActiveJobExpired(job, System.currentTimeMillis()) || !authorizedActor
            || !isExpectedTile(job, layer, dim, chunkX, chunkZ)) {
            return false;
        }
        String tileKey = WorldMapSnapshotManifest.tileKey(layer, dim, chunkX, chunkZ);
        boolean firstUpload = job.manifest == null || job.manifest.tiles == null
            || !job.manifest.tiles.containsKey(tileKey);
        if (firstUpload) {
            job.completed++;
        }
        if (job.manifest != null) {
            WorldMapSnapshotStore.registerTileInManifest(
                job.manifest,
                layer,
                dim,
                chunkX,
                chunkZ,
                readTileBytes(ownerUuid, networkId, version, layer, dim, chunkX, chunkZ));
        }
        job.lastTouchedMs = System.currentTimeMillis();
        return true;
    }

    public boolean onSnapshotComplete(EntityPlayerMP player, String ownerUuid, int networkId, int version, String source,
        String sourceStatsJson, int tilePx) {
        String jobKey = jobKey(ownerUuid, networkId, version);
        ActiveJob job = activeJobs.get(jobKey);
        boolean authorizedActor = player != null && (WorldMapPacketAuthorization.canOperateOwner(player, ownerUuid)
            || isActiveJobPlayer(ownerUuid, networkId, version, player));
        if (job == null || !authorizedActor || job.manifest == null || isActiveJobExpired(job, System.currentTimeMillis())
            || !WorldMapPacketAuthorization.isValidSource(source)
            || !WorldMapPacketAuthorization.isValidTilePx(tilePx)) {
            return false;
        }
        Map<String, Integer> sourceStats = parseSourceStats(sourceStatsJson);
        if (sourceStats == null) {
            return false;
        }
        applyFinalizeManifest(job.manifest, source, sourceStats, tilePx);
        // The logical index is optional metadata.  Its failure must not make
        // the independently captured terrain snapshot fail to finalize.
        try {
            WorldMapLogicalIndex sidecar = WorldMapLogicalIndex.copyOf(job.logicalIndex);
            sidecar.version = version;
            if (!WorldMapSnapshotStore.saveLogicalIndex(ownerUuid, networkId, version, sidecar)) {
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] Logical world map sidecar unavailable for owner={} network={} version={}",
                    ownerUuid,
                    networkId,
                    version);
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] Logical world map sidecar write failed owner={} network={} version={}",
                ownerUuid,
                networkId,
                version,
                t);
        }
        if (!WorldMapSnapshotStore.finalizeSnapshot(job.manifest)) {
            return false;
        }
        activeJobs.remove(jobKey, job);
        return true;
    }

    private static boolean isExpectedTile(ActiveJob job, String layer, int dim, int chunkX, int chunkZ) {
        if (job == null || job.manifest == null || layer == null || layer.trim()
            .isEmpty()) {
            return false;
        }
        if (job.manifest.layers != null && !job.manifest.layers.isEmpty()
            && !job.manifest.layers.contains(WorldMapTileLayer.normalize(layer))) {
            return false;
        }
        if (job.manifest.dimensions == null) {
            return false;
        }
        for (WorldMapSnapshotManifest.DimensionEntry entry : job.manifest.dimensions) {
            if (entry == null || entry.dim != dim || entry.chunks == null) {
                continue;
            }
            String pair = chunkX + "," + chunkZ;
            if (entry.chunks.contains(pair)) {
                return true;
            }
        }
        return false;
    }

    private static void applyFinalizeManifest(WorldMapSnapshotManifest manifest, String source,
        Map<String, Integer> sourceStats, int tilePx) {
        if (manifest == null) {
            return;
        }
        if (source != null && !source.isEmpty()) {
            manifest.source = source;
        }
        if (tilePx > 0) {
            manifest.tilePx = tilePx;
        }
        manifest.sourceStats = sourceStats;
        manifest.timestamp = System.currentTimeMillis();
    }

    static Map<String, Integer> parseSourceStats(String json) {
        if (json == null || json.trim()
            .isEmpty()) {
            return null;
        }
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(false);
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        Set<String> seen = new HashSet<String>();
        long total = 0L;
        try {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                return null;
            }
            reader.beginObject();
            while (reader.hasNext()) {
                if (seen.size() >= MAX_SOURCE_STATS_KEYS) {
                    return null;
                }
                String key = reader.nextName();
                if (!("dynmap".equals(key) || "journeymap".equals(key) || "client_gl".equals(key))
                    || !seen.add(key) || reader.peek() != JsonToken.NUMBER) {
                    return null;
                }
                String rawValue = reader.nextString();
                if (rawValue == null || rawValue.isEmpty()) {
                    return null;
                }
                for (int i = 0; i < rawValue.length(); i++) {
                    char c = rawValue.charAt(i);
                    if (c < '0' || c > '9') {
                        return null;
                    }
                }
                int value;
                try {
                    value = Integer.parseInt(rawValue);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (value < 0 || value > PacketWorldMapCaptureJob.MAX_TOTAL_CHUNKS
                    || total > PacketWorldMapCaptureJob.MAX_TOTAL_CHUNKS - value) {
                    return null;
                }
                total += value;
                out.put(key, Integer.valueOf(value));
            }
            reader.endObject();
            return reader.peek() == JsonToken.END_DOCUMENT ? out : null;
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }
    }

    public ActiveJob getActiveJob(String ownerUuid, int networkId) {
        if (ownerUuid == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (ActiveJob job : activeJobs.values()) {
            if (job != null && ownerUuid.equals(job.ownerUuid) && job.networkId == networkId
                && !isActiveJobExpired(job, now)) {
                return job;
            }
        }
        return null;
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
        List<String> chunks, WorldMapMetaDto meta, WorldMapLogicalIndex logicalIndex) {
        EntityPlayerMP target = findPlayerByUuid(playerUuid);
        if (target == null || getActiveJob(ownerUuid, networkId) != null || hasActiveJobForPlayer(playerUuid)) {
            return null;
        }
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

        List<PacketWorldMapCaptureJob> pages;
        try {
            pages = PacketWorldMapCaptureJob.createPages(
                ownerUuid,
                networkId,
                version,
                chunks,
                manifest.tilePx,
                Config.worldMapSnapshotSourcePriority);
        } catch (IllegalArgumentException e) {
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] Refusing invalid world map capture job owner={} network={}: {}",
                ownerUuid,
                networkId,
                e.getMessage());
            return null;
        }
        if (!WorldMapSnapshotStore.saveManifest(manifest)) {
            WorldMapSnapshotStore.discardUnpublishedSnapshot(ownerUuid, networkId, version);
            return null;
        }

        ActiveJob job = new ActiveJob();
        job.ownerUuid = ownerUuid;
        job.networkId = networkId;
        job.version = version;
        job.playerUuid = target.getUniqueID()
            .toString();
        job.playerName = playerName != null ? playerName : target.getDisplayName();
        job.totalChunks = chunks != null ? chunks.size() : 0;
        job.manifest = manifest;
        job.logicalIndex = WorldMapLogicalIndex.copyOf(logicalIndex)
            .withVersion(version);
        job.createdAtMs = System.currentTimeMillis();
        job.lastTouchedMs = job.createdAtMs;
        String key = jobKey(ownerUuid, networkId, version);
        if (activeJobs.size() >= MAX_ACTIVE_JOBS || activeJobs.putIfAbsent(key, job) != null) {
            WorldMapSnapshotStore.discardUnpublishedSnapshot(ownerUuid, networkId, version);
            return null;
        }

        try {
            for (PacketWorldMapCaptureJob page : pages) {
                AdvanceDataMonitor.ADMCHANEL.sendTo(page, target);
            }
            target.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[WebAE] World map snapshot capture started (v" + version + ")."));
        } catch (RuntimeException e) {
            activeJobs.remove(key, job);
            WorldMapSnapshotStore.discardUnpublishedSnapshot(ownerUuid, networkId, version);
            AdvanceDataMonitor.LOG.warn(
                "[WebAE] Failed to send world map capture job owner={} network={} version={}",
                ownerUuid,
                networkId,
                version,
                e);
            return null;
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
        net.minecraft.server.MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : players) {
            if (player != null && uuid.equals(
                player.getUniqueID()
                    .toString())) {
                return player;
            }
        }
        return null;
    }

    private boolean hasActiveJobForPlayer(String playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (ActiveJob job : activeJobs.values()) {
            if (job != null && playerUuid.equals(job.playerUuid) && !isActiveJobExpired(job, now)) {
                return true;
            }
        }
        return false;
    }

    private void pruneActiveJobs(long nowMs) {
        for (Map.Entry<String, ActiveJob> entry : activeJobs.entrySet()) {
            ActiveJob job = entry.getValue();
            if (job == null || isActiveJobExpired(job, nowMs) || findPlayerByUuid(job.playerUuid) == null) {
                if (job == null) {
                    activeJobs.remove(entry.getKey());
                } else if (activeJobs.remove(entry.getKey(), job)) {
                    WorldMapSnapshotStore.discardUnpublishedSnapshot(job.ownerUuid, job.networkId, job.version);
                }
            }
        }
    }

    static boolean isActiveJobExpired(ActiveJob job, long nowMs) {
        if (job == null || job.createdAtMs <= 0L || job.lastTouchedMs <= 0L) {
            return true;
        }
        long idleAge = nowMs - job.lastTouchedMs;
        long absoluteAge = nowMs - job.createdAtMs;
        return idleAge >= ACTIVE_JOB_IDLE_TTL_MS || absoluteAge >= ACTIVE_JOB_ABSOLUTE_TTL_MS;
    }

    public void clearForPlayer(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ActiveJob> entry : activeJobs.entrySet()) {
            ActiveJob job = entry.getValue();
            if (job != null && playerUuid.equals(job.playerUuid) && activeJobs.remove(entry.getKey(), job)) {
                WorldMapSnapshotStore.discardUnpublishedSnapshot(job.ownerUuid, job.networkId, job.version);
            }
        }
    }

    public void clear() {
        for (Map.Entry<String, ActiveJob> entry : activeJobs.entrySet()) {
            ActiveJob job = entry.getValue();
            if (job != null && activeJobs.remove(entry.getKey(), job)) {
                WorldMapSnapshotStore.discardUnpublishedSnapshot(job.ownerUuid, job.networkId, job.version);
            }
        }
        activeJobs.clear();
        pending.clear();
        lastRequestMs.clear();
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
        /** Bounded defensive copy of the logical snapshot used for this request. */
        public WorldMapLogicalIndex logicalIndex;
    }

    public static final class ActiveJob {

        public String ownerUuid;
        public int networkId;
        public int version;
        public String playerUuid;
        public String playerName;
        public int totalChunks;
        public int completed;
        public long createdAtMs;
        public volatile long lastTouchedMs;
        public WorldMapSnapshotManifest manifest;
        /** Bounded defensive copy of the logical snapshot used for this job. */
        public WorldMapLogicalIndex logicalIndex;
    }
}
