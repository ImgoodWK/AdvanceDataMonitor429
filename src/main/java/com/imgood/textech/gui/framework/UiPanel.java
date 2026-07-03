package com.imgood.textech.gui.framework;

import net.minecraft.client.gui.Gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 9-slice panel drawing with solid-color fallback when the theme atlas is missing.
 */
@SideOnly(Side.CLIENT)
public final class UiPanel {

    /** Default fallback when no theme atlas is available. */
    public static final int FALLBACK_PANEL_BG = 0xFFC8C8C8;
    public static final int FALLBACK_SECTION_LINE = 0xFF909090;

    private UiPanel() {}

    public static void draw(UiTheme theme, int x, int y, int width, int height) {
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

    public static void drawSolidFallback(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, FALLBACK_PANEL_BG);
    }

    /** Horizontal separator line inside a panel. */
    public static void drawDivider(int x, int y, int width) {
        if (width <= 0) {
            return;
        }
        Gui.drawRect(x, y, x + width, y + 1, FALLBACK_SECTION_LINE);
    }
}
