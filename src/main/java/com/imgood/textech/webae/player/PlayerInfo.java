package com.imgood.textech.webae.player;

/**
 * Player info DTO persisted by {@link PlayerInfoStore} and surfaced via the
 * WebAE {@code /api/players} endpoint.
 *
 * <p>
 * Fields:
 * </p>
 * <ul>
 * <li>{@code uuid} — player UUID string</li>
 * <li>{@code name} — last known username</li>
 * <li>{@code firstLogin} — epoch ms of first observed login</li>
 * <li>{@code lastLogin} — epoch ms of most recent login</li>
 * <li>{@code lastLogout} — epoch ms of most recent logout (0 if never logged out)</li>
 * <li>{@code totalOnlineMs} — cumulative online time across all sessions</li>
 * <li>{@code online} — true when the player is currently online</li>
 * </ul>
 */
public class PlayerInfo {

    public String uuid;
    public String name;
    public long firstLogin;
    public long lastLogin;
    public long lastLogout;
    public long totalOnlineMs;
    public boolean online;

    public PlayerInfo() {}

    public PlayerInfo(String uuid, String name, long firstLogin, long lastLogin, long lastLogout, long totalOnlineMs,
        boolean online) {
        this.uuid = uuid;
        this.name = name;
        this.firstLogin = firstLogin;
        this.lastLogin = lastLogin;
        this.lastLogout = lastLogout;
        this.totalOnlineMs = totalOnlineMs;
        this.online = online;
    }
}
