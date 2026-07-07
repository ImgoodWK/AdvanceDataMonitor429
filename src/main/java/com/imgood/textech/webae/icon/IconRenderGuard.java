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
 * {@code Already tesselating!}.
 */
@SideOnly(Side.CLIENT)
public final class IconRenderGuard {

    private IconRenderGuard() {}

    public static void afterRender(Minecraft mc) {
        finishTessellatorIfNeeded();
        try {
            RenderHelper.disableStandardItemLighting();
        } catch (Throwable ignored) {}
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } catch (Throwable ignored) {}
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable ignored) {}
        try {
            if (mc != null && mc.getFramebuffer() != null) {
                mc.getFramebuffer()
                    .bindFramebuffer(true);
            }
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
