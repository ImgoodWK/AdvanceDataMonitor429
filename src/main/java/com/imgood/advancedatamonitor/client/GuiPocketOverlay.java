package com.imgood.advancedatamonitor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.network.packet.PacketPocketAction;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Renders the draggable, paged extra-inventory overlay on top of the currently
 * open GuiContainer. Slot backgrounds are cut from the vanilla player inventory
 * texture (minecraft:textures/gui/container/inventory.png) so resource packs
 * that retexture the vanilla inventory automatically retexture the overlay too.
 *
 * Mouse interactions:
 * - Dragging the title bar moves the window; position is normalized 0..1 and
 * synced to the server (stored in the player-bound PocketState).
 * - Prev/Next page arrows and mouse-wheel (only while the cursor is inside the
 * overlay rectangle) switch pages.
 * - Corner button toggles collapse; collapsed state shows just a small icon.
 */
@SideOnly(Side.CLIENT)
public class GuiPocketOverlay {

    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation(
        "minecraft",
        "textures/gui/container/inventory.png");
    // Vanilla player-inventory main grid UV: 27-slot region at (7, 83), 18x18 per cell.
    private static final int CELL_U = 7;
    private static final int CELL_V = 83;
    private static final int CELL_SIZE = 18;

    private final PocketOverlayHandler handler;
    private GuiContainer host;
    private int panelX;
    private int panelY;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    private static final int PAGE_ARROW_W = 12;
    private static final int PAGE_ARROW_H = 16;
    private static final int COLLAPSE_BTN_SIZE = 12;

