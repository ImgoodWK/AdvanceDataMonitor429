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
 * GUI rendering for the matter-ball decompressor.
 * Side buttons use AE {@code GuiImgButton} ({@code guis/states.png}); upgrade slots use the AE
 * upgradeable-machine column from {@code guis/storagebus.png}.
 */
@SideOnly(Side.CLIENT)
public final class MatterBallDecompressorGuiRenderer {

    private static final String AE_MODID = "appliedenergistics2";

    /** AE upgradeable-machine GUI — upgrade column strip at u=177. */
    public static final ResourceLocation AE_UPGRADEABLE_TEXTURE = new ResourceLocation(
        AE_MODID,
        "textures/guis/storagebus.png");

    public static final ResourceLocation SLOT_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/matter_ball_decompressor_slot.png");

    public static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/matter_ball_decompressor_bg.png");

    /** Main panel — replace via {@link #BACKGROUND_TEXTURE} when hand-painted. */
    private static final int PANEL_BG = 0xFFC8C8C8;
    /** Player-inventory separator line. */
    private static final int SECTION_LINE = 0xFF909090;

    private static final int SLOT_FILL = 0xFF6E6E6E;
    private static final int SLOT_HIGHLIGHT = 0xFF9A9A9A;
    private static final int SLOT_SHADOW = 0xFF3A3A3A;

    /** AE upgrade column: slot x=187, panel x=177. */
    private static final int AE_UPGRADE_PANEL_U = 177;
    private static final int AE_UPGRADE_PANEL_V = 0;
    private static final int AE_UPGRADE_PANEL_W = 35;
    private static final int AE_UPGRADE_PANEL_HEADER = 14;
    /** AE first upgrade slot is at (187, 8) relative to GUI origin. */
    private static final int AE_UPGRADE_SLOT_X = 187;
    private static final int AE_UPGRADE_SLOT_Y = 8;

    private MatterBallDecompressorGuiRenderer() {}

    public static void drawBackground(int guiLeft, int guiTop, MatterBallDecompressorGuiLayout.Metrics metrics) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        int panelLeft = guiLeft + MatterBallDecompressorGuiLayout.LEFT_GUTTER;
        if (hasResource(BACKGROUND_TEXTURE)) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.getTextureManager()
                .bindTexture(BACKGROUND_TEXTURE);
            mc.ingameGUI.drawTexturedModalRect(
                panelLeft,
                guiTop,
                0,
                0,
                metrics.mainPanelWidth,
                metrics.guiHeight);
        } else {
            drawSolidPanel(panelLeft, guiTop, metrics);
        }

        drawAllSlotBackgrounds(guiLeft, guiTop, metrics);
        drawAeUpgradeColumn(guiLeft, guiTop, metrics);
        drawPlayerInventorySlots(guiLeft, guiTop, metrics);
    }

    /** AE upgradeable-machine right column (matches {@code GuiUpgradeable.drawBG}). */
    public static void drawAeUpgradeColumn(int guiLeft, int guiTop, MatterBallDecompressorGuiLayout.Metrics metrics) {
        int upgradeCount = MatterBallDecompressorGuiLayout.UPGRADE_COUNT;
        int panelHeight = AE_UPGRADE_PANEL_HEADER + upgradeCount * MatterBallDecompressorGuiLayout.CELL;
        int panelX = guiLeft + metrics.upgradeColumnX - (AE_UPGRADE_SLOT_X - AE_UPGRADE_PANEL_U);
        int panelY = guiTop + MatterBallDecompressorGuiLayout.CONTENT_START_Y
            - (AE_UPGRADE_SLOT_Y - AE_UPGRADE_PANEL_V);

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(AE_UPGRADEABLE_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.ingameGUI.drawTexturedModalRect(
            panelX,
            panelY,
            AE_UPGRADE_PANEL_U,
            AE_UPGRADE_PANEL_V,
            AE_UPGRADE_PANEL_W,
            panelHeight);
    }

    private static void drawSolidPanel(int panelLeft, int guiTop, MatterBallDecompressorGuiLayout.Metrics metrics) {
        Gui.drawRect(
            panelLeft,
            guiTop,
            panelLeft + metrics.mainPanelWidth,
            guiTop + metrics.guiHeight,
            PANEL_BG);

        int splitY = guiTop + metrics.playerInvY - 6;
        Gui.drawRect(
            panelLeft + 8,
            splitY,
            panelLeft + metrics.mainPanelWidth - 8,
            splitY + 1,
            SECTION_LINE);
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
                guiLeft + metrics.inputX,
                guiTop + MatterBallDecompressorGuiLayout.CONTENT_START_Y + row * MatterBallDecompressorGuiLayout.CELL);
        }

        int offset = (MatterBallDecompressorGuiLayout.MAX_BUFFER_SIDE - metrics.bufferSide) / 2;
        for (int row = 0; row < metrics.bufferSide; row++) {
            for (int col = 0; col < metrics.bufferSide; col++) {
                drawSlotCell(
                    guiLeft + metrics.bufferRegionX + (offset + col) * MatterBallDecompressorGuiLayout.CELL,
                    guiTop + MatterBallDecompressorGuiLayout.CONTENT_START_Y
                        + (offset + row) * MatterBallDecompressorGuiLayout.CELL);
            }
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
