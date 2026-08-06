package com.imgood.textech.gui.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Fixed text band for wrapped hints and validation failures. */
@SideOnly(Side.CLIENT)
public final class UiFeedbackArea {

    public static final int DEFAULT_LINE_HEIGHT = 10;

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public UiFeedbackArea(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Feedback area must have positive dimensions");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int maxLines() {
        return maxLines(height, DEFAULT_LINE_HEIGHT);
    }

    public static int maxLines(int height, int lineHeight) {
        return Math.max(0, height / Math.max(1, lineHeight));
    }

    public static <T> List<T> firstLines(List<T> lines, int maxLines) {
        if (lines == null || lines.isEmpty() || maxLines <= 0) {
            return Collections.emptyList();
        }
        return new ArrayList<T>(lines.subList(0, Math.min(lines.size(), maxLines)));
    }

    /**
     * Reserves a feedback band below the lowest control and inside the panel bottom padding. Returns {@code null}
     * when the panel does not contain even one complete feedback line.
     */
    public static UiFeedbackArea afterControls(int x, int panelY, int width, int panelHeight, int controlsBottom,
        int controlGap, int bottomPadding, int maximumHeight) {
        int panelBottom = panelY + panelHeight - Math.max(0, bottomPadding);
        int y = Math.max(controlsBottom + Math.max(0, controlGap), panelBottom - Math.max(0, maximumHeight));
        int height = panelBottom - y;
        return width > 0 && height >= DEFAULT_LINE_HEIGHT ? new UiFeedbackArea(x, y, width, height) : null;
    }

    public int draw(FontRenderer font, String message, int color) {
        if (font == null || message == null || message.isEmpty()) {
            return 0;
        }
        List<String> wrapped = firstLines(font.listFormattedStringToWidth(message, width), maxLines());
        for (int i = 0; i < wrapped.size(); i++) {
            font.drawStringWithShadow(wrapped.get(i), x, y + i * DEFAULT_LINE_HEIGHT, color);
        }
        return wrapped.size();
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
