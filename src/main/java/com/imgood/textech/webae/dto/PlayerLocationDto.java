package com.imgood.textech.webae.dto;

/**
 * Player world position for {@code GET /api/players/locations} (Phase 6.1).
 */
public final class PlayerLocationDto {

    public String uuid;
    public String name;
    public int x;
    public int y;
    public int z;
    public int dim;
    public boolean online;

    public PlayerLocationDto() {}

    public PlayerLocationDto(String uuid, String name, int x, int y, int z, int dim, boolean online) {
        this.uuid = uuid;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.online = online;
    }
}
