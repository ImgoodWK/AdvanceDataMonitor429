package com.imgood.textech.gui.framework;

/** Complete, non-sliced button shells for seven fixed aspect-ratio families and four visual states. */
public final class FixedAspectButtonFamily {

    public enum State {
        NORMAL,
        HOVER,
        PRESSED,
        DISABLED
    }

    public static final int BASE_HEIGHT = 20;
    private static final int[] BASE_WIDTHS = { 20, 50, 60, 80, 100, 200, 240 };

    private final AtlasRegion[][] regions = new AtlasRegion[State.values().length][BASE_WIDTHS.length];

    public FixedAspectButtonFamily(AtlasRegion[] normal, AtlasRegion[] hover, AtlasRegion[] pressed,
        AtlasRegion[] disabled) {
        assign(State.NORMAL, normal);
        assign(State.HOVER, hover);
        assign(State.PRESSED, pressed);
        assign(State.DISABLED, disabled);
    }

    private void assign(State state, AtlasRegion[] stateRegions) {
        if (stateRegions == null || stateRegions.length != BASE_WIDTHS.length) {
            throw new IllegalArgumentException(state + " must define all seven button families");
        }
        for (int i = 0; i < stateRegions.length; i++) {
            AtlasRegion region = stateRegions[i];
            if (region == null || region.width() != BASE_WIDTHS[i] || region.height() != BASE_HEIGHT) {
                throw new IllegalArgumentException(
                    state + " family " + BASE_WIDTHS[i] + " must be a complete " + BASE_WIDTHS[i] + "x20 shell");
            }
            regions[state.ordinal()][i] = region;
        }
    }

    public AtlasRegion region(State state, int requestedWidth, int requestedHeight) {
        return regions[state.ordinal()][familyIndexFor(requestedWidth, requestedHeight)];
    }

    public int normalizedWidth(int requestedWidth, int requestedHeight) {
        return normalizedWidthFor(requestedWidth, requestedHeight);
    }

    public int normalizedHeight(int requestedWidth, int requestedHeight) {
        return normalizedHeightFor(requestedWidth, requestedHeight);
    }

    /**
     * Fits the nearest complete shell inside the requested bounds. Normalization may shrink a control but must never
     * expand beyond the layout rectangle supplied by its caller.
     */
    public static int normalizedWidthFor(int requestedWidth, int requestedHeight) {
        int familyWidth = BASE_WIDTHS[familyIndexFor(requestedWidth, requestedHeight)];
        int height = fittedHeightFor(familyWidth, requestedWidth, requestedHeight);
        return Math.max(1, Math.round(familyWidth * (height / (float) BASE_HEIGHT)));
    }

    public static int normalizedHeightFor(int requestedWidth, int requestedHeight) {
        int familyWidth = BASE_WIDTHS[familyIndexFor(requestedWidth, requestedHeight)];
        return fittedHeightFor(familyWidth, requestedWidth, requestedHeight);
    }

    public static int familyIndexFor(int requestedWidth, int requestedHeight) {
        double ratio = Math.max(1, requestedWidth) / (double) Math.max(1, requestedHeight);
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < BASE_WIDTHS.length; i++) {
            double familyRatio = BASE_WIDTHS[i] / (double) BASE_HEIGHT;
            double distance = Math.abs(familyRatio - ratio);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static int familyCount() {
        return BASE_WIDTHS.length;
    }

    public static int baseWidth(int familyIndex) {
        return BASE_WIDTHS[familyIndex];
    }

    private static int fittedHeightFor(int familyWidth, int requestedWidth, int requestedHeight) {
        int maxWidth = Math.max(1, requestedWidth);
        int height = Math
            .max(1, Math.min(requestedHeight, (int) Math.floor(maxWidth * BASE_HEIGHT / (double) familyWidth)));
        while (height > 1 && Math.round(familyWidth * (height / (float) BASE_HEIGHT)) > maxWidth) {
            height--;
        }
        return height;
    }
}
