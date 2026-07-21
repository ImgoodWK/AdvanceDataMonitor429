package com.imgood.textech.gui.framework.widget;

import net.minecraft.client.gui.FontRenderer;

import com.imgood.textech.gui.framework.UiTheme;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Per-frame render/input context for the widget tree.
 */
@SideOnly(Side.CLIENT)
public final class UiRenderContext {

    private final FontRenderer font;
    private final UiTheme theme;
    private final int originX;
    private final int originY;
    private final int mouseX;
    private final int mouseY;

    public UiRenderContext(FontRenderer font, UiTheme theme, int originX, int originY, int mouseX, int mouseY) {
        this.font = font;
        this.theme = theme;
        this.originX = originX;
        this.originY = originY;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public FontRenderer font() {
        return font;
    }

    public UiTheme theme() {
        return theme;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int mouseX() {
        return mouseX;
    }

    public int mouseY() {
        return mouseY;
    }

    public UiRenderContext withOrigin(int absX, int absY) {
        return new UiRenderContext(font, theme, absX, absY, mouseX, mouseY);
    }
}
