package com.imgood.advancedatamonitor.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.client.PocketPortalGuiRenderer;
import com.imgood.advancedatamonitor.client.PocketClientCache;
import com.imgood.advancedatamonitor.gui.container.ContainerPocketStorage;
import com.imgood.advancedatamonitor.network.packet.PacketPocketAction;

/**
 * Display names / 显示名称:
 * - EN: Dimensional Pocket Storage
 * - ZH: 次元口袋存储
 * Lang keys: adm.title.pocketStoragePaged, adm.button.pocket.prevPage, adm.button.pocket.nextPage, adm.button.pocket.openConfig
 *
 * Native container-backed storage GUI for the Dimensional Pocket. Shows the
 * pocket's current page as a real slot grid (items movable via vanilla
 * windowClick / shift-click) plus the player inventory, with prev/next page
 * buttons.
 */
public class GuiPocketStorage extends GuiContainer {

    private static final int GUI_WIDTH = 176;

    private final ContainerPocketStorage container;
    private GuiButton prevButton;
    private GuiButton nextButton;
    private GuiButton configButton;

    public GuiPocketStorage(EntityPlayer player) {
        super(new ContainerPocketStorage(player));
        this.container = (ContainerPocketStorage) inventorySlots;
        this.xSize = GUI_WIDTH;
        // Use cached dimensions to avoid data rollback: even before the first sync
        // arrives, PocketClientCache holds the last-known values from the previous
        // session (or defaults 1/1 which is safe — sync will widen if needed).
        this.ySize = computeHeight();
        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.requestSync());
    }

    /** Fixed height for max-size pocket grid (7 rows) + player inventory. */
    private static int computeHeight() {
        int rows = (PocketState.SLOTS_PER_PAGE_CAP + 8) / 9;
        return 18 + rows * 18 + 14 + 3 * 18 + 4 + 18 + 8;
    }

    @Override
    public void initGui() {
        this.ySize = computeHeight();
        super.initGui();
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        int rows = (PocketState.SLOTS_PER_PAGE_CAP + 8) / 9;
        int btnY = startY + 18 + rows * 18 + 2;
        prevButton = new GuiButton(0, startX + 8, btnY, 36, 16, I18n.format("adm.button.pocket.prevPage"));
        nextButton = new GuiButton(1, startX + this.xSize - 8 - 36, btnY, 36, 16, I18n.format("adm.button.pocket.nextPage"));
        configButton = new GuiButton(2, startX + this.xSize / 2 - 40, btnY, 80, 16, I18n.format("adm.button.pocket.openConfig"));
        this.buttonList.add(prevButton);
        this.buttonList.add(nextButton);
        this.buttonList.add(configButton);
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (prevButton != null) prevButton.enabled = container.getCurrentPage() > 0;
        if (nextButton != null) nextButton.enabled = container.getCurrentPage() < container.getPageCount() - 1;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == prevButton) {
            int target = Math.max(0, container.getCurrentPage() - 1);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setPage(target));
        } else if (button == nextButton) {
            int target = Math.min(container.getPageCount() - 1, container.getCurrentPage() + 1);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setPage(target));
        } else if (button == configButton) {
            // Request the server to open the config GUI. Opening client-side leaves the
            // server's openContainer as the default ContainerPlayer (windowId 0), so upgrade
            // card slot clicks would be executed on the wrong container and bounced back.
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.openConfigGui());
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // If a sync arrived and the server's slotsPerPage/pageCount differs from what
        // we built the container with, recreate this GUI locally with the correct
        // dimensions. PocketClientCache now holds the authoritative values after sync.
        //
        // IMPORTANT: When recreating the GUI, preserve the windowId from the old container.
        // The new container's windowId defaults to 0, but the server assigned a non-zero
        // windowId when the GUI was originally opened via FMLNetworkHandler.openGui.
        // If the windowId is lost, all client→server windowClick packets are rejected
        // AND all server→client item-sync packets are rejected, making the GUI unusable.
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
        updateButtonStates();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        int slotsPerPage = container.getSlotsPerPage();
        PocketPortalGuiRenderer.drawStorageRift(startX, startY, slotsPerPage);
        PocketPortalGuiRenderer.drawVanillaPlayerInventoryBackground(startX, startY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = String.format(
            I18n.format("adm.title.pocketStoragePaged"),
            container.getCurrentPage() + 1,
            container.getPageCount());
        this.fontRendererObj
            .drawString(title, (this.xSize - this.fontRendererObj.getStringWidth(title)) / 2, 4, 0xFFFFFF);
        int rows = (PocketState.SLOTS_PER_PAGE_CAP + 8) / 9;
        int playerAreaTop = 18 + rows * 18 + 14;
        this.fontRendererObj
            .drawString(I18n.format("container.inventory"), 8, playerAreaTop - 10, 0x404040);
    }
}
