package com.imgood.textech.handler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Maintains an O(1) UUID→EntityPlayerMP index
 * ({@link ConcurrentHashMap}) to replace the O(n) player list scan
 * in snapshot collection, owner context resolution, and other
 * frequently called lookups.</p>
 *
 * <p>Also ticks the store's debounced save from the server tick event so that
 * rapid login/logout bursts do not cause excessive disk writes.</p>
 */
public class HandlerWebPlayerTracker {

    /** O(1) index: uuid string → online player reference. */
    private static final ConcurrentHashMap<String, WeakReference<EntityPlayerMP>> onlinePlayerIndex =
        new ConcurrentHashMap<String, WeakReference<EntityPlayerMP>>();

    /**
     * Look up an online player by UUID string in O(1) time.
     * Returns {@code null} when the player is offline or the weak reference has been collected.
     */
    public static EntityPlayerMP findOnlinePlayer(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        WeakReference<EntityPlayerMP> ref = onlinePlayerIndex.get(uuid);
        if (ref == null) return null;
        EntityPlayerMP player = ref.get();
        if (player == null) {
            onlinePlayerIndex.remove(uuid);
            return null;
        }
        return player;
    }

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
            if (event.player instanceof EntityPlayerMP) {
                onlinePlayerIndex.put(uuid.toString(), new WeakReference<EntityPlayerMP>((EntityPlayerMP) event.player));
            }
            AdvanceDataMonitor.LOG.info("[WebAE] Player logged in: {} ({})", name, uuid);
            com.imgood.textech.webae.context.NetworkRegistry.onPlayerLogin(uuid.toString());
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
            onlinePlayerIndex.remove(uuid.toString());
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
                        UUID id = ((EntityPlayerMP) obj).getUniqueID();
                        online.add(id);
                        // ensure index is up-to-date for any player that may have been missed
                        if (!onlinePlayerIndex.containsKey(id.toString())) {
                            onlinePlayerIndex.put(id.toString(), new WeakReference<EntityPlayerMP>((EntityPlayerMP) obj));
                        }
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
            com.imgood.textech.webae.context.NetworkRegistry.tickHealthCheck(now);
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Player tracker tick failed", t);
        }
    }
}
