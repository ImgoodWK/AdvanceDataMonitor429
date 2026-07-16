package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Resets client render state after off-screen icon export. GTNH custom item renderers
 * occasionally leave {@link Tessellator} mid-draw; the next frame then crashes with
 * {@code Already tesselating!}. AE terminal-style renderers may also leave stencil /
 * depth / blend state that punches holes in the next icon's FBO capture.
 */
@SideOnly(Side.CLIENT)
public final class IconRenderGuard {

    private IconRenderGuard() {}

    public static void afterRender(Minecraft mc) {
        finishTessellatorIfNeeded();
        try {
            RenderHelper.disableStandardItemLighting();
        } catch (Throwable ignored) {}
        resetGlState();
        try {
            if (mc != null && mc.getFramebuffer() != null) {
                mc.getFramebuffer()
                    .bindFramebuffer(true);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Clear color/depth/stencil on the currently bound framebuffer. Call after
     * {@code bindFramebuffer} before drawing an icon so prior stencil masks cannot
     * clip the next item (square / AE-terminal shaped holes).
     */
    public static void clearFboBuffers() {
        try {
            GL11.glClearColor(0f, 0f, 0f, 0f);
        } catch (Throwable ignored) {}
        try {
            GL11.glClearDepth(1D);
        } catch (Throwable ignored) {}
        try {
            GL11.glClearStencil(0);
        } catch (Throwable ignored) {}
        try {
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        } catch (Throwable t) {
            // Some drivers reject STENCIL clear if the FBO has no stencil; fall back.
            try {
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            } catch (Throwable ignored) {}
        }
    }

    /** Best-effort restore of GL bits commonly polluted by custom IItemRenderer. */
    public static void resetGlState() {
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } catch (Throwable ignored) {}
        try {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        } catch (Throwable ignored) {}
        try {
            GL11.glStencilMask(~0);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, ~0);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        } catch (Throwable ignored) {}
        try {
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
        } catch (Throwable ignored) {}
        try {
            GL11.glColorMask(true, true, true, true);
        } catch (Throwable ignored) {}
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } catch (Throwable ignored) {}
        try {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        } catch (Throwable ignored) {}
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable ignored) {}
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        } catch (Throwable ignored) {}
    }

    private static void finishTessellatorIfNeeded() {
        try {
            Tessellator tess = Tessellator.instance;
            java.lang.reflect.Field field = Tessellator.class.getDeclaredField("isDrawing");
            field.setAccessible(true);
            if (field.getBoolean(tess)) {
                tess.draw();
            }
        } catch (Throwable ignored) {}
    }
}
