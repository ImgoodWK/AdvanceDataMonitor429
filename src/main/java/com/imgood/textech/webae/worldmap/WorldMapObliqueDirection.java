package com.imgood.textech.webae.worldmap;

/**
 * 
 * Oblique (mineshot-style ortho) camera orbit direction around chunk center.
 * 
 * Default pitch matches mineshot {@code xRot=30}; yaw matches compass orbit.
 * 
 */

public enum WorldMapObliqueDirection {

    SE("se", "adm.webae.worldmap.oblique.se", -45.0F, 30.0F),

    SW("sw", "adm.webae.worldmap.oblique.sw", -135.0F, 30.0F),

    NE("ne", "adm.webae.worldmap.oblique.ne", 45.0F, 30.0F),

    NW("nw", "adm.webae.worldmap.oblique.nw", 135.0F, 30.0F);

    public final String id;

    public final String labelKey;

    /** Horizontal orbit yaw in degrees (mineshot {@code yRot}). */

    public final float yawDeg;

    /** Downward pitch in degrees (mineshot {@code xRot}). */

    public final float pitchDeg;

    WorldMapObliqueDirection(String id, String labelKey, float yawDeg, float pitchDeg) {

        this.id = id;

        this.labelKey = labelKey;

        this.yawDeg = yawDeg;

        this.pitchDeg = pitchDeg;

    }

    public static WorldMapObliqueDirection fromId(String id) {

        if (id == null || id.isEmpty()) {

            return SE;

        }

        String trimmed = id.trim()

            .toLowerCase();

        for (WorldMapObliqueDirection dir : values()) {

            if (dir.id.equals(trimmed)) {

                return dir;

            }

        }

        return null;

    }

    /** Remap local chunk coords so the SE isometric painter can draw other oblique directions. */

    public void mapLocal(int lx, int lz, int[] out) {

        switch (this) {

            case SE:

                out[0] = lx;

                out[1] = lz;

                break;

            case SW:

                out[0] = lz;

                out[1] = 15 - lx;

                break;

            case NE:

                out[0] = 15 - lz;

                out[1] = lx;

                break;

            case NW:

                out[0] = 15 - lx;

                out[1] = 15 - lz;

                break;

            default:

                out[0] = lx;

                out[1] = lz;

                break;

        }

    }

}
