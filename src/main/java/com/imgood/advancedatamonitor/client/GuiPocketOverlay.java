package com.imgood.advancedatamonitor.client;



import java.util.List;



import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.Gui;

import net.minecraft.client.gui.inventory.GuiContainer;

import net.minecraft.client.renderer.RenderHelper;

import net.minecraft.client.renderer.entity.RenderItem;

import net.minecraft.client.resources.I18n;

import net.minecraft.item.ItemStack;



import org.lwjgl.input.Mouse;

import org.lwjgl.opengl.GL11;



import com.imgood.advancedatamonitor.AdvanceDataMonitor;

import com.imgood.advancedatamonitor.mixin.GuiScreenTooltipAccess;

import com.imgood.advancedatamonitor.network.packet.PacketPocketAction;



import cpw.mods.fml.relauncher.Side;

import cpw.mods.fml.relauncher.SideOnly;



/**

 * Renders the draggable, paged extra-inventory overlay on top of the currently

 * open GuiContainer. Slot markers are semi-transparent blue-violet cells; the

 * portal rift texture wraps the grid two cell rings larger so items feel pulled

 * through a dimensional crack.

 */

@SideOnly(Side.CLIENT)

public class GuiPocketOverlay {



    private static final int CELL_SIZE = PocketPortalGuiRenderer.CELL_SIZE;



    private GuiContainer host;

    private int panelX;

    private int panelY;

    private boolean dragging;

    private int dragOffsetX;

    private int dragOffsetY;



    private static final int PAGE_ARROW_W = 12;

    private static final int PAGE_ARROW_H = 16;

    private static final int COLLAPSE_BTN_SIZE = 12;

    private static final int ARROW_BTN_FILL_ENABLED = 0x4A7098E0;

    private static final int ARROW_BTN_FILL_DISABLED = 0x28709880;

    private static final int ARROW_BTN_RIM_ENABLED = 0x5588AAFF;

    private static final int ARROW_BTN_RIM_DISABLED = 0x4088AA60;

    private static final int FONT_LINE_HEIGHT = 9;
    private static final int FOOTER_ARROW_GAP = 3;
    /** Semi-transparent backdrop behind overlay title / footer labels. */
    private static final int LABEL_BACKDROP = 0xC0101828;
    private static final int LABEL_BACKDROP_PAD_X = 4;
    private static final int LABEL_BACKDROP_PAD_Y = 2;



    public GuiPocketOverlay(GuiContainer host) {

        attach(host);

    }



    public void attach(GuiContainer host) {

        this.host = host;

        Minecraft mc = Minecraft.getMinecraft();

        float nx = PocketClientCache.getWindowX();

        float ny = PocketClientCache.getWindowY();

        int pw = getPanelWidth();

        int ph = getPanelHeight();

        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(

            mc,

            mc.displayWidth,

            mc.displayHeight);

        int scaledW = sr.getScaledWidth();

        int scaledH = sr.getScaledHeight();

        panelX = (int) (nx * Math.max(1, scaledW - pw));

        panelY = (int) (ny * Math.max(1, scaledH - ph));

        if (panelX < 0) panelX = 0;

        if (panelY < 0) panelY = 0;

    }



    public boolean isDragging() {

        return dragging;

    }



    /**
     * Hovered pocket-slot tooltip via {@link GuiContainer#drawHoveringText} so GTNH / NEI /
     * community tooltip-style mods can replace the vanilla box. Call after cursor item render.
     */
    public void drawHoveredItemTooltip(int mouseX, int mouseY) {
        if (host == null || PocketClientCache.isCollapsed() || dragging) return;
        int slot = getSlotAt(mouseX, mouseY);
        if (slot < 0) return;
        ItemStack stack = PocketClientCache.getStack(PocketClientCache.getCurrentPage(), slot);
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        List<String> lines = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        if (lines == null || lines.isEmpty()) return;
        ((GuiScreenTooltipAccess) (Object) host).adm$drawHoveringText(lines, mouseX, mouseY, mc.fontRenderer);
    }



    public void setDragging(boolean dragging) {

        this.dragging = dragging;

    }



    public int getPanelWidth() {

        return PocketPortalGuiRenderer.overlayPanelWidth();

    }



    public int getPanelHeight() {

        if (PocketClientCache.isCollapsed()) return COLLAPSE_BTN_SIZE + 8;

        return PocketPortalGuiRenderer.overlayPanelHeight();

    }



