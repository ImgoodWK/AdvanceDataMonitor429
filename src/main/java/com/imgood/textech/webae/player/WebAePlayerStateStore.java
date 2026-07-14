package com.imgood.textech.webae.player;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.TeXTechDataDir;

/**
 * Singleton store for per-player WebAE admin state (disabled flag, activity
 * counters, etc.). Persists to {@code TeXTech/WebAE/web-player-states.json}.
 *
 * <p>
 * Thread-safety: the in-memory map is a {@link ConcurrentHashMap}. Save
 * scheduling is synchronized on this instance.
 * </p>
 */
public class WebAePlayerStateStore {

    private static final WebAePlayerStateStore INSTANCE = new WebAePlayerStateStore();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static File storeFile() {
        return TeXTechDataDir.webAeFile("web-player-states.json");
    }

    private static final long SAVE_DEBOUNCE_MS = 2000L;

    private final Map<String, WebAePlayerState> states = new ConcurrentHashMap<String, WebAePlayerState>();
    private volatile boolean loaded;
    private volatile boolean dirty;
    private long lastSaveAt;
    private long nextScheduledSaveAt;

    private WebAePlayerStateStore() {}

    public static WebAePlayerStateStore getInstance() {
        return INSTANCE;
    }

    /**
     * Get or create the state entry for a player.
     */
    private WebAePlayerState getOrCreate(String playerUuid, String playerName) {
        ensureLoaded();
        WebAePlayerState state = states.get(playerUuid);
        if (state == null) {
            state = new WebAePlayerState(playerUuid, playerName);
            states.put(playerUuid, state);
        } else if (playerName != null && !playerName.isEmpty()
            && (state.playerName == null || state.playerName.isEmpty())) {
            state.playerName = playerName;
        }
        return state;
    }

    /**
     * Record an API request for a player, updating last-active time and
     * cumulative counters.
     */
    public void touchRequest(String playerUuid, long responseMs) {
        if (playerUuid == null) return;
        WebAePlayerState state = states.get(playerUuid);
        if (state == null) {
            state = getOrCreate(playerUuid, null);
        }
        state.lastActiveAt = System.currentTimeMillis();
        state.requestCount++;
        state.totalResponseMs += responseMs;
        scheduleSave();
    }

    /** Disable WebAE for a player. */
    public void setDisabled(String playerUuid, String reason) {
        if (playerUuid == null) return;
        WebAePlayerState state = getOrCreate(playerUuid, null);
        state.disabled = true;
        state.disabledReason = reason;
        state.disabledAt = System.currentTimeMillis();
        scheduleSave();
    }

    /** Enable WebAE for a player. */
    public void setEnabled(String playerUuid) {
        if (playerUuid == null) return;
        WebAePlayerState state = getOrCreate(playerUuid, null);
        state.disabled = false;
        state.disabledReason = null;
        scheduleSave();
    }

    /** Get the state for a player, or null if unknown. */
    public WebAePlayerState getState(String playerUuid) {
        ensureLoaded();
        if (playerUuid == null) return null;
        return states.get(playerUuid);
    }

    /** True when the player has been disabled for WebAE by an admin. */
    public boolean isDisabled(String playerUuid) {
        WebAePlayerState state = getState(playerUuid);
        return state != null && state.disabled;
    }

    /** Get all known player states. */
    public Map<String, WebAePlayerState> getAllStates() {
        ensureLoaded();
        return new java.util.HashMap<String, WebAePlayerState>(states);
    }

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
        Map<String, WebAePlayerState> snapshot = new java.util.HashMap<String, WebAePlayerState>(states);
        String json = GSON.toJson(snapshot);
        java.io.FileWriter fw = null;
        try {
            storeFile().getParentFile().mkdirs();
            java.io.File tmp = new java.io.File(storeFile().getParentFile(), storeFile().getName() + ".tmp");
            fw = new java.io.FileWriter(tmp);
            fw.write(json);
            fw.close();
            fw = null;
            if (storeFile().exists()) {
                storeFile().delete();
            }
            tmp.renameTo(storeFile());
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to save player state store: {}", e.getMessage());
        } finally {
            if (fw != null) {
                try {
                    fw.close();
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
            Map<String, WebAePlayerState> loaded = GSON
                .fromJson(reader, new TypeToken<Map<String, WebAePlayerState>>() {}.getType());
            if (loaded != null) {
                for (Map.Entry<String, WebAePlayerState> e : loaded.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        states.put(e.getKey(), e.getValue());
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to load player state store: {}", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
