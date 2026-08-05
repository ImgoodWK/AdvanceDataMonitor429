package com.imgood.textech.gui.framework;

import net.minecraft.client.gui.Gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Sparse ADM panel drawing with legacy-theme and solid-color fallbacks. */
@SideOnly(Side.CLIENT)
public final class UiPanel {

    /** Default fallback when no theme atlas is available. */
    public static final int FALLBACK_PANEL_BG = 0xFFC8C8C8;
    public static final int FALLBACK_SECTION_LINE = 0xFF909090;

    private UiPanel() {}

    public static void draw(UiTheme theme, int x, int y, int width, int height) {
        SparseFrameRegion sparse = theme != null ? theme.sparseMainFrame() : null;
        if (sparse != null && GuiBlitUtil.hasResource(sparse.topLeft().texture())) {
            GuiBlitUtil.drawSparseFrame(sparse, x, y, width, height);
            return;
        }
        TiledFrameRegion frame = theme != null ? theme.mainFrame() : null;
        if (frame != null && GuiBlitUtil.hasResource(frame.topLeft().texture())) {
            GuiBlitUtil.drawTiledFrame(frame, x, y, width, height);
            return;
        }
        draw(theme, x, y, width, height, theme != null ? theme.mainPanel() : null);
    }

    public static void draw(UiTheme theme, int x, int y, int width, int height, NineSliceRegion region) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (region != null && GuiBlitUtil.hasResource(region.texture())) {
            GuiBlitUtil.drawNineSlice(region, x, y, width, height);
            return;
        }
        drawSolidFallback(x, y, width, height);
    }

    public static void drawSection(UiTheme theme, int x, int y, int width, int height) {
        SparseFrameRegion sparse = theme != null ? theme.sparseSectionFrame() : null;
        if (sparse != null && GuiBlitUtil.hasResource(sparse.topLeft().texture())) {
            GuiBlitUtil.drawSparseFrame(sparse, x, y, width, height);
            return;
        }
        TiledFrameRegion frame = theme != null ? theme.sectionFrame() : null;
        if (frame != null && GuiBlitUtil.hasResource(frame.topLeft().texture())) {
            GuiBlitUtil.drawTiledFrame(frame, x, y, width, height);
            return;
        }
        draw(theme, x, y, width, height, theme != null ? theme.sectionPanel() : null);
    }

    public static void drawSolidFallback(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, FALLBACK_PANEL_BG);
    }

    /** Horizontal separator line inside a panel. */
    public static void drawDivider(int x, int y, int width) {
        drawDivider(null, x, y, width);
    }

    public static void drawDivider(UiTheme theme, int x, int y, int width) {
        if (width <= 0) {
            return;
        }
        AtlasRegion exact = theme != null ? theme.dividerRegion() : null;
        if (exact != null && GuiBlitUtil.hasResource(exact.texture())) {
            GuiBlitUtil.drawCenteredRegion(exact, x, y, width, exact.height());
            return;
        }
        NineSliceRegion divider = theme != null ? theme.divider() : null;
        if (divider != null && GuiBlitUtil.hasResource(divider.texture())) {
            GuiBlitUtil.drawHorizontalSlice(divider, x, y, width, divider.regionH());
            return;
        }
        Gui.drawRect(x, y, x + width, y + 1, FALLBACK_SECTION_LINE);
    }

    public static void drawTitleOrnament(UiTheme theme, int x, int y, int width) {
        AtlasRegion ornament = theme != null ? theme.titleOrnament() : null;
        if (ornament != null && GuiBlitUtil.hasResource(ornament.texture())) {
            GuiBlitUtil.drawCenteredRegion(ornament, x, y, width, ornament.height());
        }
    }

    public static void drawFooterOrnament(UiTheme theme, int x, int y, int width) {
        AtlasRegion ornament = theme != null ? theme.footerOrnament() : null;
        if (ornament != null && GuiBlitUtil.hasResource(ornament.texture())) {
            GuiBlitUtil.drawCenteredRegion(ornament, x, y, width, ornament.height());
        }
    }
}
