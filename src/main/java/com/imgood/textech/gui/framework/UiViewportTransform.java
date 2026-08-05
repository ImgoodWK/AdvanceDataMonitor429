package com.imgood.textech.gui.framework;

/** Uniform logical-canvas transform used by responsive non-container screens. */
public final class UiViewportTransform {

    private final int logicalWidth;
    private final int logicalHeight;
    private final float scale;
    private final float originX;
    private final float originY;

    private UiViewportTransform(int logicalWidth, int logicalHeight, float scale, float originX, float originY) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.scale = scale;
        this.originX = originX;
        this.originY = originY;
    }

    public static UiViewportTransform fit(int viewportWidth, int viewportHeight, int logicalWidth, int logicalHeight,
        int margin) {
        if (logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("Logical canvas dimensions must be positive");
        }
        int safeMargin = Math.max(0, margin);
        int availableWidth = Math.max(1, viewportWidth - safeMargin * 2);
        int availableHeight = Math.max(1, viewportHeight - safeMargin * 2);
        float scale = Math.min(1.0F, Math.min(availableWidth / (float) logicalWidth, availableHeight / (float) logicalHeight));
        float drawnWidth = logicalWidth * scale;
        float drawnHeight = logicalHeight * scale;
        return new UiViewportTransform(
            logicalWidth,
            logicalHeight,
            scale,
            (viewportWidth - drawnWidth) * 0.5F,
            (viewportHeight - drawnHeight) * 0.5F);
    }

    /**
     * Fit a centered logical panel while retaining screen-space coordinates as the logical coordinate system.
     *
     * <p>Legacy ADM screens position controls around {@code width / 2} and {@code height / 2}. Scaling an
     * independent fixed canvas would move those controls away from their panel. This transform instead scales the
     * complete logical viewport about its centre, so existing coordinates, hit tests, keyboard focus, and tooltip
     * anchors continue to agree.</p>
     */
    public static UiViewportTransform fitCenteredBounds(int viewportWidth, int viewportHeight, int contentWidth,
        int contentHeight, int margin) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || contentWidth <= 0 || contentHeight <= 0) {
            throw new IllegalArgumentException("Viewport and content dimensions must be positive");
        }
        int safeMargin = Math.max(0, margin);
        int availableWidth = Math.max(1, viewportWidth - safeMargin * 2);
        int availableHeight = Math.max(1, viewportHeight - safeMargin * 2);
        float scale = Math.min(
            1.0F,
            Math.min(availableWidth / (float) contentWidth, availableHeight / (float) contentHeight));
        return new UiViewportTransform(
            viewportWidth,
            viewportHeight,
            scale,
            (viewportWidth - viewportWidth * scale) * 0.5F,
            (viewportHeight - viewportHeight * scale) * 0.5F);
    }

    public int logicalWidth() {
        return logicalWidth;
    }

    public int logicalHeight() {
        return logicalHeight;
    }

    public float scale() {
        return scale;
    }

    public float originX() {
        return originX;
    }

    public float originY() {
        return originY;
    }

    public int toLogicalX(int screenX) {
        return Math.round((screenX - originX) / scale);
    }

    public int toLogicalY(int screenY) {
        return Math.round((screenY - originY) / scale);
    }

    public int toScreenX(int logicalX) {
        return Math.round(originX + logicalX * scale);
    }

    public int toScreenY(int logicalY) {
        return Math.round(originY + logicalY * scale);
    }

    public int screenWidth() {
        return Math.round(logicalWidth * scale);
    }

    public int screenHeight() {
        return Math.round(logicalHeight * scale);
    }
}
