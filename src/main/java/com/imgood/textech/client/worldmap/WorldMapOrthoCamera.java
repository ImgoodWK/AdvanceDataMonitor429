package com.imgood.textech.client.worldmap;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import com.imgood.textech.webae.worldmap.WorldMapObliqueDirection;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Orthographic / look-at camera presets for HD world map chunk rendering.
 * Oblique views follow mineshot defaults ({@code xRot=30}, compass {@code yRot}).
 */
@SideOnly(Side.CLIENT)
public final class WorldMapOrthoCamera {

    public final float eyeX;
    public final float eyeY;
    public final float eyeZ;
    public final float lookX;
    public final float lookY;
    public final float lookZ;
    public final float upX;
    public final float upY;
    public final float upZ;
    public final float orthoLeft;
    public final float orthoRight;
    public final float orthoBottom;
    public final float orthoTop;
    public final float orthoNear;
    public final float orthoFar;

    private WorldMapOrthoCamera(float eyeX, float eyeY, float eyeZ, float lookX, float lookY, float lookZ, float upX,
        float upY, float upZ, float orthoLeft, float orthoRight, float orthoBottom, float orthoTop, float orthoNear,
        float orthoFar) {
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.lookX = lookX;
        this.lookY = lookY;
        this.lookZ = lookZ;
        this.upX = upX;
        this.upY = upY;
        this.upZ = upZ;
        this.orthoLeft = orthoLeft;
        this.orthoRight = orthoRight;
        this.orthoBottom = orthoBottom;
        this.orthoTop = orthoTop;
        this.orthoNear = orthoNear;
        this.orthoFar = orthoFar;
    }

    public static WorldMapOrthoCamera forView(WorldMapView view, int chunkX, int chunkZ) {
        float cx = chunkX * 16.0F + 8.0F;
        float cz = chunkZ * 16.0F + 8.0F;
        float cy = 72.0F;
        float span = 14.0F;

        if (view == WorldMapView.FLAT) {
            return new WorldMapOrthoCamera(
                cx,
                220.0F,
                cz,
                cx,
                cy,
                cz,
                0.0F,
                0.0F,
                -1.0F,
                -span,
                span,
                -span,
                span,
                -256.0F,
                512.0F);
        }
        if (view != null && view.isOblique() && view.obliqueDirection != null) {
            return obliqueCamera(cx, cy, cz, span, view.obliqueDirection);
        }
        return forView(WorldMapView.FLAT, chunkX, chunkZ);
    }

    private static WorldMapOrthoCamera obliqueCamera(float cx, float cy, float cz, float span,
        WorldMapObliqueDirection direction) {
        float pitchRad = (float) Math.toRadians(direction.pitchDeg);
        float yawRad = (float) Math.toRadians(direction.yawDeg + 180.0F);
        float horiz = (float) Math.cos(pitchRad);
        float dist = 72.0F;
        float eyeX = cx + dist * horiz * (float) Math.sin(yawRad);
        float eyeY = cy + dist * (float) Math.sin(pitchRad);
        float eyeZ = cz + dist * horiz * (float) Math.cos(yawRad);
        return new WorldMapOrthoCamera(
            eyeX,
            eyeY,
            eyeZ,
            cx,
            cy,
            cz,
            0.0F,
            1.0F,
            0.0F,
            -span * 1.35F,
            span * 1.35F,
            -span,
            span * 1.25F,
            -256.0F,
            512.0F);
    }

    public void apply() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(orthoLeft, orthoRight, orthoBottom, orthoTop, orthoNear, orthoFar);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GLU.gluLookAt(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);
    }
}
