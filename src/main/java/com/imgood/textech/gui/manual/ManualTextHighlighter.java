package com.imgood.textech.gui.manual;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws a single text line with search-term highlights (literal + pinyin ranges).
 */
@SideOnly(Side.CLIENT)
public final class ManualTextHighlighter {

    private static final int HIGHLIGHT_BG = 0x70FFD020;
    private static final int HIGHLIGHT_TEXT = 0xFFFFE880;

    private ManualTextHighlighter() {}

    public static void drawLine(FontRenderer font, String line, int x, int y, String query, int normalColor) {
        if (ManualSearchUtil.isQueryEmpty(query) || line == null || line.isEmpty()) {
            font.drawString(line == null ? "" : line, x, y, normalColor);
            return;
        }
        boolean[] marks = buildHighlightMask(line, query);
        if (!hasAny(marks)) {
            font.drawString(line, x, y, normalColor);
            return;
        }
        int cursorX = x;
        for (int i = 0; i < line.length();) {
            boolean highlighted = marks[i];
            int end = i + 1;
            while (end < line.length() && marks[end] == highlighted) {
                end++;
            }
            String segment = line.substring(i, end);
            if (highlighted) {
                int width = font.getStringWidth(segment);
                Gui.drawRect(cursorX - 1, y - 1, cursorX + width + 1, y + 9, HIGHLIGHT_BG);
                font.drawString(segment, cursorX, y, HIGHLIGHT_TEXT);
            } else {
                font.drawString(segment, cursorX, y, normalColor);
            }
            cursorX += font.getStringWidth(segment);
            i = end;
        }
    }

    private static boolean[] buildHighlightMask(String line, String query) {
        boolean[] marks = new boolean[line.length()];
        String lowerLine = line.toLowerCase();
        String[] tokens = ManualSearchUtil.splitTokens(query);
        for (String token : tokens) {
            markLiteral(marks, lowerLine, token);
            markPinyin(marks, line, token);
        }
        return marks;
    }

    private static void markLiteral(boolean[] marks, String lowerLine, String token) {
        int from = 0;
        while (from <= lowerLine.length() - token.length()) {
            int index = lowerLine.indexOf(token, from);
            if (index < 0) {
                break;
            }
            for (int i = index; i < index + token.length(); i++) {
                marks[i] = true;
            }
            from = index + 1;
        }
    }

    private static void markPinyin(boolean[] marks, String line, String token) {
        if (!ManualSearchUtil.containsChinese(line) || !ManualSearchUtil.tokenMatches(line, token)) {
            return;
        }
        for (int i = 0; i < line.length();) {
            if (marks[i]) {
                i++;
                continue;
            }
            int bestEnd = -1;
            for (int end = i + 1; end <= line.length(); end++) {
                String sub = line.substring(i, end);
                if (ManualSearchUtil.tokenMatches(sub, token)) {
                    bestEnd = end;
                } else if (bestEnd > i) {
                    break;
                }
            }
            if (bestEnd > i) {
                for (int k = i; k < bestEnd; k++) {
                    marks[k] = true;
                }
                i = bestEnd;
            } else {
                i++;
            }
        }
    }

    private static boolean hasAny(boolean[] marks) {
        for (boolean mark : marks) {
            if (mark) {
                return true;
            }
        }
        return false;
    }
}
