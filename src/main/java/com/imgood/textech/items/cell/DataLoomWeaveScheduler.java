package com.imgood.textech.items.cell;

/**
 * Server-tick scheduler for {@link DataLoomWeaveEngine}. Completely independent of AE inventory polling.
 */
public final class DataLoomWeaveScheduler {

    private static int weaveCooldown;

    private DataLoomWeaveScheduler() {}

    public static void onServerTick() {
        weaveCooldown++;

        if (weaveCooldown < DataLoomCellUtil.getSyncIntervalTicks()) {
            return;
        }
        weaveCooldown = 0;
        DataLoomWeaveEngine.runScheduledPass();
    }
}
