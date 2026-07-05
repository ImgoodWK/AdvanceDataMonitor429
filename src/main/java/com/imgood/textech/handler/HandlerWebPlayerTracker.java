package com.imgood.textech.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.player.PlayerInfoStore;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Server-side event handler that feeds login/logout events into
 * {@link PlayerInfoStore} for the WebAE {@code /api/players} endpoint.
 *
 * <p>
 * Also ticks the store's debounced save from the server tick event so that
 * rapid login/logout bursts do not cause excessive disk writes.
 * </p>
 */
public class HandlerWebPlayerTracker {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player == null) return;
        if (event.player.worldObj != null && event.player.worldObj.isRemote) return;
        try {
            UUID uuid = event.player.getUniqueID();
            String name = event.player.getDisplayName();
            if (name == null) name = event.player.getCommandSenderName();
            PlayerInfoStore.instance()
                .touchLogin(uuid, name, System.currentTimeMillis());
            AdvanceDataMonitor.LOG.info("[WebAE] Player logged in: {} ({})", name, uuid);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to record player login", t);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null) return;
        if (event.player.worldObj != null && event.player.worldObj.isRemote) return;
        try {
            UUID uuid = event.player.getUniqueID();
            PlayerInfoStore.instance()
                .touchLogout(uuid, System.currentTimeMillis());
            String name = event.player.getCommandSenderName();
            AdvanceDataMonitor.LOG.info("[WebAE] Player logged out: {} ({})", name, uuid);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to record player logout", t);
        }
    }

    /**
     * Reconcile in-memory online flags with the actual online player list and
     * flush any pending debounced save. Invoked from {@link HandlerTick#onServerTick}.
     */
    public static void onServerTick(long now) {
        try {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && server.getConfigurationManager() != null) {
                List<UUID> online = new ArrayList<UUID>();
                for (Object obj : server.getConfigurationManager().playerEntityList) {
                    if (obj instanceof EntityPlayerMP) {
                        online.add(((EntityPlayerMP) obj).getUniqueID());
                    }
                }
                PlayerInfoStore.instance()
                    .reconcileOnline(online, now);
            }
            PlayerInfoStore.instance()
                .tickSave(now);
            // 在线人数趋势采样（p2-dashboard）
            com.imgood.textech.webae.player.PlayerOnlineSampler.instance()
                .onServerTick(now);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Player tracker tick failed", t);
        }
    }
}
