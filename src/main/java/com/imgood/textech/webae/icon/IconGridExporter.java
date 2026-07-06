package com.imgood.textech.webae.icon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI-style grid batch renderer: draws many inventory slots into one FBO readback,
 * then crops each cell to a 32×32 PNG. Matches {@code GuiItemIconDumper} spacing.
 */
@SideOnly(Side.CLIENT)
public final class IconGridExporter {

    public static final int COLS = 16;
    public static final int ROWS = 8;
    public static final int SLOTS_PER_PAGE = COLS * ROWS;
    public static final int SLOT_SIZE = 18;
    public static final int ICON_INSET = 1;
    public static final int ICON_PX = 16;

    private static final int FBO_WIDTH = COLS * SLOT_SIZE;
    private static final int FBO_HEIGHT = ROWS * SLOT_SIZE;

    private final IconExportResolver resolver;
    private Framebuffer fbo;
    private boolean prevScissorEnabled;

    public IconGridExporter(IconExportResolver resolver) {
        this.resolver = resolver;
    }

    public void reset() {
        if (fbo != null) {
            fbo.deleteFramebuffer();
            fbo = null;
        }
    }

    /**
     * Render up to {@link #SLOTS_PER_PAGE} items; map key is canonical itemId.
     */
    public Map<String, byte[]> renderPage(Minecraft mc, List<IconItemEnumerator.StackTask> tasks) {
        Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        if (tasks == null || tasks.isEmpty() || resolver == null) return out;
        ensureFbo();
        beginGridRender();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            for (int i = 0; i < tasks.size() && i < SLOTS_PER_PAGE; i++) {
                IconItemEnumerator.StackTask task = tasks.get(i);
                if (task == null || task.stack == null) continue;
                int col = i % COLS;
                int row = i / COLS;
                int x = col * SLOT_SIZE + ICON_INSET;
                int y = row * SLOT_SIZE + ICON_INSET;
                drawStackInSlot(mc, task.stack, x, y);
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Grid page render failed: {}", t.getMessage());
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);

        BufferedImage page = readGridImage();
        finishGridRender(mc);

        for (int i = 0; i < tasks.size() && i < SLOTS_PER_PAGE; i++) {
            IconItemEnumerator.StackTask task = tasks.get(i);
            if (task == null || task.stack == null) continue;
            byte[] gridCrop = cropSlot(page, i);
            IconExportResolver.ResolveResult result = resolver.resolve(mc, task.stack, task.itemId, gridCrop);
            if (result.png != null && result.png.length > 0) {
                out.put(task.itemId, result.png);
            }
        }
        return out;
    }

    /** High-res single-item export for lazy-load / verify. */
    public byte[] renderSingle(Minecraft mc, ItemStack stack, String itemId) {
        if (stack == null || resolver == null) return null;
        return resolver.resolve(mc, stack, itemId, null).png;
    }

    private void drawStackInSlot(Minecraft mc, ItemStack stack, int x, int y) {
        if (IconGlFallback.needsGlFallback(stack)) {
            return;
        }
        RenderItem.getInstance()
            .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
    }

    private byte[] cropSlot(BufferedImage page, int index) {
        if (page == null) return null;
        int col = index % COLS;
        int row = index / COLS;
        int x = col * SLOT_SIZE + ICON_INSET;
        int y = row * SLOT_SIZE + ICON_INSET;
        if (x + ICON_PX > page.getWidth() || y + ICON_PX > page.getHeight()) return null;
        BufferedImage icon = page.getSubimage(x, y, ICON_PX, ICON_PX);
        BufferedImage scaled = new BufferedImage(
            IconRenderer.ICON_SIZE,
            IconRenderer.ICON_SIZE,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(icon, 0, 0, IconRenderer.ICON_SIZE, IconRenderer.ICON_SIZE, null);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            ImageIO.write(scaled, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private void ensureFbo() {
        if (fbo == null) {
            fbo = new Framebuffer(FBO_WIDTH, FBO_HEIGHT, true);
        }
    }

    private void beginGridRender() {
        try {
            prevScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        } catch (Throwable ignored) {
            prevScissorEnabled = false;
        }
        fbo.bindFramebuffer(true);
        GL11.glViewport(0, 0, FBO_WIDTH, FBO_HEIGHT);
        GL11.glScissor(0, 0, FBO_WIDTH, FBO_HEIGHT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, FBO_WIDTH, FBO_HEIGHT, 0.0D, -1000.0D, 1000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private BufferedImage readGridImage() {
        ByteBuffer buf = BufferUtils.createByteBuffer(FBO_WIDTH * FBO_HEIGHT * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, FBO_WIDTH, FBO_HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

        BufferedImage img = new BufferedImage(FBO_WIDTH, FBO_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[FBO_WIDTH];
        for (int y = 0; y < FBO_HEIGHT; y++) {
            int srcY = FBO_HEIGHT - 1 - y;
            buf.position(srcY * FBO_WIDTH * 4);
            for (int x = 0; x < FBO_WIDTH; x++) {
                int r = buf.get() & 0xFF;
                int g = buf.get() & 0xFF;
                int b = buf.get() & 0xFF;
                int a = buf.get() & 0xFF;
                row[x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            img.setRGB(0, y, FBO_WIDTH, 1, row, 0, FBO_WIDTH);
        }
        return img;
    }

    private void finishGridRender(Minecraft mc) {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        if (!prevScissorEnabled) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        mc.getFramebuffer()
            .bindFramebuffer(true);
    }
}
