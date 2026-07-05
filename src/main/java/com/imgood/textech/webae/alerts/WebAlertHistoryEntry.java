package com.imgood.textech.webae.alerts;

/**
 * One occurrence of a WebAE alert (active or cleared), exposed via GET /api/alerts/history.
 */
public final class WebAlertHistoryEntry {

    public String id = "";
    public String type = "";
    public String severity = "warning";
    public String title = "";
    public String message = "";
    public long firstSeenAt;
    public long lastSeenAt;
    /** 0 when still active. */
    public long clearedAt;
    public int networkId = -1;
    public String sourceKey = "";
    public boolean active;
}
