package com.imgood.textech.gui.framework;

/** Four independent corners and one cover-cropped background. */
public final class SparseFrameRegion {

    private final AtlasRegion topLeft;
    private final AtlasRegion topRight;
    private final AtlasRegion bottomLeft;
    private final AtlasRegion bottomRight;
    private final AtlasRegion background;

    public SparseFrameRegion(AtlasRegion topLeft, AtlasRegion topRight, AtlasRegion bottomLeft, AtlasRegion bottomRight,
        AtlasRegion background) {
        this.topLeft = require(topLeft, "topLeft");
        this.topRight = require(topRight, "topRight");
        this.bottomLeft = require(bottomLeft, "bottomLeft");
        this.bottomRight = require(bottomRight, "bottomRight");
        this.background = require(background, "background");
    }

    private static AtlasRegion require(AtlasRegion region, String name) {
        if (region == null) {
            throw new IllegalArgumentException(name);
        }
        return region;
    }

    /** Shrink all four corners together only when the destination is too small. */
    public float uniformScaleFor(int width, int height) {
        if (width <= 0 || height <= 0) {
            return 0.0F;
        }
        int horizontalNeed = Math.max(topLeft.width() + topRight.width(), bottomLeft.width() + bottomRight.width());
        int verticalNeed = Math.max(topLeft.height() + bottomLeft.height(), topRight.height() + bottomRight.height());
        return Math.min(1.0F, Math.min(width / (float) horizontalNeed, height / (float) verticalNeed));
    }

    public AtlasRegion topLeft() {
        return topLeft;
    }

    public AtlasRegion topRight() {
        return topRight;
    }

    public AtlasRegion bottomLeft() {
        return bottomLeft;
    }

    public AtlasRegion bottomRight() {
        return bottomRight;
    }

    public AtlasRegion background() {
        return background;
    }
}
