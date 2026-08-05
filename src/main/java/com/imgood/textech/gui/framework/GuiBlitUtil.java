package com.imgood.textech.gui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shared texture blit helpers. ADM chrome uses sparse pieces and complete controls.
 */
@SideOnly(Side.CLIENT)
public final class GuiBlitUtil {

    private GuiBlitUtil() {}

    public static void blit(ResourceLocation texture, int atlasSize, int x, int y, int w, int h, int u, int v, int sw,
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

    public static void drawRegion(AtlasRegion region, int x, int y) {
        if (region == null) {
            return;
        }
        pushAlphaBlend();
        try {
            blit(
                region.texture(),
                region.atlasSize(),
                x,
                y,
                region.width(),
                region.height(),
                region.u(),
                region.v(),
                region.width(),
                region.height());
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** Draw one source region with a uniform scale on both axes. */
    public static void drawRegionScaled(AtlasRegion region, int x, int y, float scale) {
        if (region == null || scale <= 0.0F) return;
        pushAlphaBlend();
        try {
            blitScaled(region, x, y, scale);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** Fill a destination with one uniformly scaled source region, cropping equally from opposite sides. */
    public static void drawCoverCropped(AtlasRegion region, int x, int y, int width, int height) {
        if (region == null || width <= 0 || height <= 0) {
            return;
        }
        pushAlphaBlend();
        try {
            drawCoverCroppedUnchecked(region, x, y, width, height);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** Draw the background and each of the four corners exactly once. */
    public static void drawSparseFrame(SparseFrameRegion frame, int x, int y, int width, int height) {
        if (frame == null || width <= 0 || height <= 0) {
            return;
        }
        float scale = frame.uniformScaleFor(width, height);
        if (scale <= 0.0F) {
            return;
        }
        int[][] positions = sparseChromePositions(frame, x, y, width, height, scale);
        AtlasRegion[] pieces = {
            frame.topLeft(),
            frame.topRight(),
            frame.bottomLeft(),
            frame.bottomRight()
        };

        pushAlphaBlend();
        try {
            drawCoverCroppedUnchecked(frame.background(), x, y, width, height);
            for (int i = 0; i < pieces.length; i++) {
                blitScaled(pieces[i], positions[i][0], positions[i][1], scale);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** Draw one complete fixed-aspect button shell. No runtime slicing or tiling is performed. */
    public static void drawFixedAspectButton(FixedAspectButtonFamily family, FixedAspectButtonFamily.State state,
        int x, int y, int requestedWidth, int requestedHeight) {
        if (family == null || state == null || requestedWidth <= 0 || requestedHeight <= 0) {
            return;
        }
        int drawWidth = family.normalizedWidth(requestedWidth, requestedHeight);
        int drawHeight = family.normalizedHeight(requestedWidth, requestedHeight);
        int drawX = x + (requestedWidth - drawWidth) / 2;
        int drawY = y + (requestedHeight - drawHeight) / 2;
        AtlasRegion region = family.region(state, requestedWidth, requestedHeight);
        float scale = drawHeight / (float) FixedAspectButtonFamily.BASE_HEIGHT;
        pushAlphaBlend();
        try {
            blitScaled(region, drawX, drawY, scale);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** Draw two side strokes and one centre-cropped bottom stroke, all at one uniform scale. */
    public static void drawUnderlineField(UnderlineFieldRegion field, UnderlineFieldRegion.State state,
        int x, int y, int width, int height) {
        if (field == null || state == null || width <= 0 || height <= 0) {
            return;
        }
        UnderlineFieldRegion.Style style = field.style(state);
        float scale = field.uniformScaleForHeight(state, height);
        if (scale <= 0.0F) {
            return;
        }
        int leftWidth = scaled(style.left().width(), scale);
        int rightWidth = scaled(style.right().width(), scale);
        int maximumBottomWidth = scaled(style.bottom().width(), scale);
        int drawWidth = Math.min(width, leftWidth + maximumBottomWidth + rightWidth);
        int drawX = x + (width - drawWidth) / 2;
        int availableBottomWidth = Math.max(1, drawWidth - leftWidth - rightWidth);
        int sideY = y + height - scaled(style.left().height(), scale);
        int bottomY = y + height - scaled(style.bottom().height(), scale);
        double requestedSourceWidth = Math.min(style.bottom().width(), availableBottomWidth / (double) scale);
        double sourceOffset = (style.bottom().width() - requestedSourceWidth) / 2.0D;

        pushAlphaBlend();
        try {
            blitScaled(style.left(), drawX, sideY, scale);
            blitScaled(style.right(), drawX + drawWidth - rightWidth, sideY, scale);
            blitUv(
                style.bottom().texture(),
                style.bottom().atlasSize(),
                drawX + leftWidth,
                bottomY,
                availableBottomWidth,
                scaled(style.bottom().height(), scale),
                style.bottom().u() + sourceOffset,
                style.bottom().v(),
                requestedSourceWidth,
                style.bottom().height());
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** Draw one ornament once, preserving aspect ratio and only shrinking when space is insufficient. */
    public static void drawCenteredRegion(AtlasRegion region, int x, int y, int width, int height) {
        if (region == null || width <= 0 || height <= 0) {
            return;
        }
        float scale = Math.min(1.0F, Math.min(width / (float) region.width(), height / (float) region.height()));
        int drawWidth = scaled(region.width(), scale);
        int drawHeight = scaled(region.height(), scale);
        drawRegionScaled(region, x + (width - drawWidth) / 2, y + (height - drawHeight) / 2, scale);
    }

    /** Source-space crop for a mathematically uniform cover operation. */
    static double[] coverCrop(int sourceWidth, int sourceHeight, int destinationWidth, int destinationHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || destinationWidth <= 0 || destinationHeight <= 0) {
            return null;
        }
        double sourceAspect = sourceWidth / (double) sourceHeight;
        double destinationAspect = destinationWidth / (double) destinationHeight;
        if (sourceAspect > destinationAspect) {
            double cropWidth = sourceHeight * destinationAspect;
            return new double[] { (sourceWidth - cropWidth) / 2.0D, 0.0D, cropWidth, sourceHeight };
        }
        double cropHeight = sourceWidth / destinationAspect;
        return new double[] { 0.0D, (sourceHeight - cropHeight) / 2.0D, sourceWidth, cropHeight };
    }

    static int[][] sparseChromePositions(SparseFrameRegion frame, int x, int y, int width, int height, float scale) {
        int trW = scaled(frame.topRight().width(), scale);
        int blH = scaled(frame.bottomLeft().height(), scale);
        int brW = scaled(frame.bottomRight().width(), scale);
        int brH = scaled(frame.bottomRight().height(), scale);
        return new int[][] {
            { x, y },
            { x + width - trW, y },
            { x, y + height - blH },
            { x + width - brW, y + height - brH }
        };
    }

    /** Tile at native pixel size; the final tile is UV-clipped, never scaled. */
    public static void drawTiled(AtlasRegion region, int x, int y, int width, int height) {
        if (region == null || width <= 0 || height <= 0) {
            return;
        }
        pushAlphaBlend();
        try {
            drawTiledUnchecked(region, x, y, width, height);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static void drawTiledFrame(TiledFrameRegion frame, int x, int y, int width, int height) {
        if (frame == null || width <= 0 || height <= 0) {
            return;
        }
        int left = frame.leftWidth();
        int right = frame.rightWidth();
        int top = frame.topHeight();
        int bottom = frame.bottomHeight();
        if (width < left + right || height < top + bottom) {
            if (frame.fill() != null) {
                drawTiled(frame.fill(), x, y, width, height);
            }
            return;
        }

        int middleWidth = width - left - right;
        int middleHeight = height - top - bottom;
        pushAlphaBlend();
        try {
            if (frame.fill() != null && middleWidth > 0 && middleHeight > 0) {
                drawTiledUnchecked(frame.fill(), x + left, y + top, middleWidth, middleHeight);
            }
            drawTiledUnchecked(frame.topEdge(), x + left, y, middleWidth, top);
            drawTiledUnchecked(frame.bottomEdge(), x + left, y + height - bottom, middleWidth, bottom);
            drawTiledUnchecked(frame.leftEdge(), x, y + top, left, middleHeight);
            drawTiledUnchecked(frame.rightEdge(), x + width - right, y + top, right, middleHeight);
            blitExact(frame.topLeft(), x, y);
            blitExact(frame.topRight(), x + width - frame.topRight().width(), y);
            blitExact(frame.bottomLeft(), x, y + height - frame.bottomLeft().height());
            blitExact(
                frame.bottomRight(),
                x + width - frame.bottomRight().width(),
                y + height - frame.bottomRight().height());
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static void drawTiledBar(TiledBarRegion bar, int x, int y, int width, int height) {
        if (bar == null || width <= 0 || height <= 0) {
            return;
        }
        int drawHeight = Math.min(height, bar.height());
        int sourceYOffset = (bar.height() - drawHeight) / 2;
        int drawY = y + (height - drawHeight) / 2;
        AtlasRegion left = verticalClip(bar.left(), sourceYOffset, drawHeight);
        AtlasRegion center = verticalClip(bar.center(), sourceYOffset, drawHeight);
        AtlasRegion right = verticalClip(bar.right(), sourceYOffset, drawHeight);
        int capsWidth = left.width() + right.width();

        pushAlphaBlend();
        try {
            if (width < capsWidth) {
                drawTiledUnchecked(center, x, drawY, width, drawHeight);
                return;
            }
            blitExact(left, x, drawY);
            drawTiledUnchecked(center, x + left.width(), drawY, width - capsWidth, drawHeight);
            blitExact(right, x + width - right.width(), drawY);
        } finally {
            GL11.glPopAttrib();
        }
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
        if (AdmUiTheme.ATLAS.equals(region.texture())) {
            drawAdmCompatibilityRegion(region, x, y, width, height);
            return;
        }
        if (borderPx <= 0) {
            return;
        }
        int border = Math.min(borderPx, Math.min(width, height) / 2);
        if (border <= 0) {
            return;
        }

        pushAlphaBlend();
        try {

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
        blit(tex, atlas, x, y + height - b, b, b, ru, rv + region.regionH() - srcBorder, srcBorder, srcBorder);
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

        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * Horizontal 3-slice compatibility entry point. ADM controls use fixed caps plus a tiled center.
     */
    public static void drawHorizontalSlice(NineSliceRegion region, int x, int y, int width, int height) {
        if (region == null || width <= 0 || height <= 0) {
            return;
        }
        if (AdmUiTheme.ATLAS.equals(region.texture())) {
            drawAdmCompatibilityRegion(region, x, y, width, height);
            return;
        }
        int cap = Math.min(region.borderPx(), width / 2);
        if (cap <= 0) {
            return;
        }

        pushAlphaBlend();
        try {

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
        blit(tex, atlas, x + width - cap, y, cap, height, ru + region.regionW() - srcCap, rv, srcCap, region.regionH());

        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * Keeps the old {@link NineSliceRegion} API safe when an ADM descriptor reaches a legacy extension point.
     * The ADM atlas is never sliced or tiled here: panels use four corners, controls use their exact assets.
     */
    private static void drawAdmCompatibilityRegion(NineSliceRegion region, int x, int y, int width, int height) {
        AdmUiTheme theme = AdmUiTheme.instance();
        if (region == theme.mainPanel()) {
            drawSparseFrame(theme.sparseMainFrame(), x, y, width, height);
            return;
        }
        if (region == theme.sectionPanel()) {
            drawSparseFrame(theme.sparseSectionFrame(), x, y, width, height);
            return;
        }

        FixedAspectButtonFamily.State buttonState = region == theme.buttonDisabled()
            ? FixedAspectButtonFamily.State.DISABLED
            : region == theme.buttonPressed() ? FixedAspectButtonFamily.State.PRESSED
                : region == theme.buttonHover() ? FixedAspectButtonFamily.State.HOVER
                    : region == theme.buttonNormal() ? FixedAspectButtonFamily.State.NORMAL : null;
        if (buttonState != null) {
            drawFixedAspectButton(theme.fixedAspectButtons(), buttonState, x, y, width, height);
            return;
        }

        UnderlineFieldRegion.State fieldState = region == theme.textFieldDisabled()
            ? UnderlineFieldRegion.State.DISABLED
            : region == theme.textFieldInvalid() ? UnderlineFieldRegion.State.INVALID
                : region == theme.textFieldFocused() ? UnderlineFieldRegion.State.FOCUSED
                    : region == theme.textFieldNormal() ? UnderlineFieldRegion.State.NORMAL : null;
        if (fieldState != null) {
            drawUnderlineField(theme.underlineField(), fieldState, x, y, width, height);
            return;
        }

        AtlasRegion exact = region == theme.slot() ? theme.slotRegion()
            : region == theme.scrollTrack() ? theme.scrollTrackRegion()
                : region == theme.scrollThumb() ? theme.scrollThumbRegion()
                    : region == theme.divider() ? theme.dividerRegion()
                        : region == theme.toggleOff() ? theme.toggleOffRegion()
                            : region == theme.toggleOn() ? theme.toggleOnRegion()
                                : region == theme.toggleDisabled() ? theme.toggleDisabledRegion()
                                    : region == theme.checkOff() ? theme.checkOffRegion()
                                        : region == theme.checkOn() ? theme.checkOnRegion()
                                            : region == theme.checkDisabled() ? theme.checkDisabledRegion() : null;
        if (exact != null) {
            if (region == theme.scrollTrack() || region == theme.scrollThumb()) {
                drawCoverCropped(exact, x, y, width, height);
            } else {
                drawCenteredRegion(exact, x, y, width, height);
            }
            return;
        }

        drawCoverCropped(
            new AtlasRegion(
                region.texture(),
                region.atlasSize(),
                region.u(),
                region.v(),
                region.regionW(),
                region.regionH()),
            x,
            y,
            width,
            height);
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

    /** Fit a full texture inside {@code width}×{@code height} while preserving its aspect ratio. */
    public static void drawFullTexture(ResourceLocation texture, int x, int y, int width, int height) {
        if (texture == null || width <= 0 || height <= 0) {
            return;
        }
        pushAlphaBlend();
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            int sourceWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int sourceHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            int[] fitted = fitInside(x, y, width, height, sourceWidth, sourceHeight);
            if (fitted == null) {
                return;
            }
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(fitted[0], fitted[1] + fitted[3], 0, 0, 1);
            tessellator.addVertexWithUV(fitted[0] + fitted[2], fitted[1] + fitted[3], 0, 1, 1);
            tessellator.addVertexWithUV(fitted[0] + fitted[2], fitted[1], 0, 1, 0);
            tessellator.addVertexWithUV(fitted[0], fitted[1], 0, 0, 0);
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    static int[] fitInside(int x, int y, int maxWidth, int maxHeight, int sourceWidth, int sourceHeight) {
        if (maxWidth <= 0 || maxHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return null;
        }
        double scale = Math.min((double) maxWidth / sourceWidth, (double) maxHeight / sourceHeight);
        int drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        return new int[] { x + (maxWidth - drawWidth) / 2, y + (maxHeight - drawHeight) / 2, drawWidth, drawHeight };
    }

    static AtlasRegion clippedTile(AtlasRegion region, int width, int height) {
        if (region == null || width <= 0 || height <= 0 || width > region.width() || height > region.height()) {
            throw new IllegalArgumentException("Tile clip exceeds source region");
        }
        return width == region.width() && height == region.height() ? region : region.subRegion(0, 0, width, height);
    }

    private static int scaled(int value, float scale) {
        return Math.max(1, Math.round(value * scale));
    }

    private static void drawCoverCroppedUnchecked(AtlasRegion region, int x, int y, int width, int height) {
        double[] crop = coverCrop(region.width(), region.height(), width, height);
        if (crop == null) return;
        blitUv(region.texture(), region.atlasSize(), x, y, width, height, region.u() + crop[0],
            region.v() + crop[1], crop[2], crop[3]);
    }

    private static void blitScaled(AtlasRegion region, int x, int y, float scale) {
        blit(region.texture(), region.atlasSize(), x, y, scaled(region.width(), scale),
            scaled(region.height(), scale), region.u(), region.v(), region.width(), region.height());
    }

    private static void blitUv(ResourceLocation texture, int atlasSize, int x, int y, int width, int height,
        double u, double v, double sourceWidth, double sourceHeight) {
        if (texture == null || atlasSize <= 0 || width <= 0 || height <= 0 || sourceWidth <= 0.0D
            || sourceHeight <= 0.0D) {
            return;
        }
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        double tex = atlasSize;
        double u0 = u / tex;
        double v0 = v / tex;
        double u1 = (u + sourceWidth) / tex;
        double v1 = (v + sourceHeight) / tex;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0, u0, v1);
        tessellator.addVertexWithUV(x + width, y + height, 0, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0, u0, v0);
        tessellator.draw();
    }

    private static void drawTiledUnchecked(AtlasRegion region, int x, int y, int width, int height) {
        if (region == null || width <= 0 || height <= 0) {
            return;
        }
        for (int drawY = 0; drawY < height; drawY += region.height()) {
            int tileHeight = Math.min(region.height(), height - drawY);
            for (int drawX = 0; drawX < width; drawX += region.width()) {
                int tileWidth = Math.min(region.width(), width - drawX);
                blitExact(clippedTile(region, tileWidth, tileHeight), x + drawX, y + drawY);
            }
        }
    }

    private static void blitExact(AtlasRegion region, int x, int y) {
        blit(
            region.texture(),
            region.atlasSize(),
            x,
            y,
            region.width(),
            region.height(),
            region.u(),
            region.v(),
            region.width(),
            region.height());
    }

    private static AtlasRegion verticalClip(AtlasRegion region, int offset, int height) {
        return offset == 0 && height == region.height() ? region : region.subRegion(0, offset, region.width(), height);
    }

    private static void pushAlphaBlend() {
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
