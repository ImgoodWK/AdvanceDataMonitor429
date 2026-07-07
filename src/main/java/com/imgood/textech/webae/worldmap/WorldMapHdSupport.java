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
        return Config.webWorldMapEnabled && Config.webTopologyEnabled && Config.webWorldMapClientHdEnabled;
    }

    /** True when an in-game client can upload HD tiles for this WebAE session. */
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

    /** Prefer network owner; fall back to authorized actor when online. */
    public static EntityPlayerMP resolveHdProvider(String ownerUuid, String actorUuid) {
        EntityPlayerMP owner = WebAeOwnerContext.findOnlinePlayer(ownerUuid);
        if (owner != null) {
            return owner;
        }
        if (actorUuid != null && !actorUuid.isEmpty() && !actorUuid.equals(ownerUuid)) {
            return WebAeOwnerContext.findOnlinePlayer(actorUuid);
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
