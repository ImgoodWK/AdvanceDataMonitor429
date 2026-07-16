package com.imgood.textech.webae.icon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NESQL-exporter style icon capture: single-icon FBO + {@code GuiContainerManager.drawItem}.
 *
 * <p>
 * Matches {@code com.github.dcysteine.nesql.exporter.render.Renderer} (ortho 1/16 scale, GUI
 * lighting, glReadPixels + vertical flip). Output size matches FBO ({@link #FBO_SIZE}, same as
 * NESQL default {@code icon_dimension}=64) — no bilinear downscale.
 * </p>
 *
 * <p>
 * Per-icon {@code glPushAttrib}/{@code glPopAttrib} and COLOR|DEPTH|STENCIL clear isolate
 * custom {@code IItemRenderer} state (AE terminals etc.) so batch export does not punch holes
 * into the next PNG.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class IconNesqlStyleRenderer {

    /** NESQL default {@code icon_dimension}. */
    public static final int FBO_SIZE = 64;

    private static Method drawItemMethod;
    private static boolean drawItemResolved;
    private static boolean drawItemAvailable;

    private Framebuffer fbo;
    private boolean prevScissorEnabled;
    private boolean attribPushed;

    public void reset() {
        if (fbo != null) {
            fbo.deleteFramebuffer();
            fbo = null;
        }
    }

    /**
     * Render one item stack to a PNG using NESQL's drawItem + FBO path (native FBO size).
     *
     * @return PNG bytes, or null on failure / blank
     */
    public byte[] renderItem(Minecraft mc, ItemStack stack) {
        if (mc == null || stack == null) return null;
        if (!ensureDrawItem()) {
            AdvanceDataMonitor.LOG.debug("[WebAE] NESQL-style render skipped: GuiContainerManager.drawItem unavailable");
            return null;
        }
        try {
            ensureFbo();
            beginFboRender();
            setupRenderState();
            try {
                drawItemMethod.invoke(null, Integer.valueOf(0), Integer.valueOf(0), stack);
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[WebAE] NESQL drawItem failed: {}", t.getMessage());
            }
            byte[] png = readPixelsToPng();
            finishFboRender(mc);
            return png;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.debug("[WebAE] NESQL-style FBO render failed: {}", t.getMessage());
            try {
                finishFboRender(mc);
            } catch (Throwable ignored) {}
            return null;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    private static boolean ensureDrawItem() {
        if (drawItemResolved) return drawItemAvailable;
        drawItemResolved = true;
        try {
            Class<?> cls = Class.forName("codechicken.nei.guihook.GuiContainerManager");
            drawItemMethod = cls.getMethod("drawItem", int.class, int.class, ItemStack.class);
            drawItemAvailable = true;
        } catch (Throwable t) {
            drawItemAvailable = false;
            AdvanceDataMonitor.LOG.warn("[WebAE] GuiContainerManager.drawItem not found (NEI required for NESQL icons)");
        }
        return drawItemAvailable;
    }

    private void ensureFbo() {
        if (fbo == null) {
            fbo = new Framebuffer(FBO_SIZE, FBO_SIZE, true);
        }
    }

    private void beginFboRender() {
        prevScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        attribPushed = false;
        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attribPushed = true;
        } catch (Throwable ignored) {}

        fbo.bindFramebuffer(true);
        IconRenderGuard.clearFboBuffers();
        IconRenderGuard.resetGlState();

        GL11.glViewport(0, 0, FBO_SIZE, FBO_SIZE);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, 0, FBO_SIZE, FBO_SIZE);
    }

    /** Same matrix / lighting setup as NESQL {@code Renderer.setupRenderState}, plus blend. */
    private void setupRenderState() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 1.0, 0.0, -100.0, 100.0);
        double scaleFactor = 1.0 / 16.0;
        GL11.glScaled(scaleFactor, scaleFactor, scaleFactor);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    private void finishFboRender(Minecraft mc) {
        try {
            RenderHelper.disableStandardItemLighting();
        } catch (Throwable ignored) {}
        try {
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        } catch (Throwable ignored) {}
        try {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        } catch (Throwable ignored) {}
        if (!prevScissorEnabled) {
            try {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            } catch (Throwable ignored) {}
        }
        if (attribPushed) {
            try {
                GL11.glPopAttrib();
            } catch (Throwable ignored) {}
            attribPushed = false;
        }
        if (mc != null && mc.getFramebuffer() != null) {
            try {
                mc.getFramebuffer()
                    .bindFramebuffer(true);
            } catch (Throwable ignored) {}
        }
    }

    private byte[] readPixelsToPng() {
        ByteBuffer imageByteBuffer = BufferUtils.createByteBuffer(4 * FBO_SIZE * FBO_SIZE);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, FBO_SIZE, FBO_SIZE, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, imageByteBuffer);

        int[] pixels = new int[FBO_SIZE * FBO_SIZE];
        IntBuffer intBuf = imageByteBuffer.asIntBuffer();
        intBuf.get(pixels);

        // OpenGL y is inverted vs draw methods (NESQL flips ICON targets).
        int[] flipped = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int x = i % FBO_SIZE;
            int y = FBO_SIZE - (i / FBO_SIZE + 1);
            flipped[i] = pixels[x + FBO_SIZE * y];
        }

        BufferedImage high = new BufferedImage(FBO_SIZE, FBO_SIZE, BufferedImage.TYPE_INT_ARGB);
        high.setRGB(0, 0, FBO_SIZE, FBO_SIZE, flipped, 0, FBO_SIZE);

        // Match NESQL: keep FBO native resolution (no bilinear downscale blur).
        // Frontend scales via CSS; 64px PNGs stay sharp at common display sizes.
        BufferedImage img = high;
        if (IconRenderer.ICON_SIZE != FBO_SIZE && IconRenderer.ICON_SIZE > 0) {
            img = new BufferedImage(
                IconRenderer.ICON_SIZE,
                IconRenderer.ICON_SIZE,
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            // Pixel-art icons: nearest neighbor avoids blur from 64→32 bilinear.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(high, 0, 0, IconRenderer.ICON_SIZE, IconRenderer.ICON_SIZE, null);
            g.dispose();
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to encode NESQL-style PNG", e);
            return null;
        }
    }
}
