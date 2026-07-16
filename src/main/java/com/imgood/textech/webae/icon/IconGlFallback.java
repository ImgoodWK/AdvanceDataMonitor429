package com.imgood.textech.webae.icon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fluids.Fluid;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.GLU;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Minimal OpenGL fallback for items that use custom {@link IItemRenderer} inventory paths.
 */
@SideOnly(Side.CLIENT)
final class IconGlFallback {

    private static final int FBO_SIZE = 64;
    private static final float RENDER_SCALE = 3.0F;
    private static final int RENDERED_SIZE = 16 * (int) RENDER_SCALE;
    private static final int RENDER_OFFSET = (FBO_SIZE - RENDERED_SIZE) / 2;

    private Framebuffer fbo;
    private boolean prevScissorEnabled;

    void reset() {
        cleanupFbo();
    }

    byte[] renderInventoryIcon(Minecraft mc, ItemStack stack) {
        return renderInventoryIcon(mc, stack, true);
    }

    /** Inventory FBO pass without depth, lighting, or rescale normal (flat 2D look). */
    byte[] renderFlatInventoryIcon(Minecraft mc, ItemStack stack) {
        return renderInventoryIcon(mc, stack, false);
    }

    /** Entity drop / world item perspective ({@link IItemRenderer.ItemRenderType#ENTITY}). */
    byte[] renderEntityIcon(Minecraft mc, ItemStack stack) {
        return renderTypedItemIcon(mc, stack, IItemRenderer.ItemRenderType.ENTITY);
    }

    /** First-person held item ({@link IItemRenderer.ItemRenderType#EQUIPPED_FIRST_PERSON}). */
    byte[] renderFirstPersonIcon(Minecraft mc, ItemStack stack) {
        return renderTypedItemIcon(mc, stack, IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON);
    }