    public GuiPocketOverlay(PocketOverlayHandler handler, GuiContainer host) {
        this.handler = handler;
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

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    private int getPanelWidth() {
        int cols = 9;
        return 8 + cols * CELL_SIZE + 8;
    }

    private int getPanelHeight() {
        if (PocketClientCache.isCollapsed()) return COLLAPSE_BTN_SIZE + 4;
        int rows = (PocketClientCache.getSlotsPerPage() + 8) / 9;
        return 18 + rows * CELL_SIZE + 18; // title bar + grid + arrow row
    }

    public void draw(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        updatePositionFromMouse();

        if (PocketClientCache.isCollapsed()) {
            drawCollapsed();
            return;
        }

        int pw = getPanelWidth();
        int ph = getPanelHeight();

        // Semi-transparent panel background
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Gui.drawRect(panelX, panelY, panelX + pw, panelY + ph, 0xD0101830);
        Gui.drawRect(panelX, panelY, panelX + pw, panelY + 1, 0xFF3A5A8A);
        Gui.drawRect(panelX, panelY + ph - 1, panelX + pw, panelY + ph, 0xFF3A5A8A);
        Gui.drawRect(panelX, panelY, panelX + 1, panelY + ph, 0xFF3A5A8A);
        Gui.drawRect(panelX + pw - 1, panelY, panelX + pw, panelY + ph, 0xFF3A5A8A);
        GL11.glDisable(GL11.GL_BLEND);

        // Title bar text + drag hint
        String title = I18n.format("adm.title.pocketConfig");
        mc.fontRenderer.drawStringWithShadow(title, panelX + 6, panelY + 4, 0xFFFFFF);
        String pageText = String.format(
            I18n.format("adm.label.pocket.currentPage"),
            PocketClientCache.getCurrentPage() + 1,
            PocketClientCache.getPageCount());
        mc.fontRenderer.drawStringWithShadow(
            pageText,
            panelX + pw - mc.fontRenderer.getStringWidth(pageText) - COLLAPSE_BTN_SIZE - 6,
            panelY + 4,
            0xAACCFF);

        // Collapse button (top-right corner)
        drawCollapseButton(panelX + pw - COLLAPSE_BTN_SIZE - 2, panelY + 2);

        // Slot grid using vanilla inventory.png cell texture
        int slotsPerPage = PocketClientCache.getSlotsPerPage();
        int cols = 9;
        int gridX = panelX + 6;
        int gridY = panelY + 18;
        mc.getTextureManager()
            .bindTexture(INVENTORY_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        for (int i = 0; i < slotsPerPage; i++) {
            int row = i / cols;
            int col = i % cols;
            int x = gridX + col * CELL_SIZE;
            int y = gridY + row * CELL_SIZE;
            mc.ingameGUI.drawTexturedModalRect(x, y, CELL_U, CELL_V, CELL_SIZE, CELL_SIZE);
        }

        // Draw item stacks
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderItem renderItem = new RenderItem();
        int page = PocketClientCache.getCurrentPage();
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
        }
        RenderHelper.disableStandardItemLighting();

        // Page arrows at the bottom
        int arrowY = gridY + ((slotsPerPage + 8) / 9) * CELL_SIZE + 2;
        drawArrowButton(panelX + 6, arrowY, true, PocketClientCache.getCurrentPage() > 0);
        drawArrowButton(
            panelX + pw - 6 - PAGE_ARROW_W,
            arrowY,
            false,
            PocketClientCache.getCurrentPage() < PocketClientCache.getPageCount() - 1);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void drawCollapsed() {
        int pw = COLLAPSE_BTN_SIZE + 4;
        int ph = COLLAPSE_BTN_SIZE + 4;
        Gui.drawRect(panelX, panelY, panelX + pw, panelY + ph, 0xD0101830);
        Gui.drawRect(panelX, panelY, panelX + pw, panelY + 1, 0xFF3A5A8A);
        Gui.drawRect(panelX, panelY + ph - 1, panelX + pw, panelY + ph, 0xFF3A5A8A);
        Gui.drawRect(panelX, panelY, panelX + 1, panelY + ph, 0xFF3A5A8A);
        Gui.drawRect(panelX + pw - 1, panelY, panelX + pw, panelY + ph, 0xFF3A5A8A);
        // Expand icon (a small "+")
        Minecraft mc = Minecraft.getMinecraft();
        mc.fontRenderer.drawStringWithShadow("+", panelX + 4, panelY + 3, 0xFFFFFF);
    }

    private void drawCollapseButton(int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(x, y, x + COLLAPSE_BTN_SIZE, y + COLLAPSE_BTN_SIZE, 0x80202040);
        mc.fontRenderer.drawStringWithShadow("_", x + 3, y + 1, 0xFFFFFF);
    }

    private void drawArrowButton(int x, int y, boolean left, boolean enabled) {
        int color = enabled ? 0x80204080 : 0x40202020;
        Gui.drawRect(x, y, x + PAGE_ARROW_W, y + PAGE_ARROW_H, color);
        Minecraft mc = Minecraft.getMinecraft();
        String glyph = left ? "<" : ">";
        mc.fontRenderer.drawStringWithShadow(glyph, x + 3, y + 4, enabled ? 0xFFFFFF : 0x808080);
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
        // Sync normalized position to server (throttling is implicit — server treats each as latest)
        float nx = (float) panelX / Math.max(1, mc.currentScreen.width - getPanelWidth());
        float ny = (float) panelY / Math.max(1, mc.currentScreen.height - getPanelHeight());
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            PacketPocketAction.setWindowPos(Math.max(0f, Math.min(1f, nx)), Math.max(0f, Math.min(1f, ny))));
    }

    /**
     * Called by the host GuiContainer's mouse handlers (we hook via Forge
     * GuiOverlayEvent hooks in PocketOverlayHandler if needed; for now this is
     * invoked from a custom input bridge).
     */
    public boolean onMouseClicked(int mouseX, int mouseY, int button) {
        if (PocketClientCache.isCollapsed()) {
            if (hitsRect(mouseX, mouseY, panelX, panelY, COLLAPSE_BTN_SIZE + 4, COLLAPSE_BTN_SIZE + 4)) {
                AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setCollapsed(false));
                PocketClientCache.setCurrentPage(PocketClientCache.getCurrentPage());
                return true;
            }
            return false;
        }
        int pw = getPanelWidth();
        int ph = getPanelHeight();
        // Collapse button
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
        // Title bar drag
        if (hitsRect(mouseX, mouseY, panelX, panelY, pw, 16)) {
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return true;
        }
        // Prev / Next arrows
        int slotsPerPage = PocketClientCache.getSlotsPerPage();
        int gridY = panelY + 18;
        int arrowY = gridY + ((slotsPerPage + 8) / 9) * CELL_SIZE + 2;
        if (hitsRect(mouseX, mouseY, panelX + 6, arrowY, PAGE_ARROW_W, PAGE_ARROW_H)) {
            if (PocketClientCache.getCurrentPage() > 0) {
                PocketClientCache.setCurrentPage(PocketClientCache.getCurrentPage() - 1);
                AdvanceDataMonitor.ADMCHANEL
                    .sendToServer(PacketPocketAction.setPage(PocketClientCache.getCurrentPage()));
            }
            return true;
        }
        if (hitsRect(mouseX, mouseY, panelX + pw - 6 - PAGE_ARROW_W, arrowY, PAGE_ARROW_W, PAGE_ARROW_H)) {
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

    /**
     * Mouse wheel handling — only consume the wheel while the cursor is inside
     * the overlay rectangle. Returns true if consumed.
     */
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
            return hitsRect(mouseX, mouseY, panelX, panelY, COLLAPSE_BTN_SIZE + 4, COLLAPSE_BTN_SIZE + 4);
        }
        return hitsRect(mouseX, mouseY, panelX, panelY, getPanelWidth(), getPanelHeight());
    }

    private static boolean hitsRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
