package com.imgood.textech.gui.framework;

import net.minecraft.client.gui.FontRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Two-state toggle button (icon and/or label swap).
 * <p>
 * Implemented for the UI framework; not wired into production GUIs in the initial debug pass.
 */
@SideOnly(Side.CLIENT)
public final class UiToggleButton {

    private final UiButton button;
    private boolean state;
    private int iconOn = -1;
    private int iconOff = -1;
    private String labelOn = "";
    private String labelOff = "";
    private Runnable onToggle;

    public UiToggleButton(int x, int y, int width, int height) {
        this.button = new UiButton(x, y, width, height);
        applyPresentation();
    }

    public UiToggleButton setIcons(int iconOff, int iconOn) {
        this.iconOff = iconOff;
        this.iconOn = iconOn;
        applyPresentation();
        return this;
    }

    public UiToggleButton setLabels(String labelOff, String labelOn) {
        this.labelOff = labelOff != null ? labelOff : "";
        this.labelOn = labelOn != null ? labelOn : "";
        applyPresentation();
        return this;
    }

    public UiToggleButton setState(boolean state) {
        this.state = state;
        applyPresentation();
        return this;
    }

    public boolean state() {
        return state;
    }

    public UiToggleButton setOnToggle(Runnable onToggle) {
        this.onToggle = onToggle;
        button.setOnClick(new Runnable() {

            @Override
            public void run() {
                state = !state;
                applyPresentation();
                if (onToggle != null) {
                    onToggle.run();
                }
            }
        });
        return this;
    }

    public void draw(UiTheme theme, FontRenderer font, int mouseX, int mouseY) {
        button.draw(theme, font, mouseX, mouseY);
    }

    public boolean click(int mouseX, int mouseY, int buttonId) {
        return button.click(mouseX, mouseY, buttonId);
    }

    public boolean hitTest(int mouseX, int mouseY) {
        return button.hitTest(mouseX, mouseY);
    }

    private void applyPresentation() {
        button.setIconIndex(state ? iconOn : iconOff);
        button.setLabel(state ? labelOn : labelOff);
    }
}
