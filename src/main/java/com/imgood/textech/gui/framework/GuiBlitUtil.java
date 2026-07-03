package com.imgood.textech.gui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shared texture blit and 9-slice / horizontal 3-slice drawing for ADM UI framework.
 */
@SideOnly(Side.CLIENT)
public final class GuiBlitUtil {

    private GuiBlitUtil() {}

    public static void blit(
        ResourceLocation texture,
        int atlasSize,
        int x,
        int y,
        int w,
        int h,
        int u,
        int v,
        int sw,
        int sh) {
        if (w <= 0 || h <= 0 || sw <= 0 || sh <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(texture);
        float tex = (float) atlasSize;
        float u0 = u / tex;
        float v0 = v / tex;
        float u1 = (u + sw) / tex;
        float v1 = (v + sh) / tex;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + h, 0, u0, v1);
        tessellator.addVertexWithUV(x + w, y + h, 0, u1, v1);
        tessellator.addVertexWithUV(x + w, y, 0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0, u0, v0);
        tessellator.draw();
    }

    public static void drawNineSlice(NineSliceRegion region, int x, int y, int width, int height) {
        drawNineSlice(region, x, y, width, height, region != null ? region.borderPx() : 0);
    }

    /**
     * @param borderPx destination border width; source UV uses {@link NineSliceRegion#borderPx()}.
     */
    public static void drawNineSlice(NineSliceRegion region, int x, int y, int width, int height, int borderPx) {
        if (region == null || width <= 0 || height <= 0) {
            return;
        }
        if (borderPx <= 0) {
            return;
        }
        int border = Math.min(borderPx, Math.min(width, height) / 2);
        if (border <= 0) {
            return;
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int atlas = region.atlasSize();
        ResourceLocation tex = region.texture();
        int ru = region.u();
        int rv = region.v();
        int b = border;
        int srcBorder = region.borderPx();
        int srcMidW = region.srcMidW();
        int srcMidH = region.srcMidH();
        int midW = width - b * 2;
        int midH = height - b * 2;

        blit(tex, atlas, x, y, b, b, ru, rv, srcBorder, srcBorder);
        blit(tex, atlas, x + width - b, y, b, b, ru + region.regionW() - srcBorder, rv, srcBorder, srcBorder);
        blit(
            tex,
            atlas,
            x,
            y + height - b,
            b,
            b,
            ru,
            rv + region.regionH() - srcBorder,
            srcBorder,
            srcBorder);
        blit(
            tex,
            atlas,
            x + width - b,
            y + height - b,
            b,
            b,
            ru + region.regionW() - srcBorder,
            rv + region.regionH() - srcBorder,
            srcBorder,
            srcBorder);

        if (midW > 0) {
            blit(tex, atlas, x + b, y, midW, b, ru + srcBorder, rv, srcMidW, srcBorder);
            blit(
                tex,
                atlas,
                x + b,
                y + height - b,
                midW,
                b,
                ru + srcBorder,
                rv + region.regionH() - srcBorder,
                srcMidW,
                srcBorder);
        }
        if (midH > 0) {
            blit(tex, atlas, x, y + b, b, midH, ru, rv + srcBorder, srcBorder, srcMidH);
            blit(
                tex,
                atlas,
                x + width - b,
                y + b,
                b,
                midH,
                ru + region.regionW() - srcBorder,
                rv + srcBorder,
                srcBorder,
                srcMidH);
        }
        if (midW > 0 && midH > 0) {
            blit(tex, atlas, x + b, y + b, midW, midH, ru + srcBorder, rv + srcBorder, srcMidW, srcMidH);
        }

        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * Horizontal 3-slice: left cap + stretched center + right cap.
     * Used for buttons that scale in width only.
     */
    public static void drawHorizontalSlice(NineSliceRegion region, int x, int y, int width, int height) {
        if (region == null || width <= 0 || height <= 0) {
            return;
        }
        int cap = Math.min(region.borderPx(), width / 2);
        if (cap <= 0) {
            return;
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int atlas = region.atlasSize();
        ResourceLocation tex = region.texture();
        int ru = region.u();
        int rv = region.v();
        int srcCap = region.borderPx();
        int srcMidW = region.srcMidW();
        int midW = width - cap * 2;

        blit(tex, atlas, x, y, cap, height, ru, rv, srcCap, region.regionH());
        if (midW > 0) {
            blit(tex, atlas, x + cap, y, midW, height, ru + srcCap, rv, srcMidW, region.regionH());
        }
        blit(
            tex,
            atlas,
            x + width - cap,
            y,
            cap,
            height,
            ru + region.regionW() - srcCap,
            rv,
            srcCap,
            region.regionH());

        GL11.glDisable(GL11.GL_BLEND);
    }

    public static boolean hasResource(ResourceLocation location) {
        if (location == null) {
            return false;
        }
        try {
            return Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location) != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
