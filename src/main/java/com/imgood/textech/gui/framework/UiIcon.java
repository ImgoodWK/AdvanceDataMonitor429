package com.imgood.textech.gui.framework;

import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Icon blit from theme atlas grid or a standalone texture.
 */
@SideOnly(Side.CLIENT)
public final class UiIcon {

    public enum Anchor {
        TOP_LEFT,
        CENTER
    }

    private UiIcon() {}

    /** Theme icon grid: {@code index} counts left-to-right, top-to-bottom in {@link UiTheme#iconSize()} cells. */
    public static void drawThemeIcon(UiTheme theme, int index, int x, int y) {
        int size = theme != null ? theme.iconSize() : 16;
        drawThemeIcon(theme, index, x, y, size);
    }

    public static void drawThemeIcon(UiTheme theme, int index, int x, int y, int destSize) {
        if (theme == null || theme.iconAtlas() == null || destSize <= 0) {
            return;
        }
        int size = theme.iconSize();
        int cols = 8;
        int col = index % cols;
        int row = index / cols;
        int u = theme.iconGridU() + col * size;
        int v = theme.iconGridV() + row * size;
        GuiBlitUtil.blit(theme.iconAtlas(), theme.iconAtlasSize(), x, y, destSize, destSize, u, v, size, size);
    }

    public static void drawTexture(ResourceLocation texture, int atlasSize, int u, int v, int size, int x, int y) {
        if (texture == null || size <= 0) {
            return;
        }
        GuiBlitUtil.blit(texture, atlasSize, x, y, size, size, u, v, size, size);
    }

    /** Draw icon anchored relative to a parent rectangle (e.g. button or slot). */
    public static void drawAnchored(
        UiTheme theme,
        int index,
        int parentX,
        int parentY,
        int parentW,
        int parentH,
        Anchor anchor,
        int offsetX,
        int offsetY) {
        int size = theme != null ? theme.iconSize() : 16;
        int drawX;
        int drawY;
        if (anchor == Anchor.CENTER) {
            drawX = parentX + (parentW - size) / 2 + offsetX;
            drawY = parentY + (parentH - size) / 2 + offsetY;
        } else {
            drawX = parentX + offsetX;
            drawY = parentY + offsetY;
        }
        drawThemeIcon(theme, index, drawX, drawY);
    }
}
