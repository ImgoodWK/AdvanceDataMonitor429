package com.imgood.advancedatamonitor.handler;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.items.GrappleRouteEntry;
import com.imgood.advancedatamonitor.network.packet.PacketGrapplePathSync;

public final class GrappleRouteSync {

    private GrappleRouteSync() {}

    public static void syncAll(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        List<GrappleRouteEntry> routes = GrapplePathStore.instance()
            .getRoutesForPlayer(player);
        AdvanceDataMonitor.ADMCHANEL.sendTo(PacketGrapplePathSync.routes(routes), player);
        syncBuffer(player);
    }

    public static void syncBuffer(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        AdvanceDataMonitor.ADMCHANEL
            .sendTo(PacketGrapplePathSync.buffer(GrapplePlanningSession.getBuffer(player)), player);
    }

    public static void notify(EntityPlayerMP player, String messageKey) {
        if (player == null || messageKey == null || messageKey.isEmpty()) {
            return;
        }
        PacketGrapplePathSync packet = PacketGrapplePathSync.withMessage(messageKey);
        AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);
    }
}
