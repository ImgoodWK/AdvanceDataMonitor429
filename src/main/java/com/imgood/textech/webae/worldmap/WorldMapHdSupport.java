package com.imgood.textech.webae.worldmap;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.Config;
import com.imgood.textech.webae.context.WebAeOwnerContext;

/**
 * Server-side helpers for client HD world map tile uploads (Phase 4).
 */
public final class WorldMapHdSupport {

    private WorldMapHdSupport() {}

    public static boolean isHdEnabled() {
        return Config.webWorldMapEnabled && Config.webTopologyEnabled
            && Config.webWorldMapClientHdEnabled
            && WorldMapClientCaptureMode.isEnabled();
    }

    /** True when an in-game client can upload HD tiles for this WebAE session (any dimension). */
    public static boolean isHdAvailable(String ownerUuid, String actorUuid) {
        if (!isHdEnabled() || !WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)) {
            return false;
        }
        EntityPlayerMP owner = WebAeOwnerContext.findOnlinePlayer(ownerUuid);
        if (owner != null && WorldMapPacketAuthorization.canOperateOwner(owner, ownerUuid)) {
            return true;
        }
        if (WorldMapPacketAuthorization.isValidOwnerUuid(actorUuid) && !actorUuid.equalsIgnoreCase(ownerUuid)) {
            EntityPlayerMP actor = WebAeOwnerContext.findOnlinePlayer(actorUuid);
            return actor != null && WorldMapPacketAuthorization.canOperateOwner(actor, ownerUuid);
        }
        return false;
    }

    /** True when a client in the target dimension can render/upload tiles. */
    public static boolean isClientCaptureAvailable(String ownerUuid, String actorUuid, int dim) {
        if (!isHdEnabled()) {
            return false;
        }
        return resolveHdProvider(ownerUuid, actorUuid, dim) != null;
    }

    /** Prefer network owner in target dim; fall back to authorized actor when online in dim. */
    public static EntityPlayerMP resolveHdProvider(String ownerUuid, String actorUuid) {
        return resolveHdProvider(ownerUuid, actorUuid, Integer.MIN_VALUE);
    }

    public static EntityPlayerMP resolveHdProvider(String ownerUuid, String actorUuid, int dim) {
        return resolveHdProvider(ownerUuid, actorUuid, dim, Integer.MIN_VALUE);
    }

    /**
     * Resolves only an owner/OP or the actor accepted for this exact network.
     * The network id is part of the authorization context for non-owner actors.
     */
    public static EntityPlayerMP resolveHdProvider(String ownerUuid, String actorUuid, int dim, int networkId) {
        if (!isHdEnabled() || !WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)) {
            return null;
        }
        return resolveAuthorizedProviderInternal(ownerUuid, actorUuid, dim, networkId);
    }

    /**
     * Resolves the same owner/network-scoped provider without requiring client-HD
     * rendering to be enabled.  Disk-backed direct tiles still need this check
     * before a cache hit can be returned; otherwise a stale cache entry could
     * bypass the current owner/network authorization.
     */
    public static EntityPlayerMP resolveAuthorizedProvider(String ownerUuid, String actorUuid, int dim, int networkId) {
        if (!WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)
            || !WorldMapPacketAuthorization.isValidNetworkId(networkId)) {
            return null;
        }
        return resolveAuthorizedProviderInternal(ownerUuid, actorUuid, dim, networkId);
    }

    private static EntityPlayerMP resolveAuthorizedProviderInternal(String ownerUuid, String actorUuid, int dim,
        int networkId) {
        EntityPlayerMP owner = WebAeOwnerContext.findOnlinePlayer(ownerUuid);
        if (owner != null && (dim == Integer.MIN_VALUE || owner.dimension == dim)
            && WorldMapPacketAuthorization.canOperateOwner(owner, ownerUuid)) {
            return owner;
        }
        if (WorldMapPacketAuthorization.isValidOwnerUuid(actorUuid) && !actorUuid.equalsIgnoreCase(ownerUuid)) {
            EntityPlayerMP actor = WebAeOwnerContext.findOnlinePlayer(actorUuid);
            boolean activeActor = WorldMapPacketAuthorization.isValidNetworkId(networkId)
                && WorldMapCaptureCoordinator.instance()
                    .isActiveJobPlayerForNetwork(ownerUuid, networkId, actor);
            if (actor != null && (dim == Integer.MIN_VALUE || actor.dimension == dim)
                && (WorldMapPacketAuthorization.canOperateOwner(actor, ownerUuid) || activeActor)) {
                return actor;
            }
        }
        return null;
    }

    public static boolean canUploadForOwner(EntityPlayerMP player, String ownerUuid) {
        return WorldMapPacketAuthorization.canOperateOwner(player, ownerUuid);
    }

    public static boolean canUploadForOwner(EntityPlayerMP player, String ownerUuid, int networkId) {
        if (!WorldMapPacketAuthorization.isValidNetworkId(networkId) || player == null
            || !WorldMapPacketAuthorization.isValidOwnerUuid(ownerUuid)) {
            return false;
        }
        return WorldMapPacketAuthorization.canOperateOwner(player, ownerUuid)
            || WorldMapCaptureCoordinator.instance().isActiveJobPlayerForNetwork(ownerUuid, networkId, player);
    }
}