    /**
     * NEI slot layout but always uses vanilla {@link RenderItem} — skips custom
     * {@link IItemRenderer} so halo/cosmic overlays that fail in isolated FBO still yield a flat icon.
     */
    byte[] renderVanillaNeiSlotIcon(Minecraft mc, ItemStack stack) {
        try {
            ensureFbo();
            beginFboRender();
            try {
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glPushMatrix();
                GL11.glTranslatef(RENDER_OFFSET + RENDER_SCALE, RENDER_OFFSET + RENDER_SCALE, 0.0F);
                GL11.glScalef(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
                RenderItem.getInstance()
                    .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
                GL11.glPopMatrix();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Vanilla NEI slot render failed: {}", t.getMessage());
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);

            byte[] png = readPixelsToPng();
            finishFboRender(mc);
            return png;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    /** Registry fluid icon ({@code fluid:water}) — tinted still texture like AE2 terminal slots. */
    byte[] renderRegistryFluidIcon(Minecraft mc, Fluid fluid) {
        try {
            ensureFbo();
            beginFboRender();
            try {
                IconFluidRenderer.drawTintedFluidIcon(mc, fluid, RENDER_OFFSET, RENDER_OFFSET, RENDERED_SIZE);
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Fluid GL render failed: {}", t.getMessage());
            }
            byte[] png = readPixelsToPng();
            finishFboRender(mc);
            return png;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    /**
     * Fluid cells / fluid drops: NEI slot item pass plus AE2 post-render hooks for in-game overlays.
     */
    byte[] renderFluidAwareSlotIcon(Minecraft mc, ItemStack stack) {
        try {
            ensureFbo();
            beginFboRender();
            try {
                if (IconFluidRenderer.isFluidDropItem(stack.getItem()) || IconFluidRenderer.isFluidDropStack(stack)) {
                    IconFluidRenderer.drawFluidDropStack(mc, stack, RENDER_OFFSET, RENDER_OFFSET, RENDERED_SIZE);
                } else {
                    GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    RenderHelper.enableGUIStandardItemLighting();
                    GL11.glPushMatrix();
                    GL11.glTranslatef(RENDER_OFFSET + RENDER_SCALE, RENDER_OFFSET + RENDER_SCALE, 0.0F);
                    GL11.glScalef(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);

                    IItemRenderer custom = MinecraftForgeClient
                        .getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
                    if (custom != null && custom.handleRenderType(stack, IItemRenderer.ItemRenderType.INVENTORY)) {
                        custom.renderItem(IItemRenderer.ItemRenderType.INVENTORY, stack);
                    } else {
                        RenderItem.getInstance()
                            .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
                    }
                    IconFluidRenderer.invokeAePostRenderHooks(mc, stack, 0, 0);
                    GL11.glPopMatrix();
                    RenderHelper.disableStandardItemLighting();
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                }
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Fluid-aware slot render failed: {}", t.getMessage());
            }

            byte[] png = readPixelsToPng();
            finishFboRender(mc);
            return png;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    /** NEI slot spacing: 18×18 slot with item at (+1,+1), standard GUI lighting. */
    byte[] renderNeiSlotIcon(Minecraft mc, ItemStack stack) {
        try {
            ensureFbo();
            beginFboRender();
            try {
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glPushMatrix();
                GL11.glTranslatef(RENDER_OFFSET + RENDER_SCALE, RENDER_OFFSET + RENDER_SCALE, 0.0F);
                GL11.glScalef(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);

                IItemRenderer custom = MinecraftForgeClient
                    .getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
                if (custom != null && custom.handleRenderType(stack, IItemRenderer.ItemRenderType.INVENTORY)) {
                    custom.renderItem(IItemRenderer.ItemRenderType.INVENTORY, stack);
                } else {
                    RenderItem.getInstance()
                        .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
                }
                GL11.glPopMatrix();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[WebAE] NEI slot render failed: {}", t.getMessage());
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);

            byte[] png = readPixelsToPng();
            finishFboRender(mc);
            return png;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    /** Mini block scene for {@link net.minecraft.item.ItemBlock} via {@link RenderBlocks#renderBlockAsItem}. */
    byte[] renderBlockAsItem(Minecraft mc, Block block, int metadata) {
        try {
            ensureFbo();
            beginFboRender3D();
            try {
                RenderHelper.enableStandardItemLighting();
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);

                GL11.glPushMatrix();
                GL11.glTranslatef(0.0F, -0.05F, 0.0F);
                GL11.glScalef(1.15F, 1.15F, 1.15F);
                GL11.glRotatef(25.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);

                RenderBlocks renderBlocks = new RenderBlocks();
                renderBlocks.renderBlockAsItem(block, metadata, 1.0F);
                GL11.glPopMatrix();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Block FBO render failed: {}", t.getMessage());
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);

            byte[] png = readPixelsToPng();
            finishFboRender(mc);
            return png;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    private byte[] renderTypedItemIcon(Minecraft mc, ItemStack stack, IItemRenderer.ItemRenderType type) {
        try {
            ensureFbo();
            beginFboRender3D();
            try {
                RenderHelper.enableStandardItemLighting();
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);

                GL11.glPushMatrix();
                if (type == IItemRenderer.ItemRenderType.ENTITY) {
                    GL11.glRotatef(20.0F, 1.0F, 0.0F, 0.0F);
                    GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
                } else {
                    GL11.glRotatef(-12.0F, 1.0F, 0.0F, 0.0F);
                    GL11.glRotatef(48.0F, 0.0F, 1.0F, 0.0F);
                    GL11.glTranslatef(0.0F, 0.08F, 0.0F);
                }

                IItemRenderer custom = MinecraftForgeClient.getItemRenderer(stack, type);
                RenderBlocks renderBlocks = new RenderBlocks();
                if (custom != null && custom.handleRenderType(stack, type)) {
                    custom.renderItem(type, stack, renderBlocks, mc.thePlayer);
                } else if (type == IItemRenderer.ItemRenderType.ENTITY) {
                    GL11.glRotatef(335.0F, 0.0F, 1.0F, 0.0F);
                    GL11.glRotatef(50.0F, 1.0F, 0.0F, 0.0F);
                    GL11.glScalef(0.42F, 0.42F, 0.42F);
                    RenderItem.getInstance()
                        .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, -8, -8);
                } else {
                    GL11.glScalef(0.38F, 0.38F, 0.38F);
                    RenderItem.getInstance()
                        .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, -8, -8);
                }
                GL11.glPopMatrix();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Typed item render failed ({}): {}", type, t.getMessage());
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);

            byte[] png = readPixelsToPng();
            finishFboRender(mc);
            return png;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    private byte[] renderInventoryIcon(Minecraft mc, ItemStack stack, boolean depthAndLighting) {
        try {
            ensureFbo();
            beginFboRender();
            try {
                if (depthAndLighting) {
                    GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    RenderHelper.enableGUIStandardItemLighting();
                } else {
                    GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    RenderHelper.disableStandardItemLighting();
                }
                GL11.glPushMatrix();
                GL11.glTranslatef(RENDER_OFFSET, RENDER_OFFSET, 0.0F);
                GL11.glScalef(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);

                IItemRenderer custom = MinecraftForgeClient
                    .getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
                if (custom != null && custom.handleRenderType(stack, IItemRenderer.ItemRenderType.INVENTORY)) {
                    custom.renderItem(IItemRenderer.ItemRenderType.INVENTORY, stack);
                } else {
                    RenderItem.getInstance()
                        .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
                }
                GL11.glPopMatrix();
            } catch (Throwable t) {
                AdvanceDataMonitor.LOG.debug("[WebAE] GL fallback render failed: {}", t.getMessage());
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);

            byte[] png = readPixelsToPng();
            finishFboRender(mc);
            return png;
        } finally {
            IconRenderGuard.afterRender(mc);
        }
    }

    static boolean needsGlFallback(ItemStack stack) {
        if (stack == null) return false;
        IItemRenderer custom = MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
        return custom != null && custom.handleRenderType(stack, IItemRenderer.ItemRenderType.INVENTORY);
    }

    private void ensureFbo() {
        if (fbo == null) {
            fbo = new Framebuffer(FBO_SIZE, FBO_SIZE, true);
        }
    }

    private void cleanupFbo() {
        if (fbo != null) {
            fbo.deleteFramebuffer();
            fbo = null;
        }
    }

    private void beginFboRender() {
        try {
            prevScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        } catch (Throwable ignored) {
            prevScissorEnabled = false;
        }
        fbo.bindFramebuffer(true);
        GL11.glViewport(0, 0, FBO_SIZE, FBO_SIZE);
        GL11.glScissor(0, 0, FBO_SIZE, FBO_SIZE);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        IconRenderGuard.clearFboBuffers();
        IconRenderGuard.resetGlState();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, 0, FBO_SIZE, FBO_SIZE);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, FBO_SIZE, FBO_SIZE, 0.0D, -1000.0D, 1000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void beginFboRender3D() {
        try {
            prevScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        } catch (Throwable ignored) {
            prevScissorEnabled = false;
        }
        fbo.bindFramebuffer(true);
        GL11.glViewport(0, 0, FBO_SIZE, FBO_SIZE);
        GL11.glScissor(0, 0, FBO_SIZE, FBO_SIZE);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        IconRenderGuard.clearFboBuffers();
        IconRenderGuard.resetGlState();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, 0, FBO_SIZE, FBO_SIZE);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GLU.gluPerspective(70.0F, 1.0F, 0.05F, 100.0F);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2.8F);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void finishFboRender(Minecraft mc) {
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

    private byte[] readPixelsToPng() {
        ByteBuffer buf = BufferUtils.createByteBuffer(RENDERED_SIZE * RENDERED_SIZE * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        int readY = FBO_SIZE - RENDER_OFFSET - RENDERED_SIZE;
        GL11.glReadPixels(RENDER_OFFSET, readY, RENDERED_SIZE, RENDERED_SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

        BufferedImage high = new BufferedImage(RENDERED_SIZE, RENDERED_SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[RENDERED_SIZE];
        for (int y = 0; y < RENDERED_SIZE; y++) {
            int srcY = RENDERED_SIZE - 1 - y;
            buf.position(srcY * RENDERED_SIZE * 4);
            for (int x = 0; x < RENDERED_SIZE; x++) {
                int r = buf.get() & 0xFF;
                int g = buf.get() & 0xFF;
                int b = buf.get() & 0xFF;
                int a = buf.get() & 0xFF;
                row[x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            high.setRGB(0, y, RENDERED_SIZE, 1, row, 0, RENDERED_SIZE);
        }

        BufferedImage img = new BufferedImage(
            IconRenderer.ICON_SIZE,
            IconRenderer.ICON_SIZE,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(high, 0, 0, IconRenderer.ICON_SIZE, IconRenderer.ICON_SIZE, null);
        g.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to encode GL fallback PNG", e);
            return null;
        }
    }
}
