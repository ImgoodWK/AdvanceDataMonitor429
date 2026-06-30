package com.imgood.advancedatamonitor.gui.guiscreen;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumChatFormatting;

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
 * Container-backed config GUI with drawn upgrade slots and player inventory.
 */
public class GuiDimensionalPocketConfig extends GuiContainer {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 222;
    private static final int UPGRADE_CELL = PocketPortalGuiRenderer.CELL_SIZE;
    private static final float UPGRADE_LABEL_SCALE = 0.72F;

    private static final int UPGRADE_SLOT_COUNT = 4;

    private static final int[] UPGRADE_SLOT_X = new int[] {
        PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X,
        PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X + PocketPortalGuiRenderer.CONFIG_UPGRADE_COL_STEP,
        PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X,
        PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X + PocketPortalGuiRenderer.CONFIG_UPGRADE_COL_STEP,
    };
    private static final int[] UPGRADE_SLOT_Y = new int[] {
        PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_Y,
        PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_Y,
        PocketPortalGuiRenderer.CONFIG_UPGRADE_ROW2_Y,
        PocketPortalGuiRenderer.CONFIG_UPGRADE_ROW2_Y,
    };
    private static final String[] UPGRADE_LABEL_KEYS = new String[] {
        "adm.label.pocket.slot.space",
        "adm.label.pocket.slot.page",
        "adm.label.pocket.slot.stack",
        "adm.label.pocket.slot.infinite",
    };
    private static final String[][] UPGRADE_EMPTY_TOOLTIP = new String[][] {
        { "adm.tooltip.pocket.space_card.title", "adm.tooltip.pocket.configSlot.space" },
        { "adm.tooltip.pocket.page_card.title", "adm.tooltip.pocket.configSlot.page" },
        { "adm.tooltip.pocket.stack_card.title", "adm.tooltip.pocket.configSlot.stack" },
        { "adm.tooltip.pocket.infinite_stack_card.title", "adm.tooltip.pocket.configSlot.infinite" },
    };

