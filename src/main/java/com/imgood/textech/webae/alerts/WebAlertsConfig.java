package com.imgood.textech.webae.alerts;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed config/textech/web-alerts.json (automation rules).
 */
public final class WebAlertsConfig {

    public int version = 1;
    public boolean enabled = true;
    public int pollIntervalSeconds = 10;
    public List<InventoryThresholdRule> inventoryThresholds = new ArrayList<InventoryThresholdRule>();
    public int cpuStuckMinutes = 5;
    public boolean gtErrorEnabled = true;
    public boolean orderCompleteEnabled = true;
    public int channelThresholdPercent = 90;
    public int channelThresholdAbsolute = 28;

    public static final class InventoryThresholdRule {

        public String itemId = "";
        public String fluidName = "";
        public long minAmount = 0L;
        public int networkId = -1;
        public String label = "";
    }
}