    public void draw(float partialTicks) {

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.thePlayer == null) return;

        updatePositionFromMouse();



        GL11.glPushMatrix();



        if (PocketClientCache.isCollapsed()) {

            drawCollapsed();

            GL11.glPopMatrix();

            return;

        }



        int pw = getPanelWidth();

        int slotsPerPage = PocketClientCache.getSlotsPerPage();

        int gridX = PocketPortalGuiRenderer.overlayGridOriginX(panelX);

        int gridY = PocketPortalGuiRenderer.overlayGridOriginY(panelY);



        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glDisable(GL11.GL_ALPHA_TEST);

        double overlayPhase = PocketPortalGuiRenderer.overlayAnimationPhase();
        PocketPortalGuiRenderer.drawOverlayGridPortalRift(gridX, gridY, overlayPhase);



        drawCenteredAnimatedGradientTitle(mc, panelX, pw, getTitleY(mc), I18n.format("adm.title.pocketOverlay"));



        drawCollapseButton(panelX + pw - COLLAPSE_BTN_SIZE - 2, panelY + 2);



        PocketPortalGuiRenderer.drawOverlaySlotGrid(gridX, gridY, slotsPerPage, overlayPhase);



        RenderHelper.enableGUIStandardItemLighting();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        RenderItem renderItem = new RenderItem();

        int cols = 9;

        int page = PocketClientCache.getCurrentPage();

        int hoveredSlot = getSlotAt(getCurrentMouseX(), getCurrentMouseY());

