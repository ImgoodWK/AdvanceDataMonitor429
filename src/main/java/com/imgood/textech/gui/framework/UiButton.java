package com.imgood.textech.gui.framework;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.input.Mouse;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Programmatic button with a complete fixed-aspect shell, optional theme icon and label.
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
        this.width = FixedAspectButtonFamily.normalizedWidthFor(width, height);
        this.height = FixedAspectButtonFamily.normalizedHeightFor(width, height);
        this.x = x + (width - this.width) / 2;
        this.y = y + (height - this.height) / 2;
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
        return enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void draw(UiTheme theme, FontRenderer font, int mouseX, int mouseY) {
        boolean hovered = enabled && hitTest(mouseX, mouseY);
        boolean pressed = hovered && Mouse.isButtonDown(0);
        FixedAspectButtonFamily family = theme != null ? theme.fixedAspectButtons() : null;
        TiledBarRegion bar = pickBar(theme, hovered, pressed);
        NineSliceRegion region = pickRegion(theme, hovered, pressed);
        if (family != null && GuiBlitUtil.hasResource(
            family.region(pickState(hovered, pressed), width, height)
                .texture())) {
            GuiBlitUtil.drawFixedAspectButton(family, pickState(hovered, pressed), x, y, width, height);
        } else if (bar != null && GuiBlitUtil.hasResource(
            bar.center()
                .texture())) {
                    GuiBlitUtil.drawTiledBar(bar, x, y, width, height);
                } else
            if (region != null && GuiBlitUtil.hasResource(region.texture())) {
                GuiBlitUtil.drawHorizontalSlice(region, x, y, width, height);
            } else {
                UiPanel.drawSolidFallback(x, y, width, height);
            }

        if (iconIndex >= 0) {
            if (label != null && !label.isEmpty()) {
                UiIcon.drawAnchored(theme, iconIndex, x, y, width / 2, height, UiIcon.Anchor.CENTER, 0, 0, hovered);
            } else {
                UiIcon.drawAnchored(theme, iconIndex, x, y, width, height, UiIcon.Anchor.CENTER, 0, 0, hovered);
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

    private NineSliceRegion pickRegion(UiTheme theme, boolean hovered, boolean pressed) {
        if (theme == null) {
            return null;
        }
        if (!enabled) {
            return theme.buttonDisabled();
        }
        if (pressed) {
            return theme.buttonPressed();
        }
        if (hovered) {
            return theme.buttonHover();
        }
        return theme.buttonNormal();
    }

    private FixedAspectButtonFamily.State pickState(boolean hovered, boolean pressed) {
        if (!enabled) {
            return FixedAspectButtonFamily.State.DISABLED;
        }
        if (pressed) {
            return FixedAspectButtonFamily.State.PRESSED;
        }
        return hovered ? FixedAspectButtonFamily.State.HOVER : FixedAspectButtonFamily.State.NORMAL;
    }

    private TiledBarRegion pickBar(UiTheme theme, boolean hovered, boolean pressed) {
        if (theme == null) {
            return null;
        }
        if (!enabled) {
            return theme.buttonDisabledBar();
        }
        if (pressed) {
            return theme.buttonPressedBar();
        }
        if (hovered) {
            return theme.buttonHoverBar();
        }
        return theme.buttonNormalBar();
    }
}
