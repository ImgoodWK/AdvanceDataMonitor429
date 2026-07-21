package com.imgood.textech.gui.framework.layout;

/**
 * CSS-style insets: top, right, bottom, left.
 */
public final class UiInsets {

    public static final UiInsets ZERO = new UiInsets(0, 0, 0, 0);

    public final int top;
    public final int right;
    public final int bottom;
    public final int left;

    public UiInsets(int top, int right, int bottom, int left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    public static UiInsets all(int v) {
        return new UiInsets(v, v, v, v);
    }

    public static UiInsets symmetric(int vertical, int horizontal) {
        return new UiInsets(vertical, horizontal, vertical, horizontal);
    }

    public int horizontal() {
        return left + right;
    }

    public int vertical() {
        return top + bottom;
    }
}
