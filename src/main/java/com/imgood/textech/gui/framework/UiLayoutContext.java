package com.imgood.textech.gui.framework;

import net.minecraft.client.gui.FontRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Screen-space layout helpers for container GUIs.
 * Background layer uses absolute coordinates ({@code guiLeft + localX});
 * foreground layer uses GUI-local coordinates ({@code localX}).
 */
@SideOnly(Side.CLIENT)
public final class UiLayoutContext {

    private final UiTheme theme;
    private final FontRenderer font;
    private final int guiLeft;
    private final int guiTop;
    private final int guiWidth;
    private final int guiHeight;

    public UiLayoutContext(UiTheme theme, FontRenderer font, int guiLeft, int guiTop, int guiWidth, int guiHeight) {
        this.theme = theme;
        this.font = font;
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
    }

    public UiTheme theme() {
        return theme;
    }

    public FontRenderer font() {
        return font;
    }

    public int guiLeft() {
        return guiLeft;
    }

    public int guiTop() {
        return guiTop;
    }

    public int guiWidth() {
        return guiWidth;
    }

    public int guiHeight() {
        return guiHeight;
    }

    /** Absolute screen X for background-layer drawing. */
    public int absX(int localX) {
        return guiLeft + localX;
    }

    /** Absolute screen Y for background-layer drawing. */
    public int absY(int localY) {
        return guiTop + localY;
    }
}
