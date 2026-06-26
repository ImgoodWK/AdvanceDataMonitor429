package com.imgood.advancedatamonitor.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.advancedatamonitor.items.GrappleHookMode;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;
import com.imgood.advancedatamonitor.utils.BlockPos;

public final class GrapplePlanningSession {

    private static final Map<UUID, Session> SESSIONS = new HashMap<UUID, Session>();

    private GrapplePlanningSession() {}

    public static void recordNode(EntityPlayer player, int x, int y, int z) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        if (ItemGrappleHook.getHookMode(player) != GrappleHookMode.PLANNING) {
            return;
        }
        UUID id = player.getUniqueID();
        Session session = SESSIONS.get(id);
        if (session == null) {
            session = new Session();
            session.dimension = player.worldObj.provider.dimensionId;
            SESSIONS.put(id, session);
        }
        if (session.dimension != player.worldObj.provider.dimensionId) {
            return;
        }
        BlockPos node = new BlockPos(x, y, z);
        if (!session.buffer.isEmpty()) {
            BlockPos last = session.buffer.get(session.buffer.size() - 1);
            if (last.equals(node)) {
                return;
            }
        }
        session.buffer.add(node);
    }

    public static List<BlockPos> getBuffer(EntityPlayer player) {
        Session session = SESSIONS.get(player.getUniqueID());
        if (session == null) {
            return new ArrayList<BlockPos>();
        }
        return new ArrayList<BlockPos>(session.buffer);
    }

    public static int getBufferSize(EntityPlayer player) {
        Session session = SESSIONS.get(player.getUniqueID());
        return session == null ? 0 : session.buffer.size();
    }

    public static boolean hasBuffer(EntityPlayer player) {
        return getBufferSize(player) > 0;
    }

    public static void resetBuffer(EntityPlayer player) {
        SESSIONS.remove(player.getUniqueID());
    }

    public static String saveBuffer(EntityPlayerMP player, String name) {
        Session session = SESSIONS.get(player.getUniqueID());
        if (session == null || session.buffer.isEmpty()) {
            return null;
        }
        String routeId = GrapplePathStore.instance()
            .saveRoute(player, name, session.dimension, session.buffer);
        if (routeId != null) {
            SESSIONS.remove(player.getUniqueID());
        }
        return routeId;
    }

    public static void clearOnLogout(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    private static final class Session {

        int dimension;
        final List<BlockPos> buffer = new ArrayList<BlockPos>();
    }
}
