package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.MatterBallDecompressorGuiLayout;
import com.imgood.textech.gui.container.ContainerMatterBallDecompressor;
import com.imgood.textech.network.packet.PacketMatterBallDecompressorToggle;
import com.imgood.textech.renders.MatterBallDecompressorGuiRenderer;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;

import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.ITooltip;

public class GuiMatterBallDecompressor extends GuiContainer {

    private final TileEntityMatterBallDecompressor tile;
    private final MatterBallDecompressorGuiLayout.Metrics metrics;
    private GuiImgButton outputModeButton;
    private GuiImgButton blockModeButton;

    public GuiMatterBallDecompressor(InventoryPlayer playerInventory, TileEntityMatterBallDecompressor tile) {
        super(new ContainerMatterBallDecompressor(playerInventory, tile));
        this.tile = tile;
        this.metrics = ((ContainerMatterBallDecompressor) inventorySlots).getMetrics();
        this.xSize = metrics.guiWidth;
        this.ySize = metrics.guiHeight;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
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
        // AE IO Port convention: EMPTY = to network, FILL = to storage/buffer.
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
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        MatterBallDecompressorGuiRenderer.drawBackground(left, top, metrics);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.matterBallDecompressor.name");
        int titleX = MatterBallDecompressorGuiLayout.LEFT_GUTTER
            + (metrics.mainPanelWidth - fontRendererObj.getStringWidth(title)) / 2;
        fontRendererObj.drawString(title, titleX, 7, 0x404040);
        fontRendererObj.drawString(
            I18n.format("container.inventory"),
            metrics.playerInvX,
            metrics.playerInvY - 12,
            0x404040);
    }
}
