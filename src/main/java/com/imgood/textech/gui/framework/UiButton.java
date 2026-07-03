package com.imgood.textech.gui.framework;

import net.minecraft.client.gui.FontRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Programmatic button with horizontal 3-slice background, optional theme icon and label.
 * Used where {@code GuiButton} list integration is awkward (custom hit-test containers).
 */
@SideOnly(Side.CLIENT)
public final class UiButton {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private String label = "";
    private int iconIndex = -1;
    private boolean enabled = true;
    private Runnable onClick;

    public UiButton(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public UiButton setLabel(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    public UiButton setIconIndex(int iconIndex) {
        this.iconIndex = iconIndex;
        return this;
    }

    public UiButton setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public UiButton setOnClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
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

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hitTest(int mouseX, int mouseY) {
        return enabled
            && mouseX >= x
            && mouseX < x + width
            && mouseY >= y
            && mouseY < y + height;
    }

    public void draw(UiTheme theme, FontRenderer font, int mouseX, int mouseY) {
        boolean hovered = enabled && hitTest(mouseX, mouseY);
        NineSliceRegion region = pickRegion(theme, hovered);
        if (region != null && GuiBlitUtil.hasResource(region.texture())) {
            GuiBlitUtil.drawHorizontalSlice(region, x, y, width, height);
        } else {
            UiPanel.drawSolidFallback(x, y, width, height);
        }

        if (iconIndex >= 0) {
            if (label != null && !label.isEmpty()) {
                UiIcon.drawAnchored(
                    theme,
                    iconIndex,
                    x,
                    y,
                    width / 2,
                    height,
                    UiIcon.Anchor.CENTER,
                    0,
                    0);
            } else {
                UiIcon.drawAnchored(
                    theme,
                    iconIndex,
                    x,
                    y,
                    width,
                    height,
                    UiIcon.Anchor.CENTER,
                    0,
                    0);
            }
        }

        if (label != null && !label.isEmpty()) {
            int labelX = x;
            int labelW = width;
            if (iconIndex >= 0) {
                int iconSize = theme != null ? theme.iconSize() : 16;
                labelX = x + iconSize + 2;
                labelW = width - iconSize - 4;
            }
            UiText.drawOnButton(theme, font, label, labelX, y, labelW, height, enabled, hovered);
        }
    }

    /** Call from {@code mouseClicked} when {@link #hitTest} is true. */
    public boolean click(int mouseX, int mouseY, int button) {
        if (button != 0 || !hitTest(mouseX, mouseY)) {
            return false;
        }
        if (onClick != null) {
            onClick.run();
        }
        return true;
    }

    private NineSliceRegion pickRegion(UiTheme theme, boolean hovered) {
        if (theme == null) {
            return null;
        }
        if (!enabled) {
            return theme.buttonDisabled();
        }
        if (hovered) {
            return theme.buttonHover();
        }
        return theme.buttonNormal();
    }
}