        for (int i = 0; i < slotsPerPage; i++) {

            int row = i / cols;

            int col = i % cols;

            ItemStack stack = PocketClientCache.getStack(page, i);

            if (stack != null) {

                int x = gridX + col * CELL_SIZE + 1;

                int y = gridY + row * CELL_SIZE + 1;

                renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);

                renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);

            }

            if (i == hoveredSlot) {

                int inset = 1;

                Gui.drawRect(

                    gridX + col * CELL_SIZE + inset,

                    gridY + row * CELL_SIZE + inset,

                    gridX + col * CELL_SIZE + CELL_SIZE - inset,

                    gridY + row * CELL_SIZE + CELL_SIZE - inset,

                    0x80FFFFFF);

            }

        }

        RenderHelper.disableStandardItemLighting();



        FooterLayout footer = computeFooterLayout(mc, pw);
        boolean canPrev = PocketClientCache.getCurrentPage() > 0;
        boolean canNext = PocketClientCache.getCurrentPage() < PocketClientCache.getPageCount() - 1;
        drawFooterBackdrop(footer, mc);
        drawArrowButton(footer.leftArrowX, footer.footerY, true, canPrev);
        drawArrowButton(footer.rightArrowX, footer.footerY, false, canNext);
        mc.fontRenderer.drawStringWithShadow(footer.pageText, footer.pageTextX, footer.pageTextY, 0xAACCFF);

        drawDragHint(getCurrentMouseX(), getCurrentMouseY());

        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glPopMatrix();

    }



    private int getTitleY(Minecraft mc) {
        return panelY + 4 - mc.fontRenderer.FONT_HEIGHT / 2 - FONT_LINE_HEIGHT * 2;
    }

    private static void drawCenteredAnimatedGradientTitle(Minecraft mc, int panelX, int panelW, int y, String text) {
        if (text == null || text.isEmpty()) return;
        int totalWidth = mc.fontRenderer.getStringWidth(text);
        int x = panelX + (panelW - totalWidth) / 2;
        drawLabelBackdrop(
            x - LABEL_BACKDROP_PAD_X,
            y - LABEL_BACKDROP_PAD_Y,
            x + totalWidth + LABEL_BACKDROP_PAD_X,
            y + mc.fontRenderer.FONT_HEIGHT + LABEL_BACKDROP_PAD_Y);
        drawAnimatedGradientTitle(mc, x, y, text);
    }

    private static void drawLabelBackdrop(int x1, int y1, int x2, int y2) {
        Gui.drawRect(x1, y1, x2, y2, LABEL_BACKDROP);
    }

    private void drawFooterBackdrop(FooterLayout footer, Minecraft mc) {
        int textBottom = footer.pageTextY + mc.fontRenderer.FONT_HEIGHT;
        int bottom = Math.max(footer.footerY + PAGE_ARROW_H, textBottom) + LABEL_BACKDROP_PAD_Y;
        drawLabelBackdrop(
            footer.leftArrowX - LABEL_BACKDROP_PAD_X,
            footer.footerY - LABEL_BACKDROP_PAD_Y,
            footer.rightArrowX + PAGE_ARROW_W + LABEL_BACKDROP_PAD_X,
            bottom);
    }

    /** Blue-violet gradient title that oscillates over time. */

    private static void drawAnimatedGradientTitle(Minecraft mc, int x, int y, String text) {

        if (text == null || text.isEmpty()) return;

        double phase = (Minecraft.getSystemTime() % 3000L) / 3000.0 * Math.PI * 2.0;

        int offset = 0;

        for (int i = 0; i < text.length(); i++) {

            char ch = text.charAt(i);

            double wave = Math.sin(phase + i * 0.55);

            float blend = (float) ((wave + 1.0) * 0.5);

            int r = (int) (0x55 + (0xCC - 0x55) * blend);

            int g = (int) (0x88 + (0x55 - 0x88) * blend);

            int b = (int) (0xFF + (0xFF - 0xFF) * blend);

            int color = (r << 16) | (g << 8) | b;

            String segment = String.valueOf(ch);

            mc.fontRenderer.drawStringWithShadow(segment, x + offset, y, color);

            offset += mc.fontRenderer.getCharWidth(ch);

        }

    }



    private int getFooterY() {
        int gridY = PocketPortalGuiRenderer.overlayGridOriginY(panelY);
        return gridY + PocketPortalGuiRenderer.gridPixelHeight(PocketPortalGuiRenderer.maxDisplaySlots())
            + PocketPortalGuiRenderer.RIFT_OVERSHOOT + 2 - FONT_LINE_HEIGHT * 2;
    }

    private FooterLayout computeFooterLayout(Minecraft mc, int pw) {
        FooterLayout layout = new FooterLayout();
        layout.footerY = getFooterY();
        layout.pageText = I18n.format(
            "adm.label.pocket.pageFooter",
            PocketClientCache.getCurrentPage() + 1,
            PocketClientCache.getPageCount());
        int pageTextWidth = mc.fontRenderer.getStringWidth(layout.pageText);
        int groupW = PAGE_ARROW_W + FOOTER_ARROW_GAP + pageTextWidth + FOOTER_ARROW_GAP + PAGE_ARROW_W;
        int groupLeft = panelX + (pw - groupW) / 2;
        layout.leftArrowX = groupLeft;
        layout.pageTextX = layout.leftArrowX + PAGE_ARROW_W + FOOTER_ARROW_GAP;
        layout.rightArrowX = layout.pageTextX + pageTextWidth + FOOTER_ARROW_GAP;
        layout.pageTextY = layout.footerY + 4;
        return layout;
    }

    private static final class FooterLayout {
        int footerY;
        int leftArrowX;
        int rightArrowX;
        int pageTextX;
        int pageTextY;
        String pageText;
    }



    private void drawCollapsed() {

        int pw = COLLAPSE_BTN_SIZE + 8;

        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glDisable(GL11.GL_ALPHA_TEST);

        PocketPortalGuiRenderer.drawPanel(panelX, panelY, pw, COLLAPSE_BTN_SIZE + 8);

        Minecraft mc = Minecraft.getMinecraft();

        mc.fontRenderer.drawStringWithShadow("+", panelX + 4, panelY + 3, 0xFFFFFF);

    }



    private void drawCollapseButton(int x, int y) {

        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow("_", x + 3, y + 1, 0xFFFFFF);

    }



    private void drawArrowButton(int x, int y, boolean left, boolean enabled) {

        int rim = enabled ? ARROW_BTN_RIM_ENABLED : ARROW_BTN_RIM_DISABLED;

        int fill = enabled ? ARROW_BTN_FILL_ENABLED : ARROW_BTN_FILL_DISABLED;

        Gui.drawRect(x, y, x + PAGE_ARROW_W, y + PAGE_ARROW_H, rim);

        Gui.drawRect(x + 1, y + 1, x + PAGE_ARROW_W - 1, y + PAGE_ARROW_H - 1, fill);

        Minecraft mc = Minecraft.getMinecraft();

        String glyph = left ? "<" : ">";

        mc.fontRenderer.drawStringWithShadow(glyph, x + 3, y + 4, enabled ? 0xFFFFFF : 0x808080);

    }



    /**

     * 鼠标悬停在可拖动边缘一圈时显示"拖动"小提示;拖动进行中不显示。

     */

    private void drawDragHint(int mouseX, int mouseY) {

        if (dragging) return;

        if (PocketClientCache.isCollapsed()) return;

        if (!isInDragZone(mouseX, mouseY)) return;

        Minecraft mc = Minecraft.getMinecraft();

        String hint = I18n.format("adm.tooltip.pocket.dragHint");

        int textW = mc.fontRenderer.getStringWidth(hint);

        int tx = mouseX + 12;

        int ty = mouseY - 12;

        Gui.drawRect(tx - 3, ty - 2, tx + textW + 3, ty + 9, 0xE0001018);

        Gui.drawRect(tx - 3, ty - 2, tx + textW + 3, ty - 1, 0xFF5588FF);

        mc.fontRenderer.drawStringWithShadow(hint, tx, ty, 0xAACCFF);

    }



    private void updatePositionFromMouse() {

        if (!dragging) return;

        Minecraft mc = Minecraft.getMinecraft();

        int mx = Mouse.getEventX() * mc.currentScreen.width / mc.displayWidth;

        int my = mc.currentScreen.height - Mouse.getEventY() * mc.currentScreen.height / mc.displayHeight - 1;

        panelX = mx - dragOffsetX;

        panelY = my - dragOffsetY;

        if (panelX < 0) panelX = 0;

        if (panelY < 0) panelY = 0;

    }



    public void onDragFinished() {

        if (!dragging) return;

        dragging = false;

        Minecraft mc = Minecraft.getMinecraft();

        float nx = (float) panelX / Math.max(1, mc.currentScreen.width - getPanelWidth());

        float ny = (float) panelY / Math.max(1, mc.currentScreen.height - getPanelHeight());

        float cx = Math.max(0f, Math.min(1f, nx));

        float cy = Math.max(0f, Math.min(1f, ny));

        PocketClientCache.setWindowPos(cx, cy);

        AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setWindowPos(cx, cy));

    }



    public boolean onMouseClicked(int mouseX, int mouseY, int button) {

        if (PocketClientCache.isCollapsed()) {

            if (hitsRect(mouseX, mouseY, panelX, panelY, COLLAPSE_BTN_SIZE + 8, COLLAPSE_BTN_SIZE + 8)) {

                AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setCollapsed(false));

                return true;

            }

            return false;

        }

        int pw = getPanelWidth();

        if (hitsRect(

            mouseX,

            mouseY,

            panelX + pw - COLLAPSE_BTN_SIZE - 2,

            panelY + 2,

            COLLAPSE_BTN_SIZE,

            COLLAPSE_BTN_SIZE)) {

            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setCollapsed(true));

            return true;

        }

        if (isInDragZone(mouseX, mouseY)) {

            dragging = true;

            dragOffsetX = mouseX - panelX;

            dragOffsetY = mouseY - panelY;

            return true;

        }

        int slotsPerPage = PocketClientCache.getSlotsPerPage();
        FooterLayout footer = computeFooterLayout(Minecraft.getMinecraft(), pw);
        if (hitsRect(mouseX, mouseY, footer.leftArrowX, footer.footerY, PAGE_ARROW_W, PAGE_ARROW_H)) {

            if (PocketClientCache.getCurrentPage() > 0) {

                PocketClientCache.setCurrentPage(PocketClientCache.getCurrentPage() - 1);

                AdvanceDataMonitor.ADMCHANEL

                    .sendToServer(PacketPocketAction.setPage(PocketClientCache.getCurrentPage()));

            }

            return true;

        }

        if (hitsRect(mouseX, mouseY, footer.rightArrowX, footer.footerY, PAGE_ARROW_W, PAGE_ARROW_H)) {

            if (PocketClientCache.getCurrentPage() < PocketClientCache.getPageCount() - 1) {

                PocketClientCache.setCurrentPage(PocketClientCache.getCurrentPage() + 1);

                AdvanceDataMonitor.ADMCHANEL

                    .sendToServer(PacketPocketAction.setPage(PocketClientCache.getCurrentPage()));

            }

            return true;

        }

        return false;

    }



    public void onMouseReleased(int mouseX, int mouseY, int button) {

        dragging = false;

    }



    public int getSlotAt(int mouseX, int mouseY) {

        if (PocketClientCache.isCollapsed()) return -1;

        int slotsPerPage = PocketClientCache.getSlotsPerPage();

        int cols = 9;

        int gridX = PocketPortalGuiRenderer.overlayGridOriginX(panelX);

        int gridY = PocketPortalGuiRenderer.overlayGridOriginY(panelY);

        int relX = mouseX - gridX;

        int relY = mouseY - gridY;

        if (relX < 0 || relY < 0) return -1;

        int col = relX / CELL_SIZE;

        int row = relY / CELL_SIZE;

        if (col >= cols) return -1;

        int idx = row * cols + col;

        return (idx >= 0 && idx < slotsPerPage) ? idx : -1;

    }



    public void setPanelPos(int x, int y) {

        panelX = x;

        panelY = y;

    }



    public int getPanelX() { return panelX; }

    public int getPanelY() { return panelY; }



    public boolean onMouseWheel(int mouseX, int mouseY, int delta) {

        if (PocketClientCache.isCollapsed()) return false;

        if (!hitsRect(mouseX, mouseY, panelX, panelY, getPanelWidth(), getPanelHeight())) return false;

        if (delta > 0) {

            if (PocketClientCache.getCurrentPage() > 0) {

                PocketClientCache.setCurrentPage(PocketClientCache.getCurrentPage() - 1);

                AdvanceDataMonitor.ADMCHANEL

                    .sendToServer(PacketPocketAction.setPage(PocketClientCache.getCurrentPage()));

            }

        } else if (delta < 0) {

            if (PocketClientCache.getCurrentPage() < PocketClientCache.getPageCount() - 1) {

                PocketClientCache.setCurrentPage(PocketClientCache.getCurrentPage() + 1);

                AdvanceDataMonitor.ADMCHANEL

                    .sendToServer(PacketPocketAction.setPage(PocketClientCache.getCurrentPage()));

            }

        }

        return true;

    }



    public boolean hitsPanel(int mouseX, int mouseY) {

        if (PocketClientCache.isCollapsed()) {

            return hitsRect(mouseX, mouseY, panelX, panelY, COLLAPSE_BTN_SIZE + 8, COLLAPSE_BTN_SIZE + 8);

        }

        return hitsRect(mouseX, mouseY, panelX, panelY, getPanelWidth(), getPanelHeight());

    }



    /**

     * Expanded overlay 的"边缘一圈"拖动区:整个面板减去中央 slot 网格、折叠按钮与翻页箭头。

     * 折叠状态返回 false(点击用于展开)。

     */

    public boolean isInDragZone(int mouseX, int mouseY) {

        if (PocketClientCache.isCollapsed()) return false;

        if (!hitsPanel(mouseX, mouseY)) return false;

        int pw = getPanelWidth();

        int gridX = PocketPortalGuiRenderer.overlayGridOriginX(panelX);

        int gridY = PocketPortalGuiRenderer.overlayGridOriginY(panelY);

        int gridW = PocketPortalGuiRenderer.gridPixelWidth();

        int gridH = PocketPortalGuiRenderer.gridPixelHeight(PocketPortalGuiRenderer.maxDisplaySlots());

        if (hitsRect(mouseX, mouseY, gridX, gridY, gridW, gridH)) return false;

        if (hitsRect(mouseX, mouseY, panelX + pw - COLLAPSE_BTN_SIZE - 2, panelY + 2,

            COLLAPSE_BTN_SIZE, COLLAPSE_BTN_SIZE)) return false;

        FooterLayout footer = computeFooterLayout(Minecraft.getMinecraft(), pw);

        if (hitsRect(mouseX, mouseY, footer.leftArrowX, footer.footerY, PAGE_ARROW_W, PAGE_ARROW_H)) return false;

        if (hitsRect(mouseX, mouseY, footer.rightArrowX, footer.footerY, PAGE_ARROW_W, PAGE_ARROW_H)) return false;

        return true;

    }



    private static boolean hitsRect(int mx, int my, int x, int y, int w, int h) {

        return mx >= x && mx < x + w && my >= y && my < y + h;

    }



    private static int getCurrentMouseX() {

        Minecraft mc = Minecraft.getMinecraft();

        return Mouse.getEventX() * mc.currentScreen.width / mc.displayWidth;

    }



    private static int getCurrentMouseY() {

        Minecraft mc = Minecraft.getMinecraft();

        return mc.currentScreen.height - Mouse.getEventY() * mc.currentScreen.height / mc.displayHeight - 1;

    }

}
