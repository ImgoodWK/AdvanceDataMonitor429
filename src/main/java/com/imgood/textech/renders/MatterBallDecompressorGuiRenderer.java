package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.MatterBallDecompressorGuiLayout;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Procedural (solid-color) GUI for the matter-ball decompressor.
 * <p>
 * Default: flat fills + beveled slot cells — no AE / vanilla atlas. Drop custom PNGs to override:
 * {@link #BACKGROUND_TEXTURE} (full panel) or {@link #SLOT_TEXTURE} (18×18 cell).
 */
@SideOnly(Side.CLIENT)
public final class MatterBallDecompressorGuiRenderer {

    public static final ResourceLocation SLOT_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/matter_ball_decompressor_slot.png");

    public static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/matter_ball_decompressor_bg.png");

    /** Main panel — replace via {@link #BACKGROUND_TEXTURE} when hand-painted. */
    private static final int PANEL_BG = 0xFFC8C8C8;
    /** Top toolbar strip (buttons + upgrades). */
    private static final int TOOLBAR_BG = 0xFFB0B0B0;
    private static final int TOOLBAR_BORDER = 0xFF909090;
    /** Player-inventory separator line. */
    private static final int SECTION_LINE = 0xFF909090;

    private static final int SLOT_FILL = 0xFF6E6E6E;
    private static final int SLOT_HIGHLIGHT = 0xFF9A9A9A;
    private static final int SLOT_SHADOW = 0xFF3A3A3A;

    private MatterBallDecompressorGuiRenderer() {}

    public static void drawBackground(int guiLeft, int guiTop, MatterBallDecompressorGuiLayout.Metrics metrics) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (hasResource(BACKGROUND_TEXTURE)) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.getTextureManager()
                .bindTexture(BACKGROUND_TEXTURE);
            mc.ingameGUI.drawTexturedModalRect(guiLeft, guiTop, 0, 0, metrics.guiWidth, metrics.guiHeight);
        } else {
            drawSolidPanel(guiLeft, guiTop, metrics);
        }

        drawAllSlotBackgrounds(guiLeft, guiTop, metrics);
        drawPlayerInventorySlots(guiLeft, guiTop, metrics);
    }

    private static void drawSolidPanel(int guiLeft, int guiTop, MatterBallDecompressorGuiLayout.Metrics metrics) {
        Gui.drawRect(guiLeft, guiTop, guiLeft + metrics.guiWidth, guiTop + metrics.guiHeight, PANEL_BG);

        int barY1 = guiTop + 4;
        int barY2 = barY1 + 20;
        Gui.drawRect(guiLeft + 6, barY1, guiLeft + metrics.guiWidth - 6, barY2, TOOLBAR_BG);
        Gui.drawRect(guiLeft + 6, barY1, guiLeft + metrics.guiWidth - 6, barY1 + 1, TOOLBAR_BORDER);
        Gui.drawRect(guiLeft + 6, barY2 - 1, guiLeft + metrics.guiWidth - 6, barY2, TOOLBAR_BORDER);

        int splitY = guiTop + metrics.playerInvY - 6;
        Gui.drawRect(guiLeft + 8, splitY, guiLeft + metrics.guiWidth - 8, splitY + 1, SECTION_LINE);
    }

    public static void drawSlotCell(int x, int y) {
        if (hasResource(SLOT_TEXTURE)) {
            Minecraft mc = Minecraft.getMinecraft();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager()
                .bindTexture(SLOT_TEXTURE);
            mc.ingameGUI.drawTexturedModalRect(x, y, 0, 0, MatterBallDecompressorGuiLayout.CELL,
                MatterBallDecompressorGuiLayout.CELL);
            return;
        }
        drawProceduralSlotCell(x, y);
    }

    private static void drawProceduralSlotCell(int x, int y) {
        int s = MatterBallDecompressorGuiLayout.CELL;
        Gui.drawRect(x, y, x + s, y + s, SLOT_FILL);
        Gui.drawRect(x, y, x + s, y + 1, SLOT_HIGHLIGHT);
        Gui.drawRect(x, y, x + 1, y + s, SLOT_HIGHLIGHT);
        Gui.drawRect(x + s - 1, y, x + s, y + s, SLOT_SHADOW);
        Gui.drawRect(x, y + s - 1, x + s, y + s, SLOT_SHADOW);
    }

    private static void drawAllSlotBackgrounds(int guiLeft, int guiTop, MatterBallDecompressorGuiLayout.Metrics metrics) {
        for (int row = 0; row < MatterBallDecompressorGuiLayout.INPUT_ROWS; row++) {
            drawSlotCell(
                guiLeft + MatterBallDecompressorGuiLayout.INPUT_X,
                guiTop + MatterBallDecompressorGuiLayout.CONTENT_START_Y + row * MatterBallDecompressorGuiLayout.CELL);
        }

        int offset = (MatterBallDecompressorGuiLayout.MAX_BUFFER_SIDE - metrics.bufferSide) / 2;
        for (int row = 0; row < metrics.bufferSide; row++) {
            for (int col = 0; col < metrics.bufferSide; col++) {
                drawSlotCell(
                    guiLeft + MatterBallDecompressorGuiLayout.BUFFER_REGION_X
                        + (offset + col) * MatterBallDecompressorGuiLayout.CELL,
                    guiTop + MatterBallDecompressorGuiLayout.CONTENT_START_Y
                        + (offset + row) * MatterBallDecompressorGuiLayout.CELL);
            }
        }

        for (int i = 0; i < MatterBallDecompressorGuiLayout.UPGRADE_COUNT; i++) {
            drawSlotCell(
                guiLeft + metrics.upgradeStartX + i * MatterBallDecompressorGuiLayout.CELL,
                guiTop + MatterBallDecompressorGuiLayout.TOP_ROW_Y);
        }
    }

    private static void drawPlayerInventorySlots(int guiLeft, int guiTop, MatterBallDecompressorGuiLayout.Metrics metrics) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotCell(
                    guiLeft + metrics.playerInvX + col * MatterBallDecompressorGuiLayout.CELL,
                    guiTop + metrics.playerInvY + row * MatterBallDecompressorGuiLayout.CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotCell(
                guiLeft + metrics.playerInvX + col * MatterBallDecompressorGuiLayout.CELL,
                guiTop + metrics.playerInvY + 58);
        }
    }

    private static boolean hasResource(ResourceLocation location) {
        try {
            return Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location) != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
