package com.imgood.advancedatamonitor.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.client.PocketClientCache;
import com.imgood.advancedatamonitor.gui.container.ContainerDimensionalPocket;
import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.network.packet.PacketPocketAction;

/**
 * Display names / 显示名称:
 * - EN: Dimensional Pocket Config
 * - ZH: 次元口袋配置
 * Lang keys: adm.title.pocketConfig, adm.label.pocket.*, adm.button.pocket.*
 *
 * Container-backed config GUI. Renders the two upgrade slots (space/page) on
 * top, the player inventory below, plus a toggle button for the overlay switch
 * and a small capacity stats readout. Upgrade counts are committed back to the
 * player-bound PocketState via ContainerDimensionalPocket's virtual inventory.
 */
public class GuiDimensionalPocketConfig extends GuiContainer {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 190;

    private final ContainerDimensionalPocket container;
    private GuiButton toggleButton;
    private int refreshTick = 0;

    public GuiDimensionalPocketConfig(EntityPlayer player) {
        super(new ContainerDimensionalPocket(player));
        this.container = (ContainerDimensionalPocket) inventorySlots;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        toggleButton = new GuiButton(0, startX + 110, startY + 18, 56, 16, getToggleLabel());
        this.buttonList.add(toggleButton);
    }

    private String getToggleLabel() {
        return PocketClientCache.isEnabled() ? I18n.format("adm.button.pocket.toggleOff")
            : I18n.format("adm.button.pocket.toggleOn");
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (++refreshTick % 20 == 1) {
            // Pull a fresh snapshot from the server so the stats stay accurate.
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.requestSync());
            if (toggleButton != null) {
                toggleButton.displayString = getToggleLabel();
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == toggleButton) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.toggleEnabled());
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // Draw a semi-transparent rounded background so we don't need a dedicated texture.
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        drawRect(startX, startY, startX + this.xSize, startY + this.ySize, 0xC0101830);
        // Inner panel border
        drawRect(startX, startY, startX + this.xSize, startY + 1, 0xFF3A5A8A);
        drawRect(startX, startY + this.ySize - 1, startX + this.xSize, startY + this.ySize, 0xFF3A5A8A);
        drawRect(startX, startY, startX + 1, startY + this.ySize, 0xFF3A5A8A);
        drawRect(startX + this.xSize - 1, startY, startX + this.xSize, startY + this.ySize, 0xFF3A5A8A);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("adm.title.pocketConfig");
        this.fontRendererObj
            .drawString(title, (this.xSize - this.fontRendererObj.getStringWidth(title)) / 2, 6, 0xFFFFFF);

        this.fontRendererObj.drawString(I18n.format("adm.label.pocket.spaceUpgrade"), 18, 12, 0xAACCFF);
        this.fontRendererObj.drawString(I18n.format("adm.label.pocket.pageUpgrade"), 18, 42, 0xAACCFF);

        int spaceCount = container.getSpaceUpgradeCount();
        int pageCount = container.getPageUpgradeCount();
        String spaceStat = spaceCount + " / " + PocketState.MAX_SPACE_UPGRADES;
        String pageStat = pageCount + " / " + PocketState.MAX_PAGE_UPGRADES;
        this.fontRendererObj.drawString(spaceStat, 60, 12, 0xFFFFFF);
        this.fontRendererObj.drawString(pageStat, 60, 42, 0xFFFFFF);

        // Capacity line
        int slotsPerPage = Math
            .min(PocketState.SLOTS_PER_PAGE_CAP, 1 + Math.min(spaceCount, PocketState.MAX_SPACE_UPGRADES - 2));
        int pages = PocketState.BASE_PAGES
            + (spaceCount >= PocketState.MAX_SPACE_UPGRADES ? Math.min(pageCount, PocketState.MAX_PAGE_UPGRADES) : 0);
        String capacity = String
            .format(I18n.format("adm.label.pocket.capacity"), slotsPerPage, pages, slotsPerPage * pages);
        this.fontRendererObj.drawString(capacity, 18, 58, 0xFFE08A);

        if (spaceCount < PocketState.MAX_SPACE_UPGRADES) {
            this.fontRendererObj.drawString(I18n.format("adm.error.pocket.pageUpgradeBlocked"), 18, 70, 0xFFAA55);
        }

        this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, 60, 0x404040);
    }
}
