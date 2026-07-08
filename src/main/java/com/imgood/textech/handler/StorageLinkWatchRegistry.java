package com.imgood.textech.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks storage links that are actively consumed by data monitors so periodic refresh can be skipped otherwise.
 */
public final class StorageLinkWatchRegistry {

    private static final Map<String, Integer> WATCH_COUNTS = Collections
        .synchronizedMap(new HashMap<String, Integer>());

    private StorageLinkWatchRegistry() {}

    public static void acquire(int dimensionId, int x, int y, int z) {
        String key = key(dimensionId, x, y, z);
        synchronized (WATCH_COUNTS) {
            Integer count = WATCH_COUNTS.get(key);
            WATCH_COUNTS.put(key, count == null ? 1 : count + 1);
        }
    }

    public static void release(int dimensionId, int x, int y, int z) {
        String key = key(dimensionId, x, y, z);
        synchronized (WATCH_COUNTS) {
            Integer count = WATCH_COUNTS.get(key);
            if (count == null) {
                return;
            }
            if (count <= 1) {
                WATCH_COUNTS.remove(key);
            } else {
                WATCH_COUNTS.put(key, count - 1);
            }
        }
    }

    /** @deprecated Use {@link #acquire(int, int, int, int)} / {@link #release(int, int, int, int)}. */
    @Deprecated
    public static void setWatched(int dimensionId, int x, int y, int z, boolean watched) {
        if (watched) {
            acquire(dimensionId, x, y, z);
        } else {
            release(dimensionId, x, y, z);
        }
    }

    public static boolean isWatched(int dimensionId, int x, int y, int z) {
        synchronized (WATCH_COUNTS) {
            Integer count = WATCH_COUNTS.get(key(dimensionId, x, y, z));
            return count != null && count > 0;
        }
    }

    public static void clearAll() {
        WATCH_COUNTS.clear();
    }

    static int watchedCountForTests() {
        synchronized (WATCH_COUNTS) {
            int total = 0;
            for (Integer count : WATCH_COUNTS.values()) {
                if (count != null) {
                    total += count;
                }
            }
            return total;
        }
    }

    private static String key(int dimensionId, int x, int y, int z) {
        return dimensionId + ":" + x + ":" + y + ":" + z;
    }
}
