package com.imgood.textech.assistant;

/**
 * Client-side cache of the AI assistant menu state, refreshed from the server
 * via {@code PacketAssistantMenuStateResponse}. Holds per-connector-type
 * availability and AE network permission flags so the feature menu can grey
 * out features whose connector is missing and red out features whose AE
 * security terminal denies the player.
 *
 * 显示名称 / Display names:
 * - EN: Assistant Menu State
 * - ZH: 助手菜单状态
 */
public final class AssistantMenuState {

    private boolean craftingAvailable;
    private boolean craftingHasPermission;
    private boolean storageAvailable;
    private boolean storageHasPermission;
    private boolean networkAvailable;
    private boolean networkHasPermission;
    private long timestampMs;
    private boolean valid;

    public AssistantMenuState() {
        // Default: everything available and permitted (used before first query).
        this.craftingAvailable = true;
        this.craftingHasPermission = true;
        this.storageAvailable = true;
        this.storageHasPermission = true;
        this.networkAvailable = true;
        this.networkHasPermission = true;
        this.valid = false;
        this.timestampMs = 0L;
    }

    public boolean isCraftingAvailable() {
        return craftingAvailable;
    }

    public boolean isCraftingHasPermission() {
        return craftingHasPermission;
    }

    public boolean isStorageAvailable() {
        return storageAvailable;
    }

    public boolean isStorageHasPermission() {
        return storageHasPermission;
    }

    public boolean isNetworkAvailable() {
        return networkAvailable;
    }

    public boolean isNetworkHasPermission() {
        return networkHasPermission;
    }

    public boolean isValid() {
        return valid;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public void setCrafting(boolean available, boolean hasPermission) {
        this.craftingAvailable = available;
        this.craftingHasPermission = hasPermission;
    }

    public void setStorage(boolean available, boolean hasPermission) {
        this.storageAvailable = available;
        this.storageHasPermission = hasPermission;
    }

    public void setNetwork(boolean available, boolean hasPermission) {
        this.networkAvailable = available;
        this.networkHasPermission = hasPermission;
    }

    public void markValid() {
        this.valid = true;
        this.timestampMs = System.currentTimeMillis();
    }

    public void invalidate() {
        this.valid = false;
    }

    /**
     * Returns whether a feature with the given requiredConnector is usable.
     * Connector values: "none", "crafting", "storage", "network".
     * Returns true when connector is "none" or when state is not yet valid
     * (fail-open before the first server response arrives).
     */
    public boolean isFeatureAvailable(String requiredConnector) {
        if (requiredConnector == null || requiredConnector.isEmpty() || "none".equals(requiredConnector)) {
            return true;
        }
        if (!valid) {
            return true;
        }
        if ("crafting".equals(requiredConnector)) {
            return craftingAvailable;
        }
        if ("storage".equals(requiredConnector)) {
            return storageAvailable;
        }
        if ("network".equals(requiredConnector)) {
            return networkAvailable;
        }
        return true;
    }

    /**
     * Returns whether the player has AE network permission for a feature with
     * the given requiredConnector. Fail-open when connector is "none" or state
     * is not yet valid.
     */
    public boolean isFeaturePermitted(String requiredConnector) {
        if (requiredConnector == null || requiredConnector.isEmpty() || "none".equals(requiredConnector)) {
            return true;
        }
        if (!valid) {
            return true;
        }
        if ("crafting".equals(requiredConnector)) {
            return craftingHasPermission;
        }
        if ("storage".equals(requiredConnector)) {
            return storageHasPermission;
        }
        if ("network".equals(requiredConnector)) {
            return networkHasPermission;
        }
        return true;
    }

    /**
     * Returns true only when the feature can actually be used: connector
     * present AND player has permission.
     */
    public boolean isFeatureUsable(String requiredConnector) {
        return isFeatureAvailable(requiredConnector) && isFeaturePermitted(requiredConnector);
    }
}
