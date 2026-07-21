package com.imgood.textech.gui.framework.layout;

/**
 * Resolved layout rectangle relative to the parent content origin.
 */
public final class UiLayoutBox {

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public UiLayoutBox(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public static UiLayoutBox empty() {
        return new UiLayoutBox(0, 0, 0, 0);
    }

    public boolean equalsBox(UiLayoutBox other) {
        return other != null && x == other.x && y == other.y && width == other.width && height == other.height;
    }
}
