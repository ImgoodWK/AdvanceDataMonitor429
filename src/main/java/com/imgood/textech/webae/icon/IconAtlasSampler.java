package com.imgood.textech.webae.icon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Samples item/fluid icons directly from the bound block/item texture atlases.
 * Avoids per-icon FBO rendering for the common flat-icon case.
 */
@SideOnly(Side.CLIENT)
public final class IconAtlasSampler {

    private static final int OUTPUT_SIZE = IconRenderer.ICON_SIZE;
    private static final int ICON_INSET_PX = 1;

    private BufferedImage itemsAtlas;
    private BufferedImage blocksAtlas;

    public void reset() {
        itemsAtlas = null;
        blocksAtlas = null;
    }

    public void ensureAtlases(Minecraft mc) {
        if (itemsAtlas == null) {
            itemsAtlas = downloadAtlas(mc, TextureMap.locationItemsTexture);
        }
        if (blocksAtlas == null) {
            blocksAtlas = downloadAtlas(mc, TextureMap.locationBlocksTexture);
        }
    }

    public byte[] sampleItemStack(Minecraft mc, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        ensureAtlases(mc);
        Item item = stack.getItem();
        BufferedImage atlas = atlasForItem(item);
        if (atlas == null) return null;

        int passes;
        try {
            passes = item.getRenderPasses(stack.getItemDamage());
        } catch (Throwable ignored) {
            passes = 1;
        }
        if (passes <= 0) passes = 1;

        BufferedImage composite = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = composite.createGraphics();
        boolean drew = false;
        for (int pass = 0; pass < passes; pass++) {
            IIcon icon = resolveIcon(item, stack, pass);
            if (icon == null) continue;
            BufferedImage layer = cropIcon(atlas, icon, ICON_INSET_PX);
            if (layer == null) continue;
            int tint = 0xFFFFFF;
            try {
                tint = item.getColorFromItemStack(stack, pass);
            } catch (Throwable ignored) {}
            if (tint != 0xFFFFFF) {
                layer = applyTint(layer, tint);
            }
            g.drawImage(layer, 0, 0, 16, 16, null);
            drew = true;
        }
        g.dispose();
        if (!drew) return null;
        return toPng(scaleToOutput(composite));
    }

    public byte[] sampleFluid(Minecraft mc, Fluid fluid) {
        if (fluid == null) return null;
        ensureAtlases(mc);
        IIcon icon = fluidStillIcon(fluid);
        if (icon == null || blocksAtlas == null) return null;

        BufferedImage cropped = cropIcon(blocksAtlas, icon, ICON_INSET_PX);
        if (cropped == null) return null;

        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        int tint = fluid.getColor();
        int a = (tint >> 24) & 0xFF;
        int r = (tint >> 16) & 0xFF;
        int gch = (tint >> 8) & 0xFF;
        int b = tint & 0xFF;
        if (a <= 0) a = 255;
        g.setColor(new java.awt.Color(r, gch, b, Math.min(a, 220)));
        g.fillRect(0, 0, 16, 16);
        g.drawImage(cropped, 0, 0, 16, 16, null);
        g.dispose();
        return toPng(scaleToOutput(out));
    }

    public static boolean isImageBlank(BufferedImage img) {
        if (img == null) return true;
        int w = img.getWidth();
        int h = img.getHeight();
        int visible = 0;
        int total = w * h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int gc = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (a > 16 && (r > 16 || gc > 16 || b > 16)) {
                    visible++;
                }
            }
        }
        return visible < Math.max(4, total / 50);
    }

    public static boolean isPngBlank(byte[] png) {
        if (png == null || png.length == 0) return true;
        try {
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(png));
            return isImageBlank(img);
        } catch (Exception e) {
            return true;
        }
    }

    private BufferedImage atlasForItem(Item item) {
        try {
            return item.getSpriteNumber() == 0 ? blocksAtlas : itemsAtlas;
        } catch (Throwable ignored) {
            return itemsAtlas;
        }
    }

    private static IIcon resolveIcon(Item item, ItemStack stack, int pass) {
        try {
            IIcon icon = item.getIcon(stack, pass);
            if (icon != null) return icon;
        } catch (Throwable ignored) {}
        try {
            return item.getIconIndex(stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IIcon fluidStillIcon(Fluid fluid) {
        try {
            IIcon icon = fluid.getStillIcon();
            if (icon != null) return icon;
        } catch (Throwable ignored) {}
        try {
            return fluid.getIcon();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BufferedImage downloadAtlas(Minecraft mc, ResourceLocation location) {
        try {
            mc.getTextureManager()
                .bindTexture(location);
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0) return null;

            ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] row = new int[w];
            for (int y = 0; y < h; y++) {
                int srcY = h - 1 - y;
                buf.position(srcY * w * 4);
                for (int x = 0; x < w; x++) {
                    int r = buf.get() & 0xFF;
                    int g = buf.get() & 0xFF;
                    int b = buf.get() & 0xFF;
                    int a = buf.get() & 0xFF;
                    row[x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                img.setRGB(0, y, w, 1, row, 0, w);
            }
            AdvanceDataMonitor.LOG.info("[WebAE] Atlas snapshot {}: {}x{}", location, w, h);
            return img;
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to snapshot atlas {}", location, t);
            return null;
        }
    }

    private static BufferedImage cropIcon(BufferedImage atlas, IIcon icon, int insetPx) {
        int atlasW = atlas.getWidth();
        int atlasH = atlas.getHeight();
        int x0 = (int) Math.floor(icon.getMinU() * atlasW) + insetPx;
        int x1 = (int) Math.ceil(icon.getMaxU() * atlasW) - insetPx;
        // downloadAtlas() already converts OpenGL bottom-left origin to image top-left;
        // use direct V here — (1-v) would mirror icons vertically on export.
        int y0 = (int) Math.floor(icon.getMinV() * atlasH) + insetPx;
        int y1 = (int) Math.ceil(icon.getMaxV() * atlasH) - insetPx;
        if (x1 <= x0 || y1 <= y0) return null;
        if (x0 < 0 || y0 < 0 || x1 > atlasW || y1 > atlasH) return null;
        return atlas.getSubimage(x0, y0, x1 - x0, y1 - y0);
    }

    private static BufferedImage applyTint(BufferedImage src, int rgb) {
        int tr = (rgb >> 16) & 0xFF;
        int tg = (rgb >> 8) & 0xFF;
        int tb = rgb & 0xFF;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                int r = ((argb >> 16) & 0xFF) * tr / 255;
                int g = ((argb >> 8) & 0xFF) * tg / 255;
                int b = (argb & 0xFF) * tb / 255;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static BufferedImage scaleToOutput(BufferedImage src) {
        BufferedImage out = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, null);
        g.dispose();
        return out;
    }

    private static byte[] toPng(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to encode atlas icon PNG", e);
            return null;
        }
    }
}
