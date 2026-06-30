package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.client.PocketClientCache;
import com.imgood.textech.client.PocketPortalGuiRenderer;
import com.imgood.textech.gui.container.ContainerPocketStorage;
import com.imgood.textech.handler.PocketState;
import com.imgood.textech.network.packet.PacketPocketAction;

/**
 * Display names / 显示名称:
 * - EN: Dimensional Pocket Storage
 * - ZH: 次元口袋存储
 * Lang keys: adm.title.pocketOverlay, adm.button.pocket.upgrade
 *
 * Native container-backed storage GUI for the Dimensional Pocket. Shows the
 * pocket's current page as a real slot grid (items movable via vanilla
 * windowClick / shift-click) plus the player inventory, with overlay-style
 * portal effects and header pagination.
 */
public class GuiPocketStorage extends GuiContainer {

    private static final int GUI_WIDTH = 176;

    private final ContainerPocketStorage container;
    private int configBtnX;
    private int configBtnY;

    public GuiPocketStorage(EntityPlayer player) {
        super(new ContainerPocketStorage(player));
        this.container = (ContainerPocketStorage) inventorySlots;
        this.xSize = GUI_WIDTH;
        this.ySize = computeHeight();
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.requestSync());
    }

    private static int computeHeight() {
        int rows = (PocketState.SLOTS_PER_PAGE_CAP + 8) / 9;
        return PocketPortalGuiRenderer.STORAGE_SLOT_ORIGIN_Y + rows * PocketPortalGuiRenderer.CELL_SIZE
            + 14
            + PocketPortalGuiRenderer.STORAGE_PLAYER_INV_EXTRA_Y
            + 3 * PocketPortalGuiRenderer.CELL_SIZE
            + 4
            + PocketPortalGuiRenderer.CELL_SIZE
            + 8;
    }

    @Override
    public void initGui() {
        this.ySize = computeHeight();
        super.initGui();
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        int rows = (PocketState.SLOTS_PER_PAGE_CAP + 8) / 9;
        configBtnX = startX + (this.xSize - PocketPortalGuiRenderer.STORAGE_CONFIG_BTN_W) / 2;
        configBtnY = startY + PocketPortalGuiRenderer.STORAGE_SLOT_ORIGIN_Y
            + rows * PocketPortalGuiRenderer.CELL_SIZE
            + 6;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (PocketClientCache.isReceived()) {
            int cachedSlots = PocketClientCache.getSlotsPerPage();
            int cachedPages = PocketClientCache.getPageCount();
            if (cachedSlots != container.getSlotsPerPage() || cachedPages != container.getPageCount()) {
                net.minecraft.entity.player.EntityPlayer p = mc.thePlayer;
                if (p != null) {
                    int savedWindowId = mc.thePlayer.openContainer.windowId;
                    GuiPocketStorage newGui = new GuiPocketStorage(p);
                    newGui.inventorySlots.windowId = savedWindowId;
                    mc.displayGuiScreen(newGui);
                }
                return;
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        int slotsPerPage = container.getSlotsPerPage();
        PocketPortalGuiRenderer.drawStorageRift(startX, startY, slotsPerPage);

        Minecraft mc = Minecraft.getMinecraft();
        int currentPage = container.getCurrentPage();
        int pageCount = container.getPageCount();
        boolean canPrev = currentPage > 0;
        boolean canNext = currentPage < pageCount - 1;
        PocketPortalGuiRenderer.StorageHeaderLayout header = PocketPortalGuiRenderer
            .computeStorageHeaderLayout(mc, startX, startY, this.xSize, currentPage, pageCount);
        PocketPortalGuiRenderer.drawStorageHeader(mc, startX, header, canPrev, canNext);

        PocketPortalGuiRenderer
            .drawStorageUpgradeButton(mc, configBtnX, configBtnY, I18n.format("adm.button.pocket.upgrade"));

        PocketPortalGuiRenderer.drawVanillaPlayerInventoryBackground(startX, startY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int playerAreaTop = PocketPortalGuiRenderer.storagePlayerInventoryOriginY();
        this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, playerAreaTop - 10, 0x404040);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        if (mouseX < startX || mouseX >= startX + this.xSize || mouseY < startY || mouseY >= startY + this.ySize) {
            return;
        }
        int currentPage = container.getCurrentPage();
        int pageCount = container.getPageCount();
        if (wheel > 0) {
            if (currentPage > 0) applyPageChange(currentPage - 1);
        } else if (currentPage < pageCount - 1) {
            applyPageChange(currentPage + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            if (handlePageButtonClick(mouseX, mouseY)) {
                return;
            }
            if (PocketPortalGuiRenderer.hitsStorageUpgradeButton(configBtnX, configBtnY, mouseX, mouseY)) {
                AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.openConfigGui());
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handlePageButtonClick(int mouseX, int mouseY) {
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        int currentPage = container.getCurrentPage();
        int pageCount = container.getPageCount();
        PocketPortalGuiRenderer.StorageHeaderLayout header = PocketPortalGuiRenderer
            .computeStorageHeaderLayout(Minecraft.getMinecraft(), startX, startY, this.xSize, currentPage, pageCount);
        int lineH = this.fontRendererObj.FONT_HEIGHT;
        if (PocketPortalGuiRenderer.hitsStoragePageArrow(header, mouseX, mouseY, true, lineH)) {
            if (currentPage > 0) {
                applyPageChange(currentPage - 1);
            }
            return true;
        }
        if (PocketPortalGuiRenderer.hitsStoragePageArrow(header, mouseX, mouseY, false, lineH)) {
            if (currentPage < pageCount - 1) {
                applyPageChange(currentPage + 1);
            }
            return true;
        }
        return false;
    }

    private void applyPageChange(int targetPage) {
        PocketClientCache.setCurrentPage(targetPage);
        container.applyClientPage(targetPage);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setPage(targetPage));
    }
}
