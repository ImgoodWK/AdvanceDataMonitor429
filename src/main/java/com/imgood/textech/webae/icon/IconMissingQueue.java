package com.imgood.textech.webae.icon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
 * Server-side queue of missing icon keys. Dispatches render requests only when
 * {@link Config#webIconLazyCaptureEnabled} is on and a provider has consented.
 */
public final class IconMissingQueue {

    private static final int MAX_QUEUE = 4096;
    /** After this many dispatches without upload ack, stop re-queueing for a while. */
    private static final int MAX_DISPATCH_ATTEMPTS = 2;
    /** Cooldown when client reports unresolvable or attempts are exhausted (30 min). */
    private static final long COOLDOWN_MS = 30L * 60L * 1000L;
    /** Consented provider session timeout (30 min). */
    private static final long PROVIDER_SESSION_MS = 30L * 60L * 1000L;
    /** Min interval between consent offers to the same candidate. */
    private static final long CONSENT_OFFER_COOLDOWN_MS = 60L * 1000L;
    private static final IconMissingQueue INSTANCE = new IconMissingQueue();

    private final Deque<MissingIcon> queue = new ArrayDeque<MissingIcon>();
    private final Set<String> queuedKeys = new LinkedHashSet<String>();
    private final Map<String, DispatchState> dispatchState = new ConcurrentHashMap<String, DispatchState>();
    private final Set<String> declinedUuids = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private volatile String preferredProviderUuid;
    private volatile String consentedProviderUuid;
    private volatile String consentedProviderName;
    private volatile long consentedUntilMs;
    private volatile boolean capturedWithClientTextures;
    private long lastDispatchMs;
    private long lastConsentOfferMs;
    private String lastConsentOfferUuid;

    private IconMissingQueue() {}

    public static IconMissingQueue instance() {
        return INSTANCE;
    }

    /** Remember last OP uploader as preferred provider for lazy consent. */
    public void setProviderUuid(String uuid) {
        if (uuid != null && !uuid.isEmpty()) {
            this.preferredProviderUuid = uuid;
        }
    }

    public String getProviderName() {
        return consentedProviderName;
    }

    public boolean isCapturedWithClientTextures() {
        return capturedWithClientTextures && consentedProviderUuid != null
            && System.currentTimeMillis() < consentedUntilMs;
    }

    public boolean acceptConsent(EntityPlayerMP player) {
        if (player == null) return false;
        String uuid = player.getUniqueID()
            .toString();
        declinedUuids.remove(uuid);
        consentedProviderUuid = uuid;
        consentedProviderName = player.getCommandSenderName();
        consentedUntilMs = System.currentTimeMillis() + PROVIDER_SESSION_MS;
        capturedWithClientTextures = true;
        preferredProviderUuid = uuid;
        return true;
    }

    public boolean rejectConsent(EntityPlayerMP player) {
        if (player == null) return false;
        String uuid = player.getUniqueID()
            .toString();
        declinedUuids.add(uuid);
        if (uuid.equals(consentedProviderUuid)) {
            clearConsent();
        }
        lastConsentOfferMs = 0L;
        lastConsentOfferUuid = null;
        return true;
    }

    public void clearConsent() {
        consentedProviderUuid = null;
        consentedProviderName = null;
        consentedUntilMs = 0L;
        capturedWithClientTextures = false;
    }

    public void enqueue(String pack, String mode, String itemId) {
        if (!Config.webIconCacheEnabled || !Config.webIconLazyCaptureEnabled) return;
        if (itemId == null || itemId.isEmpty()) return;
        if (pack == null || pack.isEmpty()) pack = "default";
        // Active path is nei-only; ignore requested mode.
        mode = IconRenderMode.NEI.getId();
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
        if (!Config.webIconCacheEnabled || !Config.webIconLazyCaptureEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastDispatchMs < 250L) return;
        lastDispatchMs = now;

        if (pendingCount() <= 0) return;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        EntityPlayerMP provider = resolveConsentedProvider(server, now);
        if (provider == null) {
            maybeOfferConsent(server, now);
            return;
        }

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

    /** Pick an online consented provider (or null if consent still required). */
    public EntityPlayerMP resolveProviderPlayer() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        return resolveConsentedProvider(server, System.currentTimeMillis());
    }

    public boolean isConsentedProvider(EntityPlayerMP player) {
        if (player == null) {
            return false;
        }
        EntityPlayerMP provider = resolveProviderPlayer();
        return provider != null && provider.getUniqueID()
            .equals(player.getUniqueID());
    }

    private EntityPlayerMP resolveConsentedProvider(MinecraftServer server, long now) {
        if (consentedProviderUuid == null || now >= consentedUntilMs) {
            if (consentedProviderUuid != null) {
                clearConsent();
            }
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : players) {
            if (player != null && consentedProviderUuid.equals(
                player.getUniqueID()
                    .toString())) {
                return player;
            }
        }
        clearConsent();
        return null;
    }

    private void maybeOfferConsent(MinecraftServer server, long now) {
        if (now - lastConsentOfferMs < CONSENT_OFFER_COOLDOWN_MS) return;
        EntityPlayerMP candidate = pickConsentCandidate(server);
        if (candidate == null) return;
        String uuid = candidate.getUniqueID()
            .toString();
        if (uuid.equals(lastConsentOfferUuid) && now - lastConsentOfferMs < CONSENT_OFFER_COOLDOWN_MS) {
            return;
        }
        lastConsentOfferMs = now;
        lastConsentOfferUuid = uuid;
        IconLazyConsentChat.sendOffer(candidate, pendingCount());
    }

    private EntityPlayerMP pickConsentCandidate(MinecraftServer server) {
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        if (players == null || players.isEmpty()) return null;

        if (preferredProviderUuid != null) {
            EntityPlayerMP preferred = findByUuid(players, preferredProviderUuid);
            if (preferred != null && !declinedUuids.contains(preferredProviderUuid)
                && isEligibleProvider(server, preferred)) {
                return preferred;
            }
        }

        for (EntityPlayerMP player : players) {
            if (player == null) continue;
            String uuid = player.getUniqueID()
                .toString();
            if (declinedUuids.contains(uuid)) continue;
            if (!isEligibleProvider(server, player)) continue;
            if (server.getConfigurationManager()
                .func_152596_g(player.getGameProfile())) {
                return player;
            }
        }

        if (Config.webIconLazyPreferOpOnly) {
            return null;
        }

        for (EntityPlayerMP player : players) {
            if (player == null) continue;
            String uuid = player.getUniqueID()
                .toString();
            if (declinedUuids.contains(uuid)) continue;
            return player;
        }
        return null;
    }

    private boolean isEligibleProvider(MinecraftServer server, EntityPlayerMP player) {
        if (!Config.webIconLazyPreferOpOnly) return true;
        return server.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }

    private static EntityPlayerMP findByUuid(List<EntityPlayerMP> players, String uuid) {
        for (EntityPlayerMP player : players) {
            if (player != null && uuid.equals(
                player.getUniqueID()
                    .toString())) {
                return player;
            }
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
