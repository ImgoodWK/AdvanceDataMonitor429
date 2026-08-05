package com.imgood.textech.handler;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.items.GrappleRouteEntry;
import com.imgood.textech.network.packet.PacketGrapplePathSync;

public final class GrappleRouteSync {

    private GrappleRouteSync() {}

    public static void syncAll(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        List<GrappleRouteEntry> routes = GrapplePathStore.instance()
            .getRoutesForPlayer(player);
        List<PacketGrapplePathSync> routePackets = PacketGrapplePathSync.routePackets(routes);
        for (PacketGrapplePathSync packet : routePackets) {
            AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);
        }
        syncBuffer(player);
    }

    public static void syncBuffer(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        PacketGrapplePathSync packet = PacketGrapplePathSync.buffer(GrapplePlanningSession.getBuffer(player));
        if (packet.fitsPacketBudget()) {
            AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);
        }
    }

    public static void notify(EntityPlayerMP player, String messageKey) {
        if (player == null || messageKey == null || messageKey.isEmpty()) {
            return;
        }
        PacketGrapplePathSync packet = PacketGrapplePathSync.withMessage(messageKey);
        if (packet.fitsPacketBudget()) {
            AdvanceDataMonitor.ADMCHANEL.sendTo(packet, player);
        }
    }
}
