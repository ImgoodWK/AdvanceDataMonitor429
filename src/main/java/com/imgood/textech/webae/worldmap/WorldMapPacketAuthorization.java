package com.imgood.textech.webae.worldmap;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Server-side authorization and range checks for world-map packets.
 *
 * <p>
 * Packet fields identify the target WebAE owner/resource; they never identify
 * the actor. The actor is always taken from the authenticated network context
 * and is checked here before a target field is used for a store lookup.
 * </p>
 */
public final class WorldMapPacketAuthorization {

    public static final int MAX_NETWORK_ID = 1_000_000;
    public static final int MAX_SNAPSHOT_VERSION = 1_000_000;
    public static final int MAX_CHUNK_COORDINATE = 2_000_000;
    public static final int MAX_DIMENSION = 1_000_000;
    public static final int MAX_TILE_PX = 2048;

    private WorldMapPacketAuthorization() {}

    public static boolean isValidOwnerUuid(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.length() != 36) {
            return false;
        }
        try {
            UUID uuid = UUID.fromString(ownerUuid);
            // UUID.fromString is permissive about some non-canonical forms. The
            // owner id is also a directory key, so only the standard form is
            // safe to accept here.
            return uuid.toString()
                .equals(ownerUuid);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Returns the only representation used for owner-scoped disk paths. */
    public static String canonicalOwnerUuid(String ownerUuid) {
        if (!isValidOwnerUuid(ownerUuid)) {
            return null;
        }
        return UUID.fromString(ownerUuid)
            .toString();
    }

    public static boolean isValidNetworkId(int networkId) {
        return networkId >= 0 && networkId <= MAX_NETWORK_ID;
    }

    public static boolean isValidSnapshotVersion(int version) {
        return version > 0 && version <= MAX_SNAPSHOT_VERSION;
    }

    public static boolean isValidChunk(int dim, int chunkX, int chunkZ) {
        return Math.abs((long) dim) <= MAX_DIMENSION && Math.abs((long) chunkX) <= MAX_CHUNK_COORDINATE
            && Math.abs((long) chunkZ) <= MAX_CHUNK_COORDINATE;
    }

    public static boolean isValidTilePx(int tilePx) {
        return tilePx > 0 && tilePx <= MAX_TILE_PX;
    }

    /** Snapshot/HTTP layers are deliberately a closed set. */
    public static boolean isValidLayer(String layer) {
        return WorldMapTileLayer.TERRAIN.equalsIgnoreCase(layer) || WorldMapTileLayer.AE.equalsIgnoreCase(layer);
    }

    /** Owner or server operator may operate the owner's world-map resources. */
    public static boolean canOperateOwner(EntityPlayerMP player, String ownerUuid) {
        if (player == null || !isValidOwnerUuid(ownerUuid)) {
            return false;
        }
        String actorUuid = player.getUniqueID()
            .toString();
        return ownerUuid.equals(actorUuid) || player.canCommandSenderUseCommand(2, "admweb");
    }

    /**
     * Allow snapshot writes only to the owner/operator or the player who
     * explicitly accepted the active capture job for this exact version.
     */
    public static boolean canWriteSnapshot(EntityPlayerMP player, String ownerUuid, int networkId, int version) {
        if (!isValidNetworkId(networkId) || !isValidSnapshotVersion(version)) {
            return false;
        }
        WorldMapCaptureCoordinator coordinator = WorldMapCaptureCoordinator.instance();
        if (!coordinator.hasActiveJob(ownerUuid, networkId, version)) {
            return false;
        }
        return canOperateOwner(player, ownerUuid)
            || coordinator.isActiveJobPlayer(ownerUuid, networkId, version, player);
    }

    /**
     * Allow snapshot reads only to the owner/operator or the active capture
     * player for the exact snapshot version. This prevents a client from
     * turning a tile-pull request into an arbitrary owner-file read.
     */
    public static boolean canReadSnapshot(EntityPlayerMP player, String ownerUuid, int networkId, int version) {
        if (!isValidNetworkId(networkId) || !isValidSnapshotVersion(version)) {
            return false;
        }
        if (canOperateOwner(player, ownerUuid)) {
            return true;
        }
        return WorldMapCaptureCoordinator.instance()
            .isActiveJobPlayer(ownerUuid, networkId, version, player);
    }

    public static boolean canReadSnapshotScope(EntityPlayerMP player, String ownerUuid, int networkId) {
        if (!isValidNetworkId(networkId) || !isValidOwnerUuid(ownerUuid)) {
            return false;
        }
        if (canOperateOwner(player, ownerUuid)) {
            return true;
        }
        return WorldMapCaptureCoordinator.instance()
            .isActiveJobPlayerForNetwork(ownerUuid, networkId, player);
    }

    public static boolean isValidSource(String source) {
        return "dynmap".equals(source) || "journeymap".equals(source)
            || "client_gl".equals(source)
            || "mixed".equals(source);
    }

    /** The initial on-disk manifest is explicitly marked pending. */
    public static boolean isValidManifestSource(String source) {
        return "pending".equals(source) || isValidSource(source);
    }
}
