package com.imgood.textech.renders;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.GL11;

/**
 * Shared HUD rendering utilities. Extracted from PlannerHudRenderer
 * so other HUD elements (AE2 power, storage, etc.) can reuse the
 * same positioning, scaling, and text-wrapping logic.
 */
public class HudRenderUtil {

    /** Default HUD text color â€?cyan. */
    public static final int COLOR_TITLE = 0x00FFFF;
    public static final int COLOR_VALUE = 0xFFFFFF;
    public static final int COLOR_LABEL = 0x888888;
    public static final int COLOR_WARN = 0xFF5555;
    public static final int COLOR_GOOD = 0x55FF55;

    public static final int DEFAULT_LINE_HEIGHT = 12;
    public static final int DEFAULT_PADDING = 4;

    /**
     * Compute scaled screen position from normalized [0,1] coordinates.
     */
    public static int scaledX(ScaledResolution sr, float normX, int elementWidth) {
        return (int) (normX * (sr.getScaledWidth() - elementWidth));
    }

    public static int scaledY(ScaledResolution sr, float normY, int elementHeight) {
        return (int) (normY * (sr.getScaledHeight() - elementHeight));
    }

    /**
     * Wrap text to fit within maxWidth, returning the list of lines
     * and the total height consumed.
     */
    public static int drawWrappedLines(FontRenderer fr, List<String> lines, int x, int lineY, int lineHeight,
        int color) {
        for (String line : lines) {
            fr.drawStringWithShadow(line, x, lineY, color);
            lineY += lineHeight;
        }
        return lineY;
    }

    /**
     * Draw a translucent background rectangle using GL11.
     */
    public static void drawBackground(int x, int y, int width, int height, int colorAlpha) {
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.instance;
        net.minecraft.client.renderer.OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(colorAlpha & 0xFFFFFF, (colorAlpha >> 24) & 0xFF);
        tessellator.addVertex(x, y + height, 0);
        tessellator.addVertex(x + width, y + height, 0);
        tessellator.addVertex(x + width, y, 0);
        tessellator.addVertex(x, y, 0);
        tessellator.draw();
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
    }

    /**
     * Get formatted lines from a string that may contain multi-line text
     * (split by {@code \n} or auto-wrapped).
     */
    public static List<String> formatLines(FontRenderer fr, String text, int maxWidth) {
        return fr.listFormattedStringToWidth(text, maxWidth);
    }

    /**
     * Get the total height consumed by a list of wrapped lines.
     */
    public static int totalLineHeight(List<String> lines, int lineHeight) {
        return lineHeight * Math.max(1, lines.size());
    }

    public static int packArgb(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Draw screen-space text with ARGB alpha. FontRenderer only enables GL_ALPHA_TEST by default;
     * this sets up orthographic projection and GL_BLEND so partial transparency works in 1.7.10.
     */
    public static void drawScreenTextWithAlpha(FontRenderer fr, String text, int x, int y, int argbColor) {
        if (fr == null || text == null || text.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, width, height, 0.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            fr.drawString(text, x, y, argbColor, true);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
