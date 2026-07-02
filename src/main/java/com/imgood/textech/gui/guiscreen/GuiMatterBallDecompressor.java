package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.MatterBallDecompressorGuiLayout;
import com.imgood.textech.gui.container.ContainerMatterBallDecompressor;
import com.imgood.textech.network.packet.PacketMatterBallDecompressorToggle;
import com.imgood.textech.renders.MatterBallDecompressorGuiRenderer;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;

public class GuiMatterBallDecompressor extends GuiContainer {

    private static final int BUTTON_OUTPUT_MODE = 0;
    private static final int BUTTON_BLOCK_MODE = 1;

    private final TileEntityMatterBallDecompressor tile;
    private final MatterBallDecompressorGuiLayout.Metrics metrics;

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
        buttonList.add(modeButton(
            BUTTON_OUTPUT_MODE,
            left + MatterBallDecompressorGuiLayout.BUTTON_OUTPUT_X,
            top + MatterBallDecompressorGuiLayout.TOP_ROW_Y,
            outputModeLabel()));
        buttonList.add(modeButton(
            BUTTON_BLOCK_MODE,
            left + MatterBallDecompressorGuiLayout.BUTTON_BLOCK_X,
            top + MatterBallDecompressorGuiLayout.TOP_ROW_Y,
            blockModeLabel()));
    }

    private GuiButton modeButton(int id, int x, int y, String label) {
        return new GuiButton(id, x, y, MatterBallDecompressorGuiLayout.BUTTON_WIDTH,
            MatterBallDecompressorGuiLayout.BUTTON_HEIGHT, label) {

            @Override
            public void drawButton(Minecraft mc, int mouseX, int mouseY) {
                if (!visible) {
                    return;
                }
                boolean hover = mouseX >= xPosition && mouseY >= yPosition
                    && mouseX < xPosition + width
                    && mouseY < yPosition + height;
                int color = enabled ? (hover ? 0x2060A0 : 0x404040) : 0xA0A0A0;
                String text = displayString;
                mc.fontRenderer.drawString(
                    text,
                    xPosition + (width - mc.fontRenderer.getStringWidth(text)) / 2,
                    yPosition + (height - 8) / 2,
                    color);
            }
        };
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_OUTPUT_MODE) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketMatterBallDecompressorToggle(
                    tile.xCoord,
                    tile.yCoord,
                    tile.zCoord,
                    PacketMatterBallDecompressorToggle.KIND_OUTPUT_MODE,
                    !tile.isOutputToNetwork()));
        } else if (button.id == BUTTON_BLOCK_MODE) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketMatterBallDecompressorToggle(
                    tile.xCoord,
                    tile.yCoord,
                    tile.zCoord,
                    PacketMatterBallDecompressorToggle.KIND_BLOCK_MODE,
                    !tile.isBlockMode()));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (Object obj : buttonList) {
            GuiButton button = (GuiButton) obj;
            if (button.id == BUTTON_OUTPUT_MODE) {
                button.displayString = outputModeLabel();
            } else if (button.id == BUTTON_BLOCK_MODE) {
                button.displayString = blockModeLabel();
            }
            if (button.mousePressed(mc, mouseX, mouseY)) {
                drawHoveringText(outputModeTooltip(button.id), mouseX, mouseY, fontRendererObj);
            }
        }
    }

    private String outputModeLabel() {
        if (tile.isOutputToNetwork()) {
            return I18n.format("adm.button.matter_decompressor.mode_network_short");
        }
        return I18n.format("adm.button.matter_decompressor.mode_buffer_short");
    }

    private String blockModeLabel() {
        if (tile.isBlockMode()) {
            return I18n.format("adm.button.matter_decompressor.block_on_short");
        }
        return I18n.format("adm.button.matter_decompressor.block_off_short");
    }

    private java.util.List<String> outputModeTooltip(int buttonId) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        if (buttonId == BUTTON_OUTPUT_MODE) {
            if (tile.isOutputToNetwork()) {
                lines.add(I18n.format("adm.tooltip.matter_decompressor.mode_network"));
            } else {
                lines.add(I18n.format("adm.tooltip.matter_decompressor.mode_buffer"));
            }
        } else if (buttonId == BUTTON_BLOCK_MODE) {
            if (tile.isBlockMode()) {
                lines.add(I18n.format("adm.tooltip.matter_decompressor.block_on"));
            } else {
                lines.add(I18n.format("adm.tooltip.matter_decompressor.block_off"));
            }
        }
        return lines;
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
        fontRendererObj.drawString(title, (xSize - fontRendererObj.getStringWidth(title)) / 2, 7, 0x404040);
        fontRendererObj.drawString(
            I18n.format("container.inventory"),
            metrics.playerInvX,
            metrics.playerInvY - 12,
            0x404040);
    }
}
