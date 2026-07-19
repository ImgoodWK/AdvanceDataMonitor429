package com.imgood.textech.webae.alerts;

/**
 * Active WebAE alert DTO exposed via GET /api/alerts.
 */
public final class WebAlertDto {

    public String id = "";
    public String type = "";
    public String severity = "warning";
    public String title = "";
    public String message = "";
    public long timestamp;
    public int networkId = -1;
    public boolean acknowledged;
    public String sourceKey = "";
    /** Whether WebAE should show its existing toast/browser notification for this occurrence. */
    public boolean browserNotify = true;
}
