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
        return Config.webWorldMapEnabled && Config.webTopologyEnabled && Config.webWorldMapClientHdEnabled
            && WorldMapClientCaptureMode.isEnabled();
    }

    /** True when an in-game client can upload HD tiles for this WebAE session (any dimension). */
    public static boolean isHdAvailable(String ownerUuid, String actorUuid) {
        if (!isHdEnabled()) {
            return false;
        }
        if (WebAeOwnerContext.findOnlinePlayer(ownerUuid) != null) {
            return true;
        }
        if (actorUuid != null && !actorUuid.isEmpty() && !actorUuid.equals(ownerUuid)) {
            return WebAeOwnerContext.findOnlinePlayer(actorUuid) != null;
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
        EntityPlayerMP owner = WebAeOwnerContext.findOnlinePlayer(ownerUuid);
        if (owner != null && (dim == Integer.MIN_VALUE || owner.dimension == dim)) {
            return owner;
        }
        if (actorUuid != null && !actorUuid.isEmpty() && !actorUuid.equals(ownerUuid)) {
            EntityPlayerMP actor = WebAeOwnerContext.findOnlinePlayer(actorUuid);
            if (actor != null && (dim == Integer.MIN_VALUE || actor.dimension == dim)) {
                return actor;
            }
        }
        return null;
    }

    public static boolean canUploadForOwner(EntityPlayerMP player, String ownerUuid) {
        if (player == null || ownerUuid == null || ownerUuid.isEmpty()) {
            return false;
        }
        String uuid = player.getUniqueID()
            .toString();
        return ownerUuid.equals(uuid);
    }
}
