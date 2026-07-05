package com.imgood.textech.webae.dto;

/**
 * Player DTO returned by the WebAE {@code /api/players} endpoint. Mirrors
 * {@link com.imgood.textech.webae.player.PlayerInfo} but adds the resolved
 * {@code skinUrl} and a live {@code onlineMs} (cumulative + current session).
 */
public class PlayerDto {

    public String uuid;
    public String name;
    public boolean online;
    public long onlineMs;
    public long lastLogin;
    public long lastLogout;
    /** Mojang skin texture URL or {@code null} for offline/unknown players. */
    public String skinUrl;

    public PlayerDto() {}

    public PlayerDto(String uuid, String name, boolean online, long onlineMs, long lastLogin, long lastLogout,
        String skinUrl) {
        this.uuid = uuid;
        this.name = name;
        this.online = online;
        this.onlineMs = onlineMs;
        this.lastLogin = lastLogin;
        this.lastLogout = lastLogout;
        this.skinUrl = skinUrl;
    }
}
