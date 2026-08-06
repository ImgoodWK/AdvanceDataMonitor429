package com.imgood.textech.gui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Inventory slot cell drawing — vanilla texture, custom texture, or procedural fallback.
 * <p>
 * Implemented for the UI framework; not wired into production GUIs in the initial debug pass.
 */
@SideOnly(Side.CLIENT)
public final class UiSlot {

    public static final int DEFAULT_CELL = 18;

    private static final ResourceLocation VANILLA_INVENTORY = new ResourceLocation(
        "minecraft",
        "textures/gui/container/inventory.png");
    private static final int VANILLA_CELL_U = 7;
    private static final int VANILLA_CELL_V = 83;

    private static final int SLOT_FILL = 0xFF6E6E6E;
    private static final int SLOT_HIGHLIGHT = 0xFF9A9A9A;
    private static final int SLOT_SHADOW = 0xFF3A3A3A;

    private UiSlot() {}

    public static void drawVanilla(int x, int y) {
        drawVanilla(x, y, DEFAULT_CELL);
    }

    public static void drawVanilla(int x, int y, int size) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(VANILLA_INVENTORY);
        mc.ingameGUI.drawTexturedModalRect(x, y, VANILLA_CELL_U, VANILLA_CELL_V, size, size);
    }

    public static void drawTexture(ResourceLocation texture, int x, int y, int size) {
        if (texture == null || !GuiBlitUtil.hasResource(texture)) {
            drawProcedural(x, y, size);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(texture);
        mc.ingameGUI.drawTexturedModalRect(x, y, 0, 0, size, size);
    }

    public static void drawTheme(UiTheme theme, int x, int y) {
        drawTheme(theme, x, y, DEFAULT_CELL);
    }

    public static void drawTheme(UiTheme theme, int x, int y, int size) {
        AtlasRegion exact = theme != null ? theme.slotRegion() : null;
        if (exact != null && GuiBlitUtil.hasResource(exact.texture())
            && size == exact.width()
            && size == exact.height()) {
            GuiBlitUtil.drawRegion(exact, x, y);
            return;
        }
        NineSliceRegion region = theme != null ? theme.slot() : null;
        if (region != null && GuiBlitUtil.hasResource(region.texture())) {
            GuiBlitUtil.blit(
                region.texture(),
                region.atlasSize(),
                x,
                y,
                size,
                size,
                region.u(),
                region.v(),
                region.regionW(),
                region.regionH());
            return;
        }
        drawProcedural(x, y, size);
    }

    public static void drawProcedural(int x, int y) {
        drawProcedural(x, y, DEFAULT_CELL);
    }

    public static void drawProcedural(int x, int y, int size) {
        Gui.drawRect(x, y, x + size, y + size, SLOT_FILL);
        Gui.drawRect(x, y, x + size, y + 1, SLOT_HIGHLIGHT);
        Gui.drawRect(x, y, x + 1, y + size, SLOT_HIGHLIGHT);
        Gui.drawRect(x + size - 1, y, x + size, y + size, SLOT_SHADOW);
        Gui.drawRect(x, y + size - 1, x + size, y + size, SLOT_SHADOW);
    }
}
