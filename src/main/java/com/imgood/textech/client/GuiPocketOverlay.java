package com.imgood.textech.client;



import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.Gui;

import net.minecraft.client.gui.inventory.GuiContainer;

import net.minecraft.client.renderer.RenderHelper;

import net.minecraft.client.renderer.entity.RenderItem;

import net.minecraft.client.resources.I18n;

import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;

import org.lwjgl.opengl.GL11;



import com.imgood.textech.AdvanceDataMonitor;

import com.imgood.textech.client.PocketStackOverlayRenderer;
import com.imgood.textech.mixin.GuiScreenTooltipAccess;

import com.imgood.textech.network.packet.PacketPocketAction;



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



    private static final int PAGE_ARROW_W = 10;

    private static final int COLLAPSE_BTN_SIZE = 9;

    private static final int ARROW_BTN_FILL_ENABLED = 0x4A7098E0;

    private static final int ARROW_BTN_FILL_DISABLED = 0x28709880;

    private static final int ARROW_BTN_RIM_ENABLED = 0x5588AAFF;

    private static final int ARROW_BTN_RIM_DISABLED = 0x4088AA60;

    private static final int CHROME_BTN_FILL = 0xFF0C121C;

    private static final int CHROME_BTN_RIM = 0xFF5588CC;

    private static final int HEADER_ARROW_GAP = 3;
    private static final int HEADER_EDGE_PAD = 4;
    private static final int COLLAPSE_PAGE_GAP = 4;
    /** Semi-transparent backdrop behind overlay title / header labels. */
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

        int pw = PocketPortalGuiRenderer.overlayPanelWidth();

        int ph = PocketPortalGuiRenderer.overlayPanelHeight();

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
     * Hovered pocket-slot tooltip via {@link net.minecraft.client.gui.inventory.GuiContainer#renderToolTip}
     * so GTNH / Forge / community tooltip hooks run on the same path as normal container slots.
     */
    public void drawHoveredItemTooltip(int mouseX, int mouseY) {
        if (host == null || PocketClientCache.isCollapsed() || dragging) return;
        int slot = getSlotAt(mouseX, mouseY);
        if (slot < 0) return;
        ItemStack stack = PocketClientCache.getStack(PocketClientCache.getCurrentPage(), slot);
        if (stack == null) return;
        ((GuiScreenTooltipAccess) (Object) host).adm$renderToolTip(stack, mouseX, mouseY);
    }



    public void setDragging(boolean dragging) {

        this.dragging = dragging;

    }



    public int getPanelWidth() {

        return PocketPortalGuiRenderer.overlayPanelWidth();

    }



    public int getPanelHeight() {

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



        HeaderLayout header = computeHeaderLayout(mc);
        drawLeftAlignedAnimatedGradientTitle(
            mc,
            panelX + PocketPortalGuiRenderer.RIFT_OVERSHOOT,
            header.titleY,
            I18n.format("adm.title.pocketOverlay"));

        drawCollapseButton(getCollapseButtonX(), getCollapseButtonY());



        PocketPortalGuiRenderer.drawOverlaySlotGrid(gridX, gridY, slotsPerPage, overlayPhase);



        RenderHelper.enableGUIStandardItemLighting();

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glEnable(GL11.GL_ALPHA_TEST);

        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);

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

                PocketStackOverlayRenderer.renderSlotItem(renderItem, mc.fontRenderer, mc.getTextureManager(), stack, x, y);

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

        GL11.glEnable(GL11.GL_ALPHA_TEST);

        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        boolean canPrev = PocketClientCache.getCurrentPage() > 0;
        boolean canNext = PocketClientCache.getCurrentPage() < PocketClientCache.getPageCount() - 1;
        drawHeaderBackdrop(header, mc);
        drawArrowButton(header.leftArrowX, header.lineY, true, canPrev);
        drawArrowButton(header.rightArrowX, header.lineY, false, canNext);
        mc.fontRenderer.drawStringWithShadow(header.pageText, header.pageTextX, header.pageTextY, 0xAACCFF);

        drawDragHint(getCurrentMouseX(), getCurrentMouseY());

        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glPopMatrix();

    }



    private static void drawLeftAlignedAnimatedGradientTitle(Minecraft mc, int x, int y, String text) {
        if (text == null || text.isEmpty()) return;
        int totalWidth = mc.fontRenderer.getStringWidth(text);
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

    private void drawHeaderBackdrop(HeaderLayout header, Minecraft mc) {
        int lineH = mc.fontRenderer.FONT_HEIGHT;
        drawLabelBackdrop(
            header.leftArrowX - LABEL_BACKDROP_PAD_X,
            header.lineY - LABEL_BACKDROP_PAD_Y,
            header.rightArrowX + PAGE_ARROW_W + LABEL_BACKDROP_PAD_X,
            header.lineY + lineH + LABEL_BACKDROP_PAD_Y);
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



    private HeaderLayout computeHeaderLayout(Minecraft mc) {
        HeaderLayout layout = new HeaderLayout();
        int lineH = mc.fontRenderer.FONT_HEIGHT;
        layout.lineY = panelY + (PocketPortalGuiRenderer.OVERLAY_TITLE_HEIGHT - lineH) / 2;
        layout.titleY = layout.lineY;
        layout.pageText = I18n.format(
            "adm.label.pocket.pageFooter",
            PocketClientCache.getCurrentPage() + 1,
            PocketClientCache.getPageCount());
        int pageTextWidth = mc.fontRenderer.getStringWidth(layout.pageText);
        int collapseX = getCollapseButtonX();
        int groupRight = collapseX - COLLAPSE_PAGE_GAP;
        layout.rightArrowX = groupRight - PAGE_ARROW_W;
        layout.pageTextX = layout.rightArrowX - HEADER_ARROW_GAP - pageTextWidth;
        layout.leftArrowX = layout.pageTextX - HEADER_ARROW_GAP - PAGE_ARROW_W;
        layout.pageTextY = layout.lineY;
        return layout;
    }

    private int getCollapseButtonX() {
        return panelX + PocketPortalGuiRenderer.overlayPanelWidth() - COLLAPSE_BTN_SIZE - HEADER_EDGE_PAD;
    }

    private int getCollapseButtonY() {
        return panelY + (PocketPortalGuiRenderer.OVERLAY_TITLE_HEIGHT - COLLAPSE_BTN_SIZE) / 2;
    }

    private boolean hitsCollapseButton(int mouseX, int mouseY) {
        return hitsRect(mouseX, mouseY, getCollapseButtonX(), getCollapseButtonY(), COLLAPSE_BTN_SIZE, COLLAPSE_BTN_SIZE);
    }

    private static final class HeaderLayout {
        int lineY;
        int titleY;
        int leftArrowX;
        int rightArrowX;
        int pageTextX;
        int pageTextY;
        String pageText;
    }



    private void drawCollapsed() {

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glEnable(GL11.GL_BLEND);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glDisable(GL11.GL_ALPHA_TEST);

        drawExpandButton(getCollapseButtonX(), getCollapseButtonY());

        GL11.glEnable(GL11.GL_DEPTH_TEST);

    }



    private void drawCollapseButton(int x, int y) {

        drawChromeButton(x, y, "_");

    }



    private void drawExpandButton(int x, int y) {

        drawAnimatedChromeButton(x, y, "+");

    }



    private void drawChromeButton(int x, int y, String glyph) {

        Minecraft mc = Minecraft.getMinecraft();

        Gui.drawRect(x, y, x + COLLAPSE_BTN_SIZE, y + COLLAPSE_BTN_SIZE, CHROME_BTN_RIM);

        Gui.drawRect(x + 1, y + 1, x + COLLAPSE_BTN_SIZE - 1, y + COLLAPSE_BTN_SIZE - 1, CHROME_BTN_FILL);

        drawChromeGlyph(mc, x, y, glyph);

    }



    /** Collapsed expand chip: portal-toned chaotic blue-violet fill synced with overlay rift phase. */

    private void drawAnimatedChromeButton(int x, int y, String glyph) {

        Minecraft mc = Minecraft.getMinecraft();

        int size = COLLAPSE_BTN_SIZE;

        double phase = PocketPortalGuiRenderer.overlayAnimationPhase();



        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glEnable(GL11.GL_BLEND);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);



        float pulse = (float) ((Math.sin(phase) + 1.0) * 0.5);

        float pulse2 = (float) ((Math.sin(phase * 1.35 + 1.2) + 1.0) * 0.5);

        int rimAlpha = (int) (0x88 + pulse * 0x77);

        int topColor = portalGradientArgb(pulse, rimAlpha);

        int bottomColor = portalGradientArgb(1.0f - pulse, rimAlpha);



        Gui.drawRect(x, y, x + size, y + 1, topColor);

        Gui.drawRect(x, y + size - 1, x + size, y + size, bottomColor);

        Gui.drawRect(x, y, x + 1, y + size, topColor);

        Gui.drawRect(x + size - 1, y, x + size, y + size, bottomColor);



        int innerX = x + 1;

        int innerY = y + 1;

        int innerW = size - 2;

        int innerH = size - 2;

        for (int row = 0; row < innerH; row++) {

            float t = innerH <= 1 ? 0.5f : row / (float) (innerH - 1);

            float wave = (float) (Math.sin(phase + t * Math.PI * 4.0)

                + Math.sin(phase * 0.85 + x * 0.3 + row * 0.55));

            float blend = wave * 0.25f + 0.5f;

            if (blend < 0.0f) blend = 0.0f;

            if (blend > 1.0f) blend = 1.0f;

            float alphaWave = (float) (0.55 + 0.35 * Math.sin(phase * 1.1 + row * 0.9 + y * 0.2));

            int fillA = (int) (alphaWave * 0xFF);

            if (fillA < 0x40) fillA = 0x40;

            Gui.drawRect(innerX, innerY + row, innerX + innerW, innerY + row + 1, portalGradientArgb(blend, fillA));

        }



        float travel = (float) ((1.0 + Math.sin(phase * 1.2)) * 0.5);

        int bandY = innerY + (int) (travel * Math.max(0, innerH - 2));

        float fade = (float) (Math.sin(travel * Math.PI) * 0.7);

        int bandA = (int) (fade * 0x90);

        if (bandA > 8) {

            Gui.drawRect(innerX, bandY, innerX + innerW, bandY + 2, portalGradientArgb(pulse2, bandA));

        }



        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);



        drawChromeGlyph(mc, x, y, glyph);

    }



    private static void drawChromeGlyph(Minecraft mc, int x, int y, String glyph) {

        int glyphW = mc.fontRenderer.getStringWidth(glyph);

        mc.fontRenderer.drawStringWithShadow(

            glyph,

            x + (COLLAPSE_BTN_SIZE - glyphW) / 2,

            y + (COLLAPSE_BTN_SIZE - mc.fontRenderer.FONT_HEIGHT) / 2,

            0xFFFFFF);

    }



    private static int portalGradientArgb(float blend, int alpha) {

        int r = blendChannel(0x55, 0xCC, blend);

        int g = blendChannel(0x88, 0x55, blend);

        int b = 0xFF;

        return (alpha << 24) | (r << 16) | (g << 8) | b;

    }



    private static int blendChannel(int from, int to, float t) {

        return (int) (from + (to - from) * t);

    }



    private void drawArrowButton(int x, int y, boolean left, boolean enabled) {

        Minecraft mc = Minecraft.getMinecraft();
        int lineH = mc.fontRenderer.FONT_HEIGHT;

        int rim = enabled ? ARROW_BTN_RIM_ENABLED : ARROW_BTN_RIM_DISABLED;

        int fill = enabled ? ARROW_BTN_FILL_ENABLED : ARROW_BTN_FILL_DISABLED;

        Gui.drawRect(x, y, x + PAGE_ARROW_W, y + lineH, rim);

        Gui.drawRect(x + 1, y + 1, x + PAGE_ARROW_W - 1, y + lineH - 1, fill);

        String glyph = left ? "<" : ">";

        int glyphW = mc.fontRenderer.getStringWidth(glyph);

        mc.fontRenderer.drawStringWithShadow(
            glyph,
            x + (PAGE_ARROW_W - glyphW) / 2,
            y + (lineH - mc.fontRenderer.FONT_HEIGHT) / 2,
            enabled ? 0xFFFFFF : 0x808080);

    }



    /**

     * 鼠标悬停在可拖动边缘一圈时显示"拖动"小提�?拖动进行中不显示�?

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

            if (hitsCollapseButton(mouseX, mouseY)) {

                PocketClientCache.setCollapsed(false);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setCollapsed(false));

                return true;

            }

            return false;

        }

        if (hitsCollapseButton(mouseX, mouseY)) {

            PocketClientCache.setCollapsed(true);
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setCollapsed(true));

            return true;

        }

        if (isInDragZone(mouseX, mouseY)) {

            dragging = true;

            dragOffsetX = mouseX - panelX;

            dragOffsetY = mouseY - panelY;

            return true;

        }

        HeaderLayout header = computeHeaderLayout(Minecraft.getMinecraft());
        int lineH = Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;
        if (hitsRect(mouseX, mouseY, header.leftArrowX, header.lineY, PAGE_ARROW_W, lineH)) {

            if (PocketClientCache.getCurrentPage() > 0) {

                PocketClientCache.setCurrentPage(PocketClientCache.getCurrentPage() - 1);

                AdvanceDataMonitor.ADMCHANEL

                    .sendToServer(PacketPocketAction.setPage(PocketClientCache.getCurrentPage()));

            }

            return true;

        }

        if (hitsRect(mouseX, mouseY, header.rightArrowX, header.lineY, PAGE_ARROW_W, lineH)) {

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

            return hitsCollapseButton(mouseX, mouseY);

        }

        return hitsRect(mouseX, mouseY, panelX, panelY, getPanelWidth(), getPanelHeight());

    }



    /**

     * Expanded overlay �?边缘一�?拖动�?整个面板减去中央 slot 网格、折叠按钮与顶部翻页控件�?

     * 折叠状态返�?false(点击用于展开)�?

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

        if (hitsCollapseButton(mouseX, mouseY)) return false;

        HeaderLayout header = computeHeaderLayout(Minecraft.getMinecraft());
        int lineH = Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;

        if (hitsRect(mouseX, mouseY, header.leftArrowX, header.lineY, PAGE_ARROW_W, lineH)) return false;

        if (hitsRect(mouseX, mouseY, header.rightArrowX, header.lineY, PAGE_ARROW_W, lineH)) return false;

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
