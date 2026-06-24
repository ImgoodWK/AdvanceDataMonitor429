package com.imgood.advancedatamonitor.items.cell;

/**
 * Server-tick scheduler for {@link DataLoomWeaveEngine}. Completely independent of AE inventory polling.
 */
public final class DataLoomWeaveScheduler {

    private static int weaveCooldown;
    private static int rescanCooldown;

    private DataLoomWeaveScheduler() {}

    public static void onServerTick() {
        weaveCooldown++;
        rescanCooldown++;

        if (rescanCooldown >= 20) {
            rescanCooldown = 0;
            DataLoomCellIndex.INSTANCE.rescanLoadedHosts();
        }

        if (weaveCooldown < DataLoomCellUtil.getSyncIntervalTicks()) {
            return;
        }
        weaveCooldown = 0;
        DataLoomWeaveEngine.runScheduledPass();
    }
}
