package com.imgood.textech.handler;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Spreads starry-sword entity spawns across server ticks to avoid single-tick hitches
 * (noticeable as client freezes in integrated server).
 */
public final class StarrySwordSpawnScheduler {

    private static final int MAX_SPAWNS_PER_TICK = 8;

    private static final Queue<Runnable> PENDING = new ArrayDeque<Runnable>();

    private StarrySwordSpawnScheduler() {}

    public static void schedule(Runnable spawnTask) {
        if (spawnTask != null) {
            PENDING.offer(spawnTask);
        }
    }

    public static void drainServerTick() {
        Runnable task;
        int processed = 0;
        while ((task = PENDING.poll()) != null && processed++ < MAX_SPAWNS_PER_TICK) {
            task.run();
        }
    }
}
