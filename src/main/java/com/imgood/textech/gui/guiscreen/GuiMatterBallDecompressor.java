package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.container.ContainerMatterBallDecompressor;
import com.imgood.textech.network.packet.PacketMatterBallDecompressorToggle;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;

public class GuiMatterBallDecompressor extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/matter_ball_decompressor.png");

    private static final int BUTTON_OUTPUT_MODE = 0;
    private static final int BUTTON_BLOCK_MODE = 1;

    private final TileEntityMatterBallDecompressor tile;

    public GuiMatterBallDecompressor(InventoryPlayer playerInventory, TileEntityMatterBallDecompressor tile) {
        super(new ContainerMatterBallDecompressor(playerInventory, tile));
        this.tile = tile;
        this.xSize = 300;
        this.ySize = 232;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        buttonList.add(new GuiButton(BUTTON_OUTPUT_MODE, left + 8, top + 6, 46, 20, outputModeLabel()));
        buttonList.add(new GuiButton(BUTTON_BLOCK_MODE, left + 8, top + 30, 46, 20, blockModeLabel()));
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
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager()
            .bindTexture(TEXTURE);
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        drawTexturedModalRect(left, top, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.matterBallDecompressor.name");
        fontRendererObj.drawString(title, (xSize - fontRendererObj.getStringWidth(title)) / 2, 6, 0x404040);
        fontRendererObj.drawString(I18n.format("container.inventory"), 26, ySize - 94, 0x404040);
    }
}
