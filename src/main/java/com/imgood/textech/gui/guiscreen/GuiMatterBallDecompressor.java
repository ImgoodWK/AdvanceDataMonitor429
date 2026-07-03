package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.MatterBallDecompressorGuiLayout;
import com.imgood.textech.gui.container.ContainerMatterBallDecompressor;
import com.imgood.textech.gui.custom.ADM_UiContainer;
import com.imgood.textech.gui.framework.UiButton;
import com.imgood.textech.gui.framework.UiText;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.network.packet.PacketMatterBallDecompressorToggle;
import com.imgood.textech.renders.MatterBallDecompressorGuiRenderer;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;

import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.ITooltip;

public class GuiMatterBallDecompressor extends ADM_UiContainer {

    private final TileEntityMatterBallDecompressor tile;
    private final MatterBallDecompressorGuiLayout.Metrics metrics;
    private GuiImgButton outputModeButton;
    private GuiImgButton blockModeButton;
    /** Decorative 3-slice chip — validates {@link UiButton} rendering (no click action). */
    private UiButton titleAccentChip;

    public GuiMatterBallDecompressor(InventoryPlayer playerInventory, TileEntityMatterBallDecompressor tile) {
        super(new ContainerMatterBallDecompressor(playerInventory, tile), UiThemes.ADM);
        this.tile = tile;
        this.metrics = ((ContainerMatterBallDecompressor) inventorySlots).getMetrics();
        this.xSize = metrics.guiWidth;
        this.ySize = metrics.guiHeight;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        int left = panelLeft();
        int top = panelTop();
        outputModeButton = new GuiImgButton(
            left + MatterBallDecompressorGuiLayout.CACHE_BUTTON_X,
            top + MatterBallDecompressorGuiLayout.CACHE_BUTTON_Y,
            Settings.OPERATION_MODE,
            getOutputOperationMode());
        buttonList.add(outputModeButton);
        blockModeButton = new GuiImgButton(
            left + MatterBallDecompressorGuiLayout.BLOCK_BUTTON_X,
            top + MatterBallDecompressorGuiLayout.BLOCK_BUTTON_Y,
            Settings.BLOCK,
            tile.getConfigManager()
                .getSetting(Settings.BLOCK));
        buttonList.add(blockModeButton);

        int chipX = left + MatterBallDecompressorGuiLayout.LEFT_GUTTER + metrics.mainPanelWidth - 26;
        titleAccentChip = new UiButton(chipX, top + 5, 20, 11).setIconIndex(3)
            .setEnabled(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == blockModeButton) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketMatterBallDecompressorToggle(
                    tile.xCoord,
                    tile.yCoord,
                    tile.zCoord,
                    PacketMatterBallDecompressorToggle.KIND_BLOCK_MODE,
                    !tile.isBlockMode()));
            return;
        }
        if (button == outputModeButton) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketMatterBallDecompressorToggle(
                    tile.xCoord,
                    tile.yCoord,
                    tile.zCoord,
                    PacketMatterBallDecompressorToggle.KIND_OUTPUT_MODE,
                    !tile.isOutputToNetwork()));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (outputModeButton != null) {
            outputModeButton.set(getOutputOperationMode());
        }
        if (blockModeButton != null) {
            blockModeButton.set(tile.getConfigManager()
                .getSetting(Settings.BLOCK));
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (titleAccentChip != null) {
            titleAccentChip.draw(UiThemes.ADM, fontRendererObj, mouseX, mouseY);
        }
        drawSideButtonTooltips(mouseX, mouseY);
    }

    private void drawSideButtonTooltips(int mouseX, int mouseY) {
        if (outputModeButton != null && outputModeButton.getMouseIn()) {
            drawAeStyleTooltip(cacheModeTooltip(), mouseX, mouseY);
        }
        if (blockModeButton != null && blockModeButton.getMouseIn()) {
            drawImgButtonTooltip(blockModeButton, mouseX, mouseY);
        }
    }

    private OperationMode getOutputOperationMode() {
        return tile.isOutputToNetwork() ? OperationMode.EMPTY : OperationMode.FILL;
    }

    private List<String> cacheModeTooltip() {
        if (tile.isOutputToNetwork()) {
            return aeTooltipLines(
                "adm.label.matter_decompressor.mode_network",
                "adm.tooltip.matter_decompressor.mode_network");
        }
        return aeTooltipLines(
            "adm.label.matter_decompressor.mode_buffer",
            "adm.tooltip.matter_decompressor.mode_buffer");
    }

    private static List<String> aeTooltipLines(String titleKey, String bodyKey) {
        return Arrays.asList(
            EnumChatFormatting.WHITE + I18n.format(titleKey),
            EnumChatFormatting.GRAY + I18n.format(bodyKey));
    }

    private void drawAeStyleTooltip(List<String> lines, int mouseX, int mouseY) {
        drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
    }

    private void drawImgButtonTooltip(ITooltip tooltip, int mouseX, int mouseY) {
        String message = tooltip.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        ArrayList<String> lines = new ArrayList<>();
        for (String line : message.split("\n")) {
            if (line != null && !line.isEmpty()) {
                lines.add(line);
            }
        }
        if (!lines.isEmpty()) {
            drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = panelLeft();
        int top = panelTop();
        MatterBallDecompressorGuiRenderer.drawBackground(
            left,
            top,
            metrics,
            tile.isOutputToNetwork(),
            tile.isBlockMode());
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.matterBallDecompressor.name");
        int titleCenterX = MatterBallDecompressorGuiLayout.LEFT_GUTTER + metrics.mainPanelWidth / 2;
        UiText.drawCenteredTitle(UiThemes.ADM, fontRendererObj, title, titleCenterX, 7);
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("container.inventory"),
            metrics.playerInvX,
            metrics.playerInvY - 12);
    }
}
