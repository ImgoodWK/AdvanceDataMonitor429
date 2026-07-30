package com.imgood.textech.gui;

/** Pure layout helper for fitting legacy fixed-size panels into a scaled Minecraft viewport. */
public final class GuiResponsiveLayout {

    private GuiResponsiveLayout() {}

    public static Panel fitCentered(int screenWidth, int screenHeight, int preferredWidth, int preferredHeight,
        int margin) {
        int safeMargin = Math.max(0, margin);
        int availableWidth = Math.max(1, screenWidth - safeMargin * 2);
        int availableHeight = Math.max(1, screenHeight - safeMargin * 2);
        int width = Math.min(Math.max(1, preferredWidth), availableWidth);
        int height = Math.min(Math.max(1, preferredHeight), availableHeight);
        return new Panel((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    public static final class Panel {

        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private Panel(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }
}
