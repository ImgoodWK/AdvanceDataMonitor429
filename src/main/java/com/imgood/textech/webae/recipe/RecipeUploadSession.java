package com.imgood.textech.webae.recipe;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight recipe upload sessions per player so only the first {@code isStart}
 * batch clears memory; concurrent or overlapping uploads from the same player are ignored.
 */
public final class RecipeUploadSession {

    static final long SESSION_TTL_MS = 120000L;
    private static final ConcurrentHashMap<String, SessionState> ACTIVE = new ConcurrentHashMap<String, SessionState>();

    private RecipeUploadSession() {}

    /**
     * Accepts one batch only when it is the next batch in the active session.
     * The server handler calls this after parsing and validating the bounded
     * JSON payload, so rejected or malformed batches cannot advance state.
     */
    public static synchronized BatchDecision acceptBatch(String playerUuid, int batchIndex, int totalBatches,
        boolean isStart, boolean isEnd) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        if (playerUuid == null || playerUuid.isEmpty()
            || totalBatches < 1
            || batchIndex < 0
            || batchIndex >= totalBatches) {
            return BatchDecision.rejected();
        }
        SessionState state = ACTIVE.get(playerUuid);
        boolean newSession = false;
        if (isStart) {
            if (batchIndex != 0 || state != null) {
                return BatchDecision.rejected();
            }
            state = new SessionState(totalBatches, now);
            ACTIVE.put(playerUuid, state);
            newSession = true;
        } else if (state == null) {
            return BatchDecision.rejected();
        }
        if (state.totalBatches != totalBatches || state.nextBatchIndex != batchIndex
            || isEnd != (batchIndex == totalBatches - 1)) {
            if (newSession) {
                ACTIVE.remove(playerUuid, state);
            }
            return BatchDecision.rejected();
        }
        state.receivedBatches++;
        state.nextBatchIndex++;
        state.lastTouchedMs = now;
        boolean completed = isEnd;
        if (completed) {
            ACTIVE.remove(playerUuid, state);
        }
        return new BatchDecision(true, newSession, completed);
    }

    public static synchronized void abort(String playerUuid) {
        if (playerUuid != null) {
            ACTIVE.remove(playerUuid);
        }
    }

    /**
     * @return {@code true} when this {@code isStart} batch begins a new session and the cache should be cleared
     */
    public static synchronized boolean onStart(String playerUuid, int totalBatches) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        if (playerUuid == null || playerUuid.isEmpty() || totalBatches < 1) {
            return false;
        }
        SessionState state = ACTIVE.get(playerUuid);
        if (state != null && !state.completed) {
            return false;
        }
        ACTIVE.put(playerUuid, new SessionState(totalBatches, now));
        return true;
    }

    public static synchronized void onBatch(String playerUuid) {
        if (playerUuid == null) return;
        long now = System.currentTimeMillis();
        pruneExpired(now);
        SessionState state = ACTIVE.get(playerUuid);
        if (state != null) {
            state.receivedBatches++;
            state.lastTouchedMs = now;
        }
    }

    /**
     * @return {@code true} when this was the final batch of an active session
     */
    public static synchronized boolean onEnd(String playerUuid) {
        if (playerUuid == null) return false;
        pruneExpired(System.currentTimeMillis());
        SessionState state = ACTIVE.remove(playerUuid);
        if (state == null) return false;
        state.completed = true;
        return true;
    }

    public static synchronized boolean isActive(String playerUuid) {
        if (playerUuid == null) return false;
        pruneExpired(System.currentTimeMillis());
        SessionState state = ACTIVE.get(playerUuid);
        return state != null && !state.completed;
    }

    static synchronized void pruneExpired(long nowMs) {
        for (java.util.Map.Entry<String, SessionState> entry : ACTIVE.entrySet()) {
            SessionState state = entry.getValue();
            if (state != null && nowMs - state.lastTouchedMs > SESSION_TTL_MS) {
                ACTIVE.remove(entry.getKey(), state);
            }
        }
    }

    static synchronized void clearAllForTests() {
        ACTIVE.clear();
    }

    private static final class SessionState {

        final int totalBatches;
        int receivedBatches;
        int nextBatchIndex;
        boolean completed;
        long lastTouchedMs;

        SessionState(int totalBatches, long lastTouchedMs) {
            this.totalBatches = totalBatches;
            this.lastTouchedMs = lastTouchedMs;
        }
    }

    public static final class BatchDecision {

        private static final BatchDecision REJECTED = new BatchDecision(false, false, false);

        public final boolean accepted;
        public final boolean newSession;
        public final boolean completed;

        private BatchDecision(boolean accepted, boolean newSession, boolean completed) {
            this.accepted = accepted;
            this.newSession = newSession;
            this.completed = completed;
        }

        private static BatchDecision rejected() {
            return REJECTED;
        }
    }
}
