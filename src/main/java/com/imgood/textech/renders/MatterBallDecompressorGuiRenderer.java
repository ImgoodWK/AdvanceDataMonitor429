package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.MatterBallDecompressorGuiLayout;
import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiThemes;

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

    /** Main panel — legacy optional full PNG override (superseded by {@link UiPanel} 9-slice). */
    public static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/matter_ball_decompressor_bg.png");

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
        UiPanel.draw(
            UiThemes.ADM,
            panelLeft,
            guiTop,
            metrics.mainPanelWidth,
            metrics.guiHeight);

        int splitY = guiTop + metrics.playerInvY - 6;
        UiPanel.drawDivider(panelLeft + 8, splitY, metrics.mainPanelWidth - 16);

        drawAllSlotBackgrounds(guiLeft, guiTop, metrics);
        drawAeUpgradeColumn(guiLeft, guiTop, metrics);
        drawPlayerInventorySlots(guiLeft, guiTop, metrics);
    }

    public static void drawBackground(
        int guiLeft,
        int guiTop,
        MatterBallDecompressorGuiLayout.Metrics metrics,
        boolean outputToNetwork,
        boolean blockMode) {
        drawBackground(guiLeft, guiTop, metrics);
        drawSideStatusIcons(guiLeft, guiTop, outputToNetwork, blockMode);
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

    /** Side gutter status icons drawn beside AE toggle buttons (UiIcon debug). */
    public static void drawSideStatusIcons(int guiLeft, int guiTop, boolean outputToNetwork, boolean blockMode) {
        int iconSize = 8;
        int cacheIconX = guiLeft + MatterBallDecompressorGuiLayout.CACHE_BUTTON_X
            + MatterBallDecompressorGuiLayout.SIDE_BUTTON
            + 2;
        int cacheIconY = guiTop + MatterBallDecompressorGuiLayout.CACHE_BUTTON_Y
            + (MatterBallDecompressorGuiLayout.SIDE_BUTTON - iconSize) / 2;
        drawStatusIcon(cacheIconX, cacheIconY, iconSize, outputToNetwork ? 0 : 1);

        int blockIconX = guiLeft + MatterBallDecompressorGuiLayout.BLOCK_BUTTON_X
            + MatterBallDecompressorGuiLayout.SIDE_BUTTON
            + 2;
        int blockIconY = guiTop + MatterBallDecompressorGuiLayout.BLOCK_BUTTON_Y
            + (MatterBallDecompressorGuiLayout.SIDE_BUTTON - iconSize) / 2;
        drawStatusIcon(blockIconX, blockIconY, iconSize, blockMode ? 2 : 3);
    }

    private static void drawStatusIcon(int x, int y, int size, int themeIconIndex) {
        com.imgood.textech.gui.framework.UiIcon.drawThemeIcon(UiThemes.ADM, themeIconIndex, x, y, size);
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

