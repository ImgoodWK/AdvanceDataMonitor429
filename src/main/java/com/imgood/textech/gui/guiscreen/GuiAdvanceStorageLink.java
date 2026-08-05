package com.imgood.textech.gui.guiscreen;

import java.util.List;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.container.ContainerAdvanceStorageLink;
import com.imgood.textech.gui.custom.ADM_UiContainer;
import com.imgood.textech.gui.framework.UiSlot;
import com.imgood.textech.gui.framework.UiText;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.network.packet.PacketRequestItemCountSync;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;

/**
 * Display names / 显示名称:
 * - EN: Advanced Storage Linker (container GUI)
 * - ZH: 高级存储链接器（容器界面：
 * Lang keys: tile.StorageLinkBlock.name
 */
public class GuiAdvanceStorageLink extends ADM_UiContainer {

    private final TileEntityAdvanceNetworkLink tile;
    private int requestTick;

    public GuiAdvanceStorageLink(InventoryPlayer playerInventory, TileEntityAdvanceNetworkLink tile) {
        super(new ContainerAdvanceStorageLink(playerInventory, tile), UiThemes.ADM);
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
        drawMainPanel(0, 0, xSize, ySize);
        int left = panelLeft();
        int top = panelTop();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                UiSlot.drawTheme(theme(), left + 7 + col * 18, top + 17 + row * 18);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                UiSlot.drawTheme(theme(), left + 7 + col * 18, top + 101 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            UiSlot.drawTheme(theme(), left + 7 + col * 18, top + 159);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.StorageLinkBlock.name");
        UiText.drawCenteredTitle(UiThemes.ADM, fontRendererObj, title, xSize / 2, 6);
        UiText.drawLabel(UiThemes.ADM, fontRendererObj, I18n.format("container.inventory"), 8, 90);

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
