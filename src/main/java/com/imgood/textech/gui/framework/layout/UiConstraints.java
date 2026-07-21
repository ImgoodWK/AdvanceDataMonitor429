package com.imgood.textech.gui.framework.layout;

/**
 * Parent-imposed size constraints for measure/layout.
 */
public final class UiConstraints {

    public final int minWidth;
    public final int maxWidth;
    public final int minHeight;
    public final int maxHeight;

    public UiConstraints(int minWidth, int maxWidth, int minHeight, int maxHeight) {
        this.minWidth = Math.max(0, minWidth);
        this.maxWidth = Math.max(this.minWidth, maxWidth);
        this.minHeight = Math.max(0, minHeight);
        this.maxHeight = Math.max(this.minHeight, maxHeight);
    }

    public static UiConstraints tight(int width, int height) {
        int w = Math.max(0, width);
        int h = Math.max(0, height);
        return new UiConstraints(w, w, h, h);
    }

    public static UiConstraints loose(int maxWidth, int maxHeight) {
        return new UiConstraints(0, Math.max(0, maxWidth), 0, Math.max(0, maxHeight));
    }

    public int constrainWidth(int width) {
        if (width < minWidth) {
            return minWidth;
        }
        if (width > maxWidth) {
            return maxWidth;
        }
        return width;
    }

    public int constrainHeight(int height) {
        if (height < minHeight) {
            return minHeight;
        }
        if (height > maxHeight) {
            return maxHeight;
        }
        return height;
    }

    public boolean hasBoundedWidth() {
        return maxWidth < Integer.MAX_VALUE / 4;
    }

    public boolean hasBoundedHeight() {
        return maxHeight < Integer.MAX_VALUE / 4;
    }
}
