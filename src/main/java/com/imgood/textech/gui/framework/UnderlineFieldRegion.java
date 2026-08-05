package com.imgood.textech.gui.framework;

/** Left/right strokes plus a longest bottom stroke that is centre-cropped, never tiled or stretched horizontally. */
public final class UnderlineFieldRegion {

    public enum State {
        NORMAL,
        FOCUSED,
        INVALID,
        DISABLED
    }

    public static final class Style {

        private final AtlasRegion left;
        private final AtlasRegion right;
        private final AtlasRegion bottom;

        private Style(AtlasRegion left, AtlasRegion right, AtlasRegion bottom) {
            if (left == null || right == null || bottom == null) {
                throw new IllegalArgumentException("Underline field pieces must not be null");
            }
            if (left.height() != right.height()) {
                throw new IllegalArgumentException("Field side strokes must have equal height");
            }
            this.left = left;
            this.right = right;
            this.bottom = bottom;
        }

        public AtlasRegion left() {
            return left;
        }

        public AtlasRegion right() {
            return right;
        }

        public AtlasRegion bottom() {
            return bottom;
        }
    }

    private final Style[] styles = new Style[State.values().length];

    public static State stateFor(boolean enabled, boolean invalid, boolean focused) {
        if (!enabled) return State.DISABLED;
        if (invalid) return State.INVALID;
        return focused ? State.FOCUSED : State.NORMAL;
    }

    public UnderlineFieldRegion(Style normal, Style focused, Style invalid, Style disabled) {
        styles[State.NORMAL.ordinal()] = require(normal, State.NORMAL);
        styles[State.FOCUSED.ordinal()] = require(focused, State.FOCUSED);
        styles[State.INVALID.ordinal()] = require(invalid, State.INVALID);
        styles[State.DISABLED.ordinal()] = require(disabled, State.DISABLED);
    }

    public static Style style(AtlasRegion left, AtlasRegion right, AtlasRegion bottom) {
        return new Style(left, right, bottom);
    }

    private static Style require(Style style, State state) {
        if (style == null) {
            throw new IllegalArgumentException(state.name());
        }
        return style;
    }

    public Style style(State state) {
        return styles[state.ordinal()];
    }

    public float uniformScaleForHeight(State state, int destinationHeight) {
        if (destinationHeight <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, destinationHeight / (float) style(state).left().height());
    }

    public AtlasRegion centeredBottomCrop(State state, int sourceWidth) {
        Style style = style(state);
        if (sourceWidth <= 0 || sourceWidth > style.bottom().width()) {
            throw new IllegalArgumentException("Requested underline exceeds the longest source");
        }
        int offset = (style.bottom().width() - sourceWidth) / 2;
        return style.bottom().subRegion(offset, 0, sourceWidth, style.bottom().height());
    }
}
