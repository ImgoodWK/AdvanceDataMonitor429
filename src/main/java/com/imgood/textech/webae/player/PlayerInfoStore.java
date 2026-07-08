package com.imgood.textech.webae.player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;

/**
 * Server-side in-memory store of per-player metadata for the WebAE
 * {@code /api/players} endpoint. Tracks first/last login, last logout, and
 * cumulative online time; persists to {@code TeXTech/WebAE/web-players.json}
 * with debounce so rapid login/logout bursts do not hammer the disk.
 *
 * <p>
 * Thread-safety: the in-memory map is a {@link ConcurrentHashMap}. Save
 * scheduling is synchronized on this instance. All public methods are safe to
 * call from any thread (event handlers run on the server main thread).
 * </p>
 */
public class PlayerInfoStore {

    private static final PlayerInfoStore INSTANCE = new PlayerInfoStore();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static File storeFile() {
        return TeXTechDataDir.webAeFile("web-players.json");
    }

    /** Debounce window for saves (ms). Saves requested within this window collapse into one. */
    private static final long SAVE_DEBOUNCE_MS = 2000L;

    private final Map<UUID, PlayerInfo> players = new ConcurrentHashMap<UUID, PlayerInfo>();
    private volatile boolean loaded;
    private volatile boolean dirty;
    private long lastSaveAt;
    private long nextScheduledSaveAt;

    private PlayerInfoStore() {}

    public static PlayerInfoStore instance() {
        return INSTANCE;
    }

    /** Record a login event for the given player. */
    public void touchLogin(UUID uuid, String name, long ts) {
        if (uuid == null) return;
        PlayerInfo info = players.get(uuid);
        if (info == null) {
            info = new PlayerInfo();
            info.uuid = uuid.toString();
            info.firstLogin = ts;
            info.totalOnlineMs = 0;
            players.put(uuid, info);
        }
        info.name = name != null ? name : info.name;
        info.lastLogin = ts;
        info.online = true;
        scheduleSave();
    }

    /** Record a logout event for the given player. */
    public void touchLogout(UUID uuid, long ts) {
        if (uuid == null) return;
        PlayerInfo info = players.get(uuid);
        if (info == null) {
            // Unknown player (e.g. store was wiped mid-session) — create a stub.
            info = new PlayerInfo();
            info.uuid = uuid.toString();
            info.firstLogin = ts;
            info.lastLogin = ts;
            info.totalOnlineMs = 0;
            players.put(uuid, info);
        }
        info.online = false;
        info.lastLogout = ts;
        if (info.lastLogin > 0 && ts > info.lastLogin) {
            info.totalOnlineMs += (ts - info.lastLogin);
        }
        scheduleSave();
    }

    /** Reconcile in-memory online status with the actual online player list. */
    public void reconcileOnline(List<UUID> currentlyOnline, long ts) {
        if (currentlyOnline == null) return;
        java.util.Set<UUID> onlineSet = new java.util.HashSet<UUID>(currentlyOnline);
        for (Map.Entry<UUID, PlayerInfo> e : players.entrySet()) {
            boolean isOnline = onlineSet.contains(e.getKey());
            PlayerInfo info = e.getValue();
            if (!isOnline && info.online) {
                // We thought they were online but they are not — record implicit logout.
                info.online = false;
                if (info.lastLogin > 0 && ts > info.lastLogin) {
                    info.totalOnlineMs += (ts - info.lastLogin);
                    info.lastLogout = ts;
                }
            } else if (isOnline && !info.online) {
                // We thought they were offline but they are online — record implicit login.
                info.online = true;
                info.lastLogin = ts;
            }
        }
    }

    /**
     * Recompute the live online duration for an online player and return the
     * effective total online ms (persisted + current session so far).
     */
    public long effectiveOnlineMs(UUID uuid, long now) {
        PlayerInfo info = players.get(uuid);
        if (info == null) return 0;
        long base = info.totalOnlineMs;
        if (info.online && info.lastLogin > 0 && now > info.lastLogin) {
            base += (now - info.lastLogin);
        }
        return base;
    }

    /** @return a snapshot list of all currently-online players (in arbitrary order). */
    public List<PlayerInfo> getOnlinePlayers() {
        ensureLoaded();
        List<PlayerInfo> out = new ArrayList<PlayerInfo>();
        for (PlayerInfo info : players.values()) {
            if (info.online) out.add(copy(info));
        }
        return out;
    }

    /** @return a snapshot list of all known players (online + offline). */
    public List<PlayerInfo> getAllPlayers() {
        ensureLoaded();
        List<PlayerInfo> out = new ArrayList<PlayerInfo>();
        for (PlayerInfo info : players.values()) {
            out.add(copy(info));
        }
        return out;
    }

    /** @return the {@link PlayerInfo} for a UUID, or {@code null} if unknown. */
    public PlayerInfo getPlayer(UUID uuid) {
        ensureLoaded();
        PlayerInfo info = players.get(uuid);
        return info != null ? copy(info) : null;
    }

    private static PlayerInfo copy(PlayerInfo src) {
        return new PlayerInfo(
            src.uuid,
            src.name,
            src.firstLogin,
            src.lastLogin,
            src.lastLogout,
            src.totalOnlineMs,
            src.online);
    }

    /**
     * Mark the store dirty and schedule a debounced save. Safe to call from any
     * thread; actual save is performed by {@link #tickSave(long)} on the server
     * main thread.
     */
    public synchronized void scheduleSave() {
        dirty = true;
        long now = System.currentTimeMillis();
        if (nextScheduledSaveAt == 0) {
            nextScheduledSaveAt = now + SAVE_DEBOUNCE_MS;
        }
    }

    /**
     * Called from the server tick handler. Performs a deferred save when one is
     * scheduled and the debounce window has elapsed.
     */
    public synchronized void tickSave(long now) {
        if (!dirty) return;
        if (now < nextScheduledSaveAt) return;
        saveNow();
        dirty = false;
        nextScheduledSaveAt = 0;
        lastSaveAt = now;
    }

    /** Force an immediate save (e.g. on server stop). */
    public synchronized void saveNow() {
        File parent = storeFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        BufferedWriter writer = null;
        try {
            // Convert to a plain HashMap for stable JSON serialization order.
            Map<String, PlayerInfo> snapshot = new HashMap<String, PlayerInfo>();
            for (Map.Entry<UUID, PlayerInfo> e : players.entrySet()) {
                snapshot.put(
                    e.getKey()
                        .toString(),
                    e.getValue());
            }
            writer = new BufferedWriter(new FileWriter(storeFile(), false));
            GSON.toJson(snapshot, writer);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to save player info store", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!storeFile().isFile()) return;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(storeFile()));
            Map<String, PlayerInfo> loaded = GSON
                .fromJson(reader, new TypeToken<Map<String, PlayerInfo>>() {}.getType());
            if (loaded != null) {
                for (Map.Entry<String, PlayerInfo> e : loaded.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(e.getKey());
                        PlayerInfo info = e.getValue();
                        if (info != null) {
                            info.uuid = uuid.toString();
                            // On reload, treat everyone as offline until proven online.
                            info.online = false;
                            players.put(uuid, info);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load player info store: {}", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
