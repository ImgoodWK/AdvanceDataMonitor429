package com.imgood.textech.webae.icon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    /** After this many dispatches without upload ack, stop re-queueing for a while. */
    private static final int MAX_DISPATCH_ATTEMPTS = 2;
    /** Cooldown when client reports unresolvable or attempts are exhausted (30 min). */
    private static final long COOLDOWN_MS = 30L * 60L * 1000L;
    private static final IconMissingQueue INSTANCE = new IconMissingQueue();

    private final Deque<MissingIcon> queue = new ArrayDeque<MissingIcon>();
    private final Set<String> queuedKeys = new LinkedHashSet<String>();
    private final Map<String, DispatchState> dispatchState = new ConcurrentHashMap<String, DispatchState>();
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
            if (isOnCooldown(key)) return;
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
            recordDispatch(missing.key);
            AdvanceDataMonitor.ADMCHANEL
                .sendTo(new PacketWebIconRequest(missing.pack, missing.mode, missing.itemId), provider);
        }
    }

    /** Client could not resolve item id — avoid hammering the provider on every 404. */
    public void markUnresolvable(String pack, String mode, String itemId) {
        if (itemId == null || itemId.isEmpty()) return;
        if (pack == null) pack = "default";
        if (mode == null) mode = IconRenderMode.NEI.getId();
        markUnresolvableKey(pack, mode, itemId);
        for (String candidate : IconItemId.lookupCandidates(itemId)) {
            markUnresolvableKey(pack, mode, candidate);
        }
        for (IconRenderMode renderMode : IconRenderMode.values()) {
            String modeId = renderMode.getId();
            if (!modeId.equals(mode)) {
                markUnresolvableKey(pack, modeId, itemId);
            }
        }
    }

    private void markUnresolvableKey(String pack, String mode, String itemId) {
        String key = pack + "|" + mode + "|" + itemId;
        long until = System.currentTimeMillis() + COOLDOWN_MS;
        DispatchState state = new DispatchState();
        state.cooldownUntilMs = until;
        state.attempts = MAX_DISPATCH_ATTEMPTS;
        dispatchState.put(key, state);
        synchronized (this) {
            queuedKeys.remove(key);
        }
    }

    public void acknowledge(String pack, String mode, String itemId) {
        if (itemId == null) return;
        if (pack == null) pack = "default";
        if (mode == null) mode = IconRenderMode.NEI.getId();
        String key = pack + "|" + mode + "|" + itemId;
        dispatchState.remove(key);
        synchronized (this) {
            queuedKeys.remove(key);
        }
    }

    public int pendingCount() {
        synchronized (this) {
            return queue.size();
        }
    }

    public void clear() {
        synchronized (this) {
            queue.clear();
            queuedKeys.clear();
        }
        dispatchState.clear();
    }

    private boolean isOnCooldown(String key) {
        DispatchState state = dispatchState.get(key);
        if (state == null) return false;
        return System.currentTimeMillis() < state.cooldownUntilMs;
    }

    private void recordDispatch(String key) {
        long now = System.currentTimeMillis();
        DispatchState state = dispatchState.get(key);
        if (state == null) {
            state = new DispatchState();
            dispatchState.put(key, state);
        }
        state.attempts++;
        state.lastAttemptMs = now;
        if (state.attempts >= MAX_DISPATCH_ATTEMPTS) {
            state.cooldownUntilMs = now + COOLDOWN_MS;
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

    private static final class DispatchState {

        int attempts;
        long lastAttemptMs;
        long cooldownUntilMs;
    }
}
