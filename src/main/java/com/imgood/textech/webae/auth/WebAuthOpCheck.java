package com.imgood.textech.webae.auth;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Helper to check whether a player UUID maps to an OP-authorized player.
 * Used by WebAE handlers that restrict refresh/inject endpoints to admins.
 */
public final class WebAuthOpCheck {

    private WebAuthOpCheck() {}

    /**
     * @return true if the player exists online and has OP permission level >= 2.
     */
    public static boolean isOp(String playerUuid) {
        EntityPlayerMP player = findPlayer(playerUuid);
        if (player == null) return false;
        return player.canCommandSenderUseCommand(2, "admweb");
    }

    public static EntityPlayerMP findPlayer(String playerUuid) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP mp = (EntityPlayerMP) obj;
                if (mp.getUniqueID()
                    .toString()
                    .equals(playerUuid)) {
                    return mp;
                }
            }
        }
        return null;
    }
}
