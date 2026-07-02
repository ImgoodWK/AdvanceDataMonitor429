package com.imgood.textech.gui;

/**
 * Layout for the matter-ball decompressor GUI.
 * <p>
 * Screenshot / custom-art anchors (relative to GUI top-left):
 * <ul>
 * <li>{@link #REF_POINT_A_X}/{@link #REF_POINT_A_Y} — panel origin</li>
 * <li>{@link #REF_POINT_B_X}/{@link #REF_POINT_B_Y} — bottom-right of the fixed 9×9 buffer region</li>
 * </ul>
 * Default rendering is procedural solid fills ({@link com.imgood.textech.renders.MatterBallDecompressorGuiRenderer}).
 * Optional hand-painted overrides:
 * {@code matter_ball_decompressor_bg.png} (panel) + {@code matter_ball_decompressor_slot.png} (18×18 cell).
 */
public final class MatterBallDecompressorGuiLayout {

    public static final int CELL = 18;

    /** Point A — align custom background top-left here. */
    public static final int REF_POINT_A_X = 0;
    public static final int REF_POINT_A_Y = 0;

    /** Point B — align 9×9 buffer region bottom-right here when drawing at max capacity. */
    public static final int REF_POINT_B_X = 62 + 9 * CELL;
    public static final int REF_POINT_B_Y = 24 + 9 * CELL;

    public static final int TOP_ROW_Y = 6;
    public static final int CONTENT_START_Y = 24;

    public static final int BUTTON_OUTPUT_X = 8;
    public static final int BUTTON_BLOCK_X = 58;
    public static final int BUTTON_WIDTH = 46;
    public static final int BUTTON_HEIGHT = 16;

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
        int bufferGrid = side * CELL;
        int maxRegion = MAX_BUFFER_SIDE * CELL;
        int contentHeight = Math.max(INPUT_ROWS * CELL, maxRegion);
        int playerInvY = CONTENT_START_Y + contentHeight + PLAYER_INV_GAP;
        int guiHeight = playerInvY + 58 + 18 + 4;
        int guiWidth = Math.max(176, BUFFER_REGION_X + maxRegion + 8);
        int upgradeStartX = guiWidth - 8 - UPGRADE_COUNT * CELL;
        int playerInvX = Math.max(8, (guiWidth - 9 * CELL) / 2);
        return new Metrics(guiWidth, guiHeight, side, playerInvX, playerInvY, upgradeStartX);
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
        return BUFFER_REGION_X + offset + col * CELL;
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
        public final int bufferSide;
        public final int playerInvX;
        public final int playerInvY;
        public final int upgradeStartX;

        private Metrics(int guiWidth, int guiHeight, int bufferSide, int playerInvX, int playerInvY,
            int upgradeStartX) {
            this.guiWidth = guiWidth;
            this.guiHeight = guiHeight;
            this.bufferSide = bufferSide;
            this.playerInvX = playerInvX;
            this.playerInvY = playerInvY;
            this.upgradeStartX = upgradeStartX;
        }
    }
}
