package com.imgood.textech.webae.onboarding;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWebConsoleTokenNotify;

/**
 * Phase 6.2: notify block owners about WebAE console access after NetworkLink placement.
 */
public final class WebConsoleOnboarding {

    private WebConsoleOnboarding() {}

    /**
     * Sends a client-side clickable chat (S→C packet) with console URL and {@code /admweb login} hint.
     */
    public static void notifyOwnerOnNetworkLinkPlaced(EntityPlayerMP player) {
        if (player == null || !Config.webConsoleEnabled) {
            return;
        }
        AdvanceDataMonitor.ADMCHANEL.sendTo(
            new PacketWebConsoleTokenNotify(
                PacketWebConsoleTokenNotify.KIND_ONBOARDING,
                "",
                Config.webConsolePort,
                Config.webConsoleBindAddress),
            player);
    }
}
