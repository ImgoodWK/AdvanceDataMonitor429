package com.imgood.textech.handler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks storage links that are actively consumed by data monitors so periodic refresh can be skipped otherwise.
 */
public final class StorageLinkWatchRegistry {

    private static final Set<String> WATCHED = Collections.synchronizedSet(new HashSet<String>());

    private StorageLinkWatchRegistry() {}

    public static void setWatched(int dimensionId, int x, int y, int z, boolean watched) {
        String key = key(dimensionId, x, y, z);
        if (watched) {
            WATCHED.add(key);
        } else {
            WATCHED.remove(key);
        }
    }

    public static boolean isWatched(int dimensionId, int x, int y, int z) {
        return WATCHED.contains(key(dimensionId, x, y, z));
    }

    public static void clearAll() {
        WATCHED.clear();
    }

    private static String key(int dimensionId, int x, int y, int z) {
        return dimensionId + ":" + x + ":" + y + ":" + z;
    }
}
