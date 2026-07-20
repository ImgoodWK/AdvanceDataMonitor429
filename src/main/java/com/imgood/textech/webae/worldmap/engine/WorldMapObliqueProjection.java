package com.imgood.textech.webae.worldmap.engine;

import com.imgood.textech.webae.worldmap.WorldMapObliqueDirection;

/**
 * Orthographic oblique camera matching {@code WorldMapOrthoCamera} / mineshot orbit presets.
 * Maps tile pixels to world-space ray origin + direction for software ray tracing.
 */
public final class WorldMapObliqueProjection {

    public final double eyeX;
    public final double eyeY;
    public final double eyeZ;
    public final double dirX;
    public final double dirY;
    public final double dirZ;
    public final double rightX;
    public final double rightY;
    public final double rightZ;
    public final double upX;
    public final double upY;
    public final double upZ;
    public final double orthoLeft;
    public final double orthoRight;
    public final double orthoBottom;
    public final double orthoTop;

    private WorldMapObliqueProjection(double eyeX, double eyeY, double eyeZ, double dirX, double dirY, double dirZ,
        double rightX, double rightY, double rightZ, double upX, double upY, double upZ, double orthoLeft,
        double orthoRight, double orthoBottom, double orthoTop) {
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.rightX = rightX;
        this.rightY = rightY;
        this.rightZ = rightZ;
        this.upX = upX;
        this.upY = upY;
        this.upZ = upZ;
        this.orthoLeft = orthoLeft;
        this.orthoRight = orthoRight;
        this.orthoBottom = orthoBottom;
        this.orthoTop = orthoTop;
    }

    public static WorldMapObliqueProjection forChunk(int chunkX, int chunkZ, WorldMapObliqueDirection direction) {
        if (direction == null) {
            direction = WorldMapObliqueDirection.SE;
        }
        double cx = chunkX * 16.0 + 8.0;
        double cz = chunkZ * 16.0 + 8.0;
        double cy = 72.0;
        double span = 14.0;

        double pitchRad = Math.toRadians(direction.pitchDeg);
        double yawRad = Math.toRadians(direction.yawDeg + 180.0);
        double horiz = Math.cos(pitchRad);
        double dist = 72.0;
        double eyeX = cx + dist * horiz * Math.sin(yawRad);
        double eyeY = cy + dist * Math.sin(pitchRad);
        double eyeZ = cz + dist * horiz * Math.cos(yawRad);

        double fwdX = cx - eyeX;
        double fwdY = cy - eyeY;
        double fwdZ = cz - eyeZ;
        double[] fwd = normalize(fwdX, fwdY, fwdZ);
        double dirX = fwd[0];
        double dirY = fwd[1];
        double dirZ = fwd[2];

        double[] right = cross(dirX, dirY, dirZ, 0.0, 1.0, 0.0);
        normalizeInPlace(right);
        double rightX = right[0];
        double rightY = right[1];
        double rightZ = right[2];

        double[] up = cross(rightX, rightY, rightZ, dirX, dirY, dirZ);
        normalizeInPlace(up);
        double upX = up[0];
        double upY = up[1];
        double upZ = up[2];

        double orthoLeft = -span * 1.35;
        double orthoRight = span * 1.35;
        double orthoBottom = -span;
        double orthoTop = span * 1.25;

        return new WorldMapObliqueProjection(
            eyeX,
            eyeY,
            eyeZ,
            dirX,
            dirY,
            dirZ,
            rightX,
            rightY,
            rightZ,
            upX,
            upY,
            upZ,
            orthoLeft,
            orthoRight,
            orthoBottom,
            orthoTop);
    }

    /**
     * Fills {@code out} with ray origin and normalized direction for tile pixel {@code px},{@code py}.
     */
    public void pixelToRay(int px, int py, int tilePx, Ray out) {
        if (out == null || tilePx <= 0) {
            return;
        }
        double relX = orthoLeft + (px + 0.5) / tilePx * (orthoRight - orthoLeft);
        double relY = orthoTop - (py + 0.5) / tilePx * (orthoTop - orthoBottom);
        out.originX = eyeX + rightX * relX + upX * relY;
        out.originY = eyeY + rightY * relX + upY * relY;
        out.originZ = eyeZ + rightZ * relX + upZ * relY;
        out.dirX = dirX;
        out.dirY = dirY;
        out.dirZ = dirZ;
    }

    public static final class Ray {

        public double originX;
        public double originY;
        public double originZ;
        public double dirX;
        public double dirY;
        public double dirZ;
    }

    private static void normalizeInPlace(double[] v) {
        if (v == null || v.length < 3) {
            return;
        }
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1.0e-9) {
            v[0] = 0.0;
            v[1] = 0.0;
            v[2] = -1.0;
            return;
        }
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
    }

    private static double[] normalize(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-9) {
            return new double[] { 0.0, 0.0, -1.0 };
        }
        return new double[] { x / len, y / len, z / len };
    }

    private static double[] cross(double ax, double ay, double az, double bx, double by, double bz) {
        return new double[] { ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx };
    }
}