    private final ContainerDimensionalPocket container;
    private int collapseBtnX;
    private int collapseBtnY;
    private int collapseLabelY;
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
        collapseBtnX = startX + PocketPortalGuiRenderer.CONFIG_COLLAPSE_BTN_X;
        collapseBtnY = startY + PocketPortalGuiRenderer.CONFIG_COLLAPSE_BTN_Y;
        collapseLabelY = collapseBtnY - PocketPortalGuiRenderer.CONFIG_LINE_HEIGHT - 1;
    }

    private String getCollapseButtonLabel() {
        return PocketClientCache.isCollapsed() ? I18n.format("adm.button.pocket.expand")
            : I18n.format("adm.button.pocket.collapse");
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (++refreshTick % 40 == 1) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.requestSync());
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int startX = (this.width - this.xSize) / 2;
        int startY = (this.height - this.ySize) / 2;

        PocketPortalGuiRenderer.drawSimpleConfigBackground(startX, startY);

        Minecraft mc = Minecraft.getMinecraft();
        PocketPortalGuiRenderer.drawOverlayStyleTitle(
            mc,
            startX + PocketPortalGuiRenderer.CONFIG_UPGRADE_ORIGIN_X,
            startY + 6,
            I18n.format("adm.title.pocketOverlay"));
        String collapseHint = I18n.format("adm.label.pocket.overlayDefault");
        int hintW = mc.fontRenderer.getStringWidth(collapseHint);
        int hintX = collapseBtnX + (PocketPortalGuiRenderer.CONFIG_TOGGLE_BTN_W - hintW) / 2;
        mc.fontRenderer.drawString(collapseHint, hintX, collapseLabelY, 0xAACCFF);
        PocketPortalGuiRenderer.drawPortalStyleButton(
            mc,
            collapseBtnX,
            collapseBtnY,
            PocketPortalGuiRenderer.CONFIG_TOGGLE_BTN_W,
            PocketPortalGuiRenderer.CONFIG_TOGGLE_BTN_H,
            getCollapseButtonLabel());
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawUpgradeSlotLabels();

        int spaceCount = container.getSpaceUpgradeCount();
        int pageCount = container.getPageUpgradeCount();
        int stackCount = container.getStackUpgradeCount();
        boolean infinite = container.hasInfiniteStackUpgrade();

        int slotsPerPage = Math
            .min(PocketState.SLOTS_PER_PAGE_CAP, 1 + Math.min(spaceCount, PocketState.MAX_SPACE_UPGRADES - 2));
        int pages = PocketState.BASE_PAGES
            + (spaceCount >= PocketState.MAX_SPACE_UPGRADES ? Math.min(pageCount, PocketState.MAX_PAGE_UPGRADES) : 0);
        int totalSlots = slotsPerPage * pages;
        int line = PocketPortalGuiRenderer.CONFIG_LINE_HEIGHT;
        this.fontRendererObj.drawString(
            I18n.format("adm.label.pocket.capacity", slotsPerPage, pages, totalSlots),
            18,
            78 + line,
            0xFFE08A);

        String stackInfo;
        if (infinite) {
            stackInfo = I18n.format("adm.label.pocket.stackLimitInfinite");
        } else {
            int perSlot = 64 * (stackCount == 0 ? 1 : (1 << stackCount));
            stackInfo = I18n.format("adm.label.pocket.stackLimit", perSlot);
        }
        this.fontRendererObj.drawString(stackInfo, 18, 88 + line, 0x00FFAA);

        if (container.isUpgradeRemovalBlocked()) {
            this.fontRendererObj.drawString(I18n.format("adm.hint.pocket.upgradeLockedWhileStored"), 18, 98 + line, 0xFFAA55);
        } else if (spaceCount < PocketState.MAX_SPACE_UPGRADES && pageCount > 0) {
            this.fontRendererObj.drawString(I18n.format("adm.error.pocket.pageUpgradeBlocked"), 18, 98 + line, 0xFFAA55);
        }

        this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, 120, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawEmptyUpgradeSlotTooltip(mouseX, mouseY);
    }

    private void drawUpgradeSlotLabels() {
        for (int i = 0; i < UPGRADE_SLOT_X.length; i++) {
            String label = I18n.format(UPGRADE_LABEL_KEYS[i]);
            int slotX = UPGRADE_SLOT_X[i];
            int slotY = UPGRADE_SLOT_Y[i];
            int labelY = slotY - 10;
            drawScaledCenteredLabel(slotX, labelY, UPGRADE_CELL, label, 0xAACCFF);
        }
    }

    private void drawScaledCenteredLabel(int areaX, int areaY, int areaW, String text, int color) {
        if (text == null || text.isEmpty()) return;
        float scale = UPGRADE_LABEL_SCALE;
        int rawW = this.fontRendererObj.getStringWidth(text);
        float scaledW = rawW * scale;
        float scaledH = this.fontRendererObj.FONT_HEIGHT * scale;
        float drawX = areaX + (areaW - scaledW) / 2.0F;
        float drawY = areaY + (10.0F - scaledH) / 2.0F;
        GL11.glPushMatrix();
        GL11.glTranslatef(drawX, drawY, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        this.fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void drawEmptyUpgradeSlotTooltip(int screenMouseX, int screenMouseY) {
        int guiLeft = (this.width - this.xSize) / 2;
        int guiTop = (this.height - this.ySize) / 2;
        int relX = screenMouseX - guiLeft;
        int relY = screenMouseY - guiTop;

        for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
            Slot slot = (Slot) this.container.inventorySlots.get(i);
            if (slot == null || slot.getHasStack()) continue;
            int x = slot.xDisplayPosition;
            int y = slot.yDisplayPosition;
            if (relX < x || relX >= x + UPGRADE_CELL || relY < y || relY >= y + UPGRADE_CELL) continue;
            List<String> lines = Arrays.asList(
                EnumChatFormatting.LIGHT_PURPLE + I18n.format(UPGRADE_EMPTY_TOOLTIP[i][0]),
                EnumChatFormatting.GRAY + I18n.format(UPGRADE_EMPTY_TOOLTIP[i][1]));
            this.drawHoveringText(lines, screenMouseX, screenMouseY, this.fontRendererObj);
            return;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0
            && PocketPortalGuiRenderer.hitsPortalStyleButton(
                collapseBtnX,
                collapseBtnY,
                PocketPortalGuiRenderer.CONFIG_TOGGLE_BTN_W,
                PocketPortalGuiRenderer.CONFIG_TOGGLE_BTN_H,
                mouseX,
                mouseY)) {
            boolean next = !PocketClientCache.isCollapsed();
            PocketClientCache.setCollapsed(next);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setCollapsed(next));
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}
