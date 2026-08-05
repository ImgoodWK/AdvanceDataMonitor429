package com.imgood.textech.gui.framework;

/** Fixed corners plus repeatable edge/fill tiles for a panel frame. */
public final class TiledFrameRegion {

    private final AtlasRegion topLeft;
    private final AtlasRegion topRight;
    private final AtlasRegion bottomLeft;
    private final AtlasRegion bottomRight;
    private final AtlasRegion topEdge;
    private final AtlasRegion bottomEdge;
    private final AtlasRegion leftEdge;
    private final AtlasRegion rightEdge;
    private final AtlasRegion fill;

    public TiledFrameRegion(AtlasRegion topLeft, AtlasRegion topRight, AtlasRegion bottomLeft, AtlasRegion bottomRight,
        AtlasRegion topEdge, AtlasRegion bottomEdge, AtlasRegion leftEdge, AtlasRegion rightEdge, AtlasRegion fill) {
        this.topLeft = require(topLeft, "topLeft");
        this.topRight = require(topRight, "topRight");
        this.bottomLeft = require(bottomLeft, "bottomLeft");
        this.bottomRight = require(bottomRight, "bottomRight");
        this.topEdge = require(topEdge, "topEdge");
        this.bottomEdge = require(bottomEdge, "bottomEdge");
        this.leftEdge = require(leftEdge, "leftEdge");
        this.rightEdge = require(rightEdge, "rightEdge");
        this.fill = fill;
    }

    public static TiledFrameRegion fromNineSlice(NineSliceRegion region) {
        if (region == null || region.borderPx() <= 0) {
            return null;
        }
        int border = region.borderPx();
        int middleWidth = region.srcMidW();
        int middleHeight = region.srcMidH();
        AtlasRegion source = new AtlasRegion(
            region.texture(),
            region.atlasSize(),
            region.u(),
            region.v(),
            region.regionW(),
            region.regionH());
        return new TiledFrameRegion(
            source.subRegion(0, 0, border, border),
            source.subRegion(border + middleWidth, 0, border, border),
            source.subRegion(0, border + middleHeight, border, border),
            source.subRegion(border + middleWidth, border + middleHeight, border, border),
            source.subRegion(border, 0, middleWidth, border),
            source.subRegion(border, border + middleHeight, middleWidth, border),
            source.subRegion(0, border, border, middleHeight),
            source.subRegion(border + middleWidth, border, border, middleHeight),
            source.subRegion(border, border, middleWidth, middleHeight));
    }

    private static AtlasRegion require(AtlasRegion region, String name) {
        if (region == null) {
            throw new IllegalArgumentException(name);
        }
        return region;
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

    public AtlasRegion topEdge() {
        return topEdge;
    }

    public AtlasRegion bottomEdge() {
        return bottomEdge;
    }

    public AtlasRegion leftEdge() {
        return leftEdge;
    }

    public AtlasRegion rightEdge() {
        return rightEdge;
    }

    public AtlasRegion fill() {
        return fill;
    }

    public int leftWidth() {
        return Math.max(topLeft.width(), bottomLeft.width());
    }

    public int rightWidth() {
        return Math.max(topRight.width(), bottomRight.width());
    }

    public int topHeight() {
        return Math.max(topLeft.height(), topRight.height());
    }

    public int bottomHeight() {
        return Math.max(bottomLeft.height(), bottomRight.height());
    }
}
