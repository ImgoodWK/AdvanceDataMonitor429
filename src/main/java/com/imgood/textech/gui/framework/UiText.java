package com.imgood.textech.gui.framework;

import net.minecraft.client.gui.FontRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Text labels for container foreground layers.
 */
@SideOnly(Side.CLIENT)
public final class UiText {

    private UiText() {}

    public static void drawLabel(UiTheme theme, FontRenderer font, String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int color = theme != null ? theme.textPrimary() : 0x404040;
        font.drawString(text, x, y, color);
    }

    public static void drawLabelShadow(UiTheme theme, FontRenderer font, String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int color = theme != null ? theme.textPrimary() : 0x404040;
        font.drawStringWithShadow(text, x, y, color);
    }

    public static void drawCenteredTitle(UiTheme theme, FontRenderer font, String text, int centerX, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int color = theme != null ? theme.textPrimary() : 0x404040;
        font.drawString(text, centerX - font.getStringWidth(text) / 2, y, color);
    }

    public static void drawAccent(UiTheme theme, FontRenderer font, String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int color = theme != null ? theme.textAccent() : 0x00FFFF;
        font.drawStringWithShadow(text, x, y, color);
    }

    public static void drawOnButton(UiTheme theme, FontRenderer font, String text, int btnX, int btnY, int btnW,
        int btnH, boolean enabled, boolean hovered) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int color;
        if (!enabled) {
            color = theme != null ? theme.textDisabled() : 0xA0A0A0;
        } else if (hovered) {
            color = theme != null ? theme.textAccent() : 0x00FFFF;
        } else {
            color = theme != null ? theme.textPrimary() : 0xFFFFFF;
        }
        int textY = btnY + (btnH - 8) / 2;
        font.drawString(text, btnX + (btnW - font.getStringWidth(text)) / 2, textY, color);
    }
}
