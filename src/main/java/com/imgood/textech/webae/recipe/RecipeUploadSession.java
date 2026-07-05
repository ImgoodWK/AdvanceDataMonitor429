package com.imgood.textech.webae.recipe;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight recipe upload sessions per player so only the first {@code isStart}
 * batch clears memory; concurrent or overlapping uploads from the same player are ignored.
 */
public final class RecipeUploadSession {

    private static final ConcurrentHashMap<String, SessionState> ACTIVE = new ConcurrentHashMap<String, SessionState>();

    private RecipeUploadSession() {}

    /**
     * @return {@code true} when this {@code isStart} batch begins a new session and the cache should be cleared
     */
    public static boolean onStart(String playerUuid, int totalBatches) {
        if (playerUuid == null || playerUuid.isEmpty()) {
            return false;
        }
        SessionState state = ACTIVE.get(playerUuid);
        if (state != null && !state.completed) {
            return false;
        }
        ACTIVE.put(playerUuid, new SessionState(totalBatches));
        return true;
    }

    public static void onBatch(String playerUuid) {
        if (playerUuid == null) return;
        SessionState state = ACTIVE.get(playerUuid);
        if (state != null) {
            state.receivedBatches++;
        }
    }

    /**
     * @return {@code true} when this was the final batch of an active session
     */
    public static boolean onEnd(String playerUuid) {
        if (playerUuid == null) return false;
        SessionState state = ACTIVE.remove(playerUuid);
        if (state == null) return false;
        state.completed = true;
        return true;
    }

    public static boolean isActive(String playerUuid) {
        if (playerUuid == null) return false;
        SessionState state = ACTIVE.get(playerUuid);
        return state != null && !state.completed;
    }

    private static final class SessionState {

        final int totalBatches;
        int receivedBatches;
        boolean completed;

        SessionState(int totalBatches) {
            this.totalBatches = totalBatches;
        }
    }
}
