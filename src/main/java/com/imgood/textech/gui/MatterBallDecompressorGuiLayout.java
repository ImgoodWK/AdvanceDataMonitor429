package com.imgood.textech.gui;

/**
 * Layout for the matter-ball decompressor GUI.
 * <p>
 * Left gutter: cache / blocking square buttons (outside main panel). Right gutter: upgrade slots (vertical).
 * Main panel anchors: {@link #REF_POINT_A_X}/{@link #REF_POINT_A_Y} — origin;
 * {@link #REF_POINT_B_X}/{@link #REF_POINT_B_Y} — bottom-right of the 9×9 buffer region.
 */
public final class MatterBallDecompressorGuiLayout {

    public static final int CELL = 18;
    public static final int SIDE_BUTTON = 18;
    public static final int EXTERNAL_GAP = 2;

    /** Width of the left gutter (button column + gap to main panel). */
    public static final int LEFT_GUTTER = SIDE_BUTTON + EXTERNAL_GAP;
    /** Width of the right gutter (upgrade column + gap from main panel). */
    public static final int RIGHT_GUTTER = CELL + EXTERNAL_GAP;

    /** Point A — align custom background top-left of the main panel here (after left gutter). */
    public static final int REF_POINT_A_X = LEFT_GUTTER;
    public static final int REF_POINT_A_Y = 0;

    /** Point B — align 9×9 buffer region bottom-right when drawing at max capacity. */
    public static final int REF_POINT_B_X = LEFT_GUTTER + 62 + 9 * CELL;
    public static final int REF_POINT_B_Y = 24 + 9 * CELL;

    public static final int CONTENT_START_Y = 24;

    /** Cache (output mode) button — left gutter, top. */
    public static final int CACHE_BUTTON_X = EXTERNAL_GAP;
    public static final int CACHE_BUTTON_Y = CONTENT_START_Y;

    /** Blocking button — left gutter, below cache button. */
    public static final int BLOCK_BUTTON_X = EXTERNAL_GAP;
    public static final int BLOCK_BUTTON_Y = CACHE_BUTTON_Y + SIDE_BUTTON + EXTERNAL_GAP;

    public static final int INPUT_X = 8;
    /** Fixed origin for the logical 9×9 buffer region (smaller tiers are centered inside). */
    public static final int BUFFER_REGION_X = 62;

    public static final int SPEED_UPGRADE_SLOTS = 4;
    public static final int CAPACITY_UPGRADE_SLOTS = 2;
    public static final int UPGRADE_COUNT = SPEED_UPGRADE_SLOTS + CAPACITY_UPGRADE_SLOTS;

    public static final int INPUT_ROWS = 9;
    public static final int MAX_BUFFER_SIDE = 9;

    public static final int PLAYER_INV_GAP = 8;

    private MatterBallDecompressorGuiLayout() {}

    public static Metrics forBufferSide(int bufferSide) {
        int side = clampBufferSide(bufferSide);
        int maxRegion = MAX_BUFFER_SIDE * CELL;
        int contentHeight = Math.max(INPUT_ROWS * CELL, maxRegion);
        int playerInvY = CONTENT_START_Y + contentHeight + PLAYER_INV_GAP;
        int mainPanelHeight = playerInvY + 58 + 18 + 4;
        int mainPanelWidth = Math.max(176, BUFFER_REGION_X + maxRegion + 8);
        int guiWidth = LEFT_GUTTER + mainPanelWidth + RIGHT_GUTTER;
        int guiHeight = mainPanelHeight;
        int inputX = LEFT_GUTTER + INPUT_X;
        int bufferRegionX = LEFT_GUTTER + BUFFER_REGION_X;
        int playerInvX = LEFT_GUTTER + Math.max(8, (mainPanelWidth - 9 * CELL) / 2);
        int upgradeColumnX = LEFT_GUTTER + mainPanelWidth + EXTERNAL_GAP;
        return new Metrics(
            guiWidth,
            guiHeight,
            mainPanelWidth,
            side,
            inputX,
            bufferRegionX,
            playerInvX,
            playerInvY,
            upgradeColumnX);
    }

    public static int clampBufferSide(int bufferSide) {
        if (bufferSide >= 9) {
            return 9;
        }
        if (bufferSide >= 3) {
            return 3;
        }
        return 1;
    }

    public static int bufferSlotX(Metrics metrics, int col) {
        int gridW = metrics.bufferSide * CELL;
        int regionW = MAX_BUFFER_SIDE * CELL;
        int offset = (regionW - gridW) / 2;
        return metrics.bufferRegionX + offset + col * CELL;
    }

    public static int bufferSlotY(Metrics metrics, int row) {
        int gridH = metrics.bufferSide * CELL;
        int regionH = MAX_BUFFER_SIDE * CELL;
        int offset = (regionH - gridH) / 2;
        return CONTENT_START_Y + offset + row * CELL;
    }

    public static final class Metrics {

        public final int guiWidth;
        public final int guiHeight;
        public final int mainPanelWidth;
        public final int bufferSide;
        public final int inputX;
        public final int bufferRegionX;
        public final int playerInvX;
        public final int playerInvY;
        public final int upgradeColumnX;

        private Metrics(int guiWidth, int guiHeight, int mainPanelWidth, int bufferSide, int inputX, int bufferRegionX,
            int playerInvX, int playerInvY, int upgradeColumnX) {
            this.guiWidth = guiWidth;
            this.guiHeight = guiHeight;
            this.mainPanelWidth = mainPanelWidth;
            this.bufferSide = bufferSide;
            this.inputX = inputX;
            this.bufferRegionX = bufferRegionX;
            this.playerInvX = playerInvX;
            this.playerInvY = playerInvY;
            this.upgradeColumnX = upgradeColumnX;
        }
    }
}
