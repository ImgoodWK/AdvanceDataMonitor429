package com.imgood.textech.webae.icon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.network.PacketWebIconRequest;

/**
 * Server-side queue of missing icon keys. Dispatches render requests to an online icon-provider client.
 */
public final class IconMissingQueue {

    private static final int MAX_QUEUE = 4096;
    private static final IconMissingQueue INSTANCE = new IconMissingQueue();

    private final Deque<MissingIcon> queue = new ArrayDeque<MissingIcon>();
    private final Set<String> queuedKeys = new LinkedHashSet<String>();
    private volatile String providerUuid;
    private long lastDispatchMs;

    private IconMissingQueue() {}

    public static IconMissingQueue instance() {
        return INSTANCE;
    }

    public void setProviderUuid(String uuid) {
        if (uuid != null && !uuid.isEmpty()) {
            this.providerUuid = uuid;
        }
    }

    public void enqueue(String pack, String mode, String itemId) {
        if (!Config.webIconCacheEnabled || !Config.webIconUploadEnabled) return;
        if (itemId == null || itemId.isEmpty()) return;
        if (pack == null || pack.isEmpty()) pack = "default";
        if (mode == null || mode.isEmpty()) mode = IconRenderMode.NEI.getId();
        if (!IconStore.isValidPackName(pack)) return;

        String key = pack + "|" + mode + "|" + itemId;
        synchronized (this) {
            if (queuedKeys.contains(key)) return;
            if (queue.size() >= MAX_QUEUE) {
                MissingIcon dropped = queue.pollFirst();
                if (dropped != null) queuedKeys.remove(dropped.key);
            }
            MissingIcon entry = new MissingIcon(pack, mode, itemId, key);
            queue.offerLast(entry);
            queuedKeys.add(key);
        }
    }

    public void onServerTick() {
        if (!Config.webIconCacheEnabled || !Config.webIconUploadEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastDispatchMs < 250L) return;
        lastDispatchMs = now;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        EntityPlayerMP provider = resolveProvider(server);
        if (provider == null) return;

        List<MissingIcon> batch = new ArrayList<MissingIcon>();
        int dispatchPerTick = Config.webIconMissingDispatchPerTick;
        if (dispatchPerTick <= 0) dispatchPerTick = 8;
        synchronized (this) {
            while (batch.size() < dispatchPerTick && !queue.isEmpty()) {
                MissingIcon next = queue.pollFirst();
                if (next != null) {
                    queuedKeys.remove(next.key);
                    batch.add(next);
                }
            }
        }
        for (MissingIcon missing : batch) {
            AdvanceDataMonitor.ADMCHANEL
                .sendTo(new PacketWebIconRequest(missing.pack, missing.mode, missing.itemId), provider);
        }
    }

    public void acknowledge(String pack, String mode, String itemId) {
        if (itemId == null) return;
        if (pack == null) pack = "default";
        if (mode == null) mode = IconRenderMode.NEI.getId();
        String key = pack + "|" + mode + "|" + itemId;
        synchronized (this) {
            queuedKeys.remove(key);
        }
    }

    public int pendingCount() {
        synchronized (this) {
            return queue.size();
        }
    }

    private EntityPlayerMP resolveProvider(MinecraftServer server) {
        if (providerUuid != null) {
            @SuppressWarnings("unchecked")
            List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
            for (EntityPlayerMP player : players) {
                if (player != null && providerUuid.equals(
                    player.getUniqueID()
                        .toString())) {
                    return player;
                }
            }
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : players) {
            if (player != null && server.getConfigurationManager()
                .func_152596_g(player.getGameProfile())) {
                return player;
            }
        }
        if (!players.isEmpty()) {
            return players.get(0);
        }
        return null;
    }

    private static final class MissingIcon {

        final String pack;
        final String mode;
        final String itemId;
        final String key;

        MissingIcon(String pack, String mode, String itemId, String key) {
            this.pack = pack;
            this.mode = mode;
            this.itemId = itemId;
            this.key = key;
        }
    }
}
