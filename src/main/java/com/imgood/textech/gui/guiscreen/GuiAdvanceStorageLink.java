package com.imgood.textech.gui.guiscreen;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.container.ContainerAdvanceStorageLink;
import com.imgood.textech.network.packet.PacketRequestItemCountSync;
import com.imgood.textech.tileentity.TileEntityAdvanceStorageLink;

/**
 * Display names / 显示名称:
 * - EN: Advanced Storage Linker (container GUI)
 * - ZH: 高级存储链接器（容器界面�?
 * Lang keys: tile.StorageLinkBlock.name
 */
public class GuiAdvanceStorageLink extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/advance_storage_link.png");

    private final TileEntityAdvanceStorageLink tile;
    private int requestTick;

    public GuiAdvanceStorageLink(InventoryPlayer playerInventory, TileEntityAdvanceStorageLink tile) {
        super(new ContainerAdvanceStorageLink(playerInventory, tile));
        this.tile = tile;
        this.xSize = 176;
        this.ySize = 184;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (++requestTick % 20 == 1) {
            AdvanceDataMonitor.ADMCHANEL
                .sendToServer(new PacketRequestItemCountSync(tile.xCoord, tile.yCoord, tile.zCoord));
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager()
            .bindTexture(TEXTURE);
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(startX, startY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.StorageLinkBlock.name");
        this.fontRendererObj
            .drawString(title, (this.xSize - this.fontRendererObj.getStringWidth(title)) / 2, 6, 0x404040);
        this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, 90, 0x404040);

        RenderHelper.enableGUIStandardItemLighting();
        for (int slot = 0; slot < 36; slot++) {
            List<ItemStack> markedItems = tile.getMarkedItems(slot);
            if (!markedItems.isEmpty()) {
                int row = slot / 9;
                int col = slot % 9;
                int xPos = 8 + col * 18;
                int yPos = 18 + row * 18;
                ItemStack marked = markedItems.get(0);
                itemRender.renderItemIntoGUI(fontRendererObj, mc.getTextureManager(), marked, xPos, yPos);
                String count = formatCount(tile.getCachedItemCount(slot));
                fontRendererObj.drawStringWithShadow(count, xPos + 1, yPos + 10, 0xFFFFFF);
            }
        }
        RenderHelper.disableStandardItemLighting();
    }

    private String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fk", count / 1000.0);
        if (count < 1000000000) return String.format("%.1fm", count / 1000000.0);
        return String.format("%.1fb", count / 1000000000.0);
    }
}
