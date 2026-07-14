package com.imgood.textech.webae.player;

/**
 * Player WebAE state DTO — persisted per-player admin metadata
 * (disabled flag, activity counters, etc.).
 */
public class WebAePlayerState {

    public String playerUuid;
    public String playerName;
    public boolean disabled;
    public String disabledReason;
    public long disabledAt;
    public long lastActiveAt;      // last API request timestamp
    public long requestCount;      // cumulative request count
    public long totalResponseMs;   // cumulative response time in ms
    public long createdAt;

    public WebAePlayerState() {}

    public WebAePlayerState(String playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.disabled = false;
        this.disabledReason = null;
        this.disabledAt = 0L;
        this.lastActiveAt = 0L;
        this.requestCount = 0L;
        this.totalResponseMs = 0L;
        this.createdAt = System.currentTimeMillis();
    }
}
