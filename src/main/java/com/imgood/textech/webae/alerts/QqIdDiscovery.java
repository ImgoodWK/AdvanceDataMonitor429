package com.imgood.textech.webae.alerts;

/**
 * One QQ openid / channel id captured while an ID-probe session is listening.
 */
public final class QqIdDiscovery {

    /** {@code c2c}, {@code group}, or {@code channel}. */
    public String kind = "";
    public String targetId = "";
    public String eventType = "";
    /** Short message/content preview for the UI. */
    public String preview = "";
    public long seenAtMs;

    public QqIdDiscovery() {}

    public QqIdDiscovery(String kind, String targetId, String eventType, String preview, long seenAtMs) {
        this.kind = kind == null ? "" : kind;
        this.targetId = targetId == null ? "" : targetId;
        this.eventType = eventType == null ? "" : eventType;
        this.preview = preview == null ? "" : preview;
        this.seenAtMs = seenAtMs;
    }

    public String dedupeKey() {
        return kind + "|" + targetId;
    }
}
