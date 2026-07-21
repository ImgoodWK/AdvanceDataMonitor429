package com.imgood.textech.gui.framework.style;

import com.imgood.textech.gui.framework.NineSliceRegion;
import com.imgood.textech.gui.framework.layout.UiInsets;

import net.minecraft.util.ResourceLocation;

/**
 * Per-widget style: padding, margin, gap, background, text colors.
 */
public final class UiStyle {

    private UiInsets padding = UiInsets.ZERO;
    private UiInsets margin = UiInsets.ZERO;
    private int gap;
    private UiBackground background = UiBackground.none();
    private int textColor = -1;
    private int textHoverColor = -1;
    private boolean visible = true;

    public UiStyle padding(int all) {
        this.padding = UiInsets.all(all);
        return this;
    }

    public UiStyle padding(int top, int right, int bottom, int left) {
        this.padding = new UiInsets(top, right, bottom, left);
        return this;
    }

    public UiStyle padding(UiInsets insets) {
        this.padding = insets != null ? insets : UiInsets.ZERO;
        return this;
    }

    public UiStyle margin(int all) {
        this.margin = UiInsets.all(all);
        return this;
    }

    public UiStyle margin(int top, int right, int bottom, int left) {
        this.margin = new UiInsets(top, right, bottom, left);
        return this;
    }

    public UiStyle margin(UiInsets insets) {
        this.margin = insets != null ? insets : UiInsets.ZERO;
        return this;
    }

    public UiStyle gap(int gap) {
        this.gap = Math.max(0, gap);
        return this;
    }

    public UiStyle background(UiBackground background) {
        this.background = background != null ? background : UiBackground.none();
        return this;
    }

    public UiStyle backgroundSolid(int argb) {
        this.background = UiBackground.solid(argb);
        return this;
    }

    public UiStyle backgroundNineSlice(NineSliceRegion region) {
        this.background = UiBackground.nineSlice(region);
        return this;
    }

    public UiStyle backgroundTexture(ResourceLocation texture) {
        this.background = UiBackground.fullTexture(texture);
        return this;
    }

    public UiStyle textColor(int rgb) {
        this.textColor = rgb;
        return this;
    }

    public UiStyle textHoverColor(int rgb) {
        this.textHoverColor = rgb;
        return this;
    }

    public UiStyle visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public UiInsets padding() {
        return padding;
    }

    public UiInsets margin() {
        return margin;
    }

    public int gap() {
        return gap;
    }

    public UiBackground background() {
        return background;
    }

    public int textColor() {
        return textColor;
    }

    public int textHoverColor() {
        return textHoverColor;
    }

    public boolean visible() {
        return visible;
    }

    public UiStyle copy() {
        UiStyle s = new UiStyle();
        s.padding = padding;
        s.margin = margin;
        s.gap = gap;
        s.background = background;
        s.textColor = textColor;
        s.textHoverColor = textHoverColor;
        s.visible = visible;
        return s;
    }
}
