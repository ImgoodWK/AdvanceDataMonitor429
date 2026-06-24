package com.imgood.advancedatamonitor.handler;

/**
 * Shared constants for Empyrean Holy Judgment sword visuals (server + client).
 */
public final class StarryCosmosSwordConstants {

    public static final float ITEM_SPRITE_THICKNESS = 0.0625F;
    public static final float BLADE_ICON_HEIGHT = 128.0F;
    /** Visible blade span vs full icon frame (excludes transparent padding). */
    public static final float BLADE_VISIBLE_HEIGHT_RATIO = 0.52F;
    public static final float SCALE_SLAM = 5.0F;
    /** Shift+right-click judgment rain: same vertical stab as left-click, scaled down. */
    public static final float SCALE_LINE_STAB_MINI = 2.0F;
    /** Extra embed depth below the half-blade surface line (world Y only; no X/Z shift). */
    public static final float SLAM_EMBED_EXTRA_DEPTH = 9.0F;
    /** Client visual pivot nudge (+world X); applied in Render, not inside Z-rolls. */
    public static final float SLAM_RENDER_PIVOT_OFFSET_X = 3.0F;

    private StarryCosmosSwordConstants() {}

    public static float slamBladeLengthBlocks() {
        return BLADE_ICON_HEIGHT * ITEM_SPRITE_THICKNESS * SCALE_SLAM;
    }

    /** Length used for embed / descent positioning (matches on-screen blade). */
    public static float slamBladeVisibleLengthBlocks() {
        return slamBladeVisibleLengthBlocks(SCALE_SLAM);
    }

    public static float slamBladeVisibleLengthBlocks(float renderScale) {
        return BLADE_ICON_HEIGHT * ITEM_SPRITE_THICKNESS * renderScale * BLADE_VISIBLE_HEIGHT_RATIO;
    }

    /** Entity Y when half the visible blade is below {@code groundY}, minus extra embed depth. */
    public static float slamEmbeddedY(float groundY) {
        return slamEmbeddedY(groundY, SCALE_SLAM);
    }

    public static float slamEmbeddedY(float groundY, float renderScale) {
        float embedExtra = SLAM_EMBED_EXTRA_DEPTH * (renderScale / SCALE_SLAM);
        return groundY + slamBladeVisibleLengthBlocks(renderScale) * 0.5F - embedExtra;
    }

    public static float slamRenderPivotOffsetX(float renderScale) {
        return SLAM_RENDER_PIVOT_OFFSET_X * (renderScale / SCALE_SLAM);
    }
}
