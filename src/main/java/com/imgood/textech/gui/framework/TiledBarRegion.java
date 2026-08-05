package com.imgood.textech.gui.framework;

/** Fixed end caps plus a repeatable center tile for a horizontal control. */
public final class TiledBarRegion {

    private final AtlasRegion left;
    private final AtlasRegion center;
    private final AtlasRegion right;

    public TiledBarRegion(AtlasRegion left, AtlasRegion center, AtlasRegion right) {
        if (left == null || center == null || right == null) {
            throw new IllegalArgumentException("Bar regions must not be null");
        }
        if (left.height() != center.height() || right.height() != center.height()) {
            throw new IllegalArgumentException("Bar regions must have the same height");
        }
        this.left = left;
        this.center = center;
        this.right = right;
    }

    public static TiledBarRegion fromHorizontalSlice(NineSliceRegion region) {
        if (region == null || region.borderPx() <= 0) {
            return null;
        }
        AtlasRegion source = new AtlasRegion(
            region.texture(),
            region.atlasSize(),
            region.u(),
            region.v(),
            region.regionW(),
            region.regionH());
        int cap = region.borderPx();
        return new TiledBarRegion(
            source.subRegion(0, 0, cap, region.regionH()),
            source.subRegion(cap, 0, region.srcMidW(), region.regionH()),
            source.subRegion(cap + region.srcMidW(), 0, cap, region.regionH()));
    }

    public AtlasRegion left() {
        return left;
    }

    public AtlasRegion center() {
        return center;
    }

    public AtlasRegion right() {
        return right;
    }

    public int height() {
        return center.height();
    }
}
