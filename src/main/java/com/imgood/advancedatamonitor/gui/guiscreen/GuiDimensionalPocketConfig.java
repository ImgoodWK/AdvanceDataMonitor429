package com.imgood.advancedatamonitor.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.client.PocketPortalGuiRenderer;
import com.imgood.advancedatamonitor.client.PocketClientCache;
import com.imgood.advancedatamonitor.gui.container.ContainerDimensionalPocket;
import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.network.packet.PacketPocketAction;

/**
 * Display names / 显示名称:
 * - EN: Dimensional Pocket Config
 * - ZH: 次元口袋配置
 *
 * Container-backed config GUI. Renders the four upgrade slots (space/page/stack/infinite stack)
 * on top, the player inventory below, plus a toggle button for the overlay switch
 * and capacity stats readout.
 */
public class GuiDimensionalPocketConfig extends GuiContainer {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 222;

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
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.requestSync());
            if (toggleButton != null) {
                toggleButton.displayString = getToggleLabel();
            }
        }
        container.refreshUpgradeDisplayFromClientCache();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == toggleButton) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.toggleEnabled());
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;
        PocketPortalGuiRenderer.drawVanillaConfigBackground(startX, startY);
        PocketPortalGuiRenderer.drawVanillaSlotCell(startX + 18, startY + 22);
        PocketPortalGuiRenderer.drawVanillaSlotCell(startX + 18 + 18 + 4, startY + 22);
        PocketPortalGuiRenderer.drawVanillaSlotCell(startX + 18, startY + 54);
        PocketPortalGuiRenderer.drawVanillaSlotCell(startX + 18 + 18 + 4, startY + 54);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("adm.title.pocketConfig");
        this.fontRendererObj
            .drawString(title, (this.xSize - this.fontRendererObj.getStringWidth(title)) / 2, 6, 0xFFFFFF);

        // Row 1 labels above row 1 slots (slots at Y=22)
        this.fontRendererObj.drawString(I18n.format("adm.label.pocket.spaceUpgrade"), 18, 12, 0xAACCFF);
        this.fontRendererObj.drawString(I18n.format("adm.label.pocket.pageUpgrade"), 62, 12, 0xAACCFF);

        int spaceCount = container.getSpaceUpgradeCount();
        int pageCount = container.getPageUpgradeCount();
        String spaceStat = spaceCount + " / " + PocketState.MAX_SPACE_UPGRADES;
        String pageStat = pageCount + " / " + PocketState.MAX_PAGE_UPGRADES;
        // Row 1 stats below row 1 slots (slots cover 22-40)
        this.fontRendererObj.drawString(spaceStat, 18, 44, 0xFFFFFF);
        this.fontRendererObj.drawString(pageStat, 62, 44, 0xFFFFFF);

        // Row 2 labels above row 2 slots (slots at Y=54)
        this.fontRendererObj.drawString(I18n.format("adm.label.pocket.stackUpgrade"), 18, 46, 0xAACCFF);
        this.fontRendererObj.drawString(I18n.format("adm.label.pocket.infiniteStackUpgrade"), 62, 46, 0xAACCFF);

        int stackCount = container.getStackUpgradeCount();
        boolean infinite = container.hasInfiniteStackUpgrade();
        String stackStat = stackCount + " / " + PocketState.MAX_STACK_UPGRADES;
        String infiniteStat = infinite ? I18n.format("adm.label.on") : I18n.format("adm.label.off");
        // Row 2 stats below row 2 slots (slots cover 54-72)
        this.fontRendererObj.drawString(stackStat, 18, 76, 0xFFFFFF);
        this.fontRendererObj.drawString(infiniteStat, 62, 76, 0xFFFFFF);

        // Capacity line
        int slotsPerPage = Math
            .min(PocketState.SLOTS_PER_PAGE_CAP, 1 + Math.min(spaceCount, PocketState.MAX_SPACE_UPGRADES - 2));
        int pages = PocketState.BASE_PAGES
            + (spaceCount >= PocketState.MAX_SPACE_UPGRADES ? Math.min(pageCount, PocketState.MAX_PAGE_UPGRADES) : 0);
        String capacity = String
            .format(I18n.format("adm.label.pocket.capacity"), slotsPerPage, pages, slotsPerPage * pages);
        this.fontRendererObj.drawString(capacity, 18, 90, 0xFFE08A);

        // Stack multiplier line
        if (stackCount > 0 || infinite) {
            String stackInfo;
            if (infinite) {
                stackInfo = I18n.format("adm.label.pocket.stackLimitInfinite");
            } else {
                int mult = 1 << stackCount;
                stackInfo = String.format(I18n.format("adm.label.pocket.stackLimit"), 64 * mult);
            }
            this.fontRendererObj.drawString(stackInfo, 18, 100, 0x00FFAA);
        }

        if (spaceCount < PocketState.MAX_SPACE_UPGRADES && pageCount > 0) {
            this.fontRendererObj.drawString(I18n.format("adm.error.pocket.pageUpgradeBlocked"), 18, 110, 0xFFAA55);
        }

        this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, 120, 0x404040);
    }
}
