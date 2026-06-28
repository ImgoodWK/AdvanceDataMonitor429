package com.imgood.advancedatamonitor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.handler.PocketState;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws the Dimensional Pocket portal panel using a 9-slice atlas
 * ({@code textures/gui/pocket_portal_panel.png}). The center is mostly
 * transparent so slot outlines stay visible; the rift is drawn two cell rings larger than
 * the slot grid. Slot markers are faint outlines that do not obscure the portal fill.
 */
@SideOnly(Side.CLIENT)
public final class PocketPortalGuiRenderer {

    public static final ResourceLocation PANEL_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/pocket_portal_panel.png");

    /** Atlas size in pixels — must match pocket_portal_panel.png. */
    public static final int TEX_SIZE = 256;
    /** Non-stretching border width on each side of the 9-slice atlas. */
    public static final int BORDER = 32;
    public static final int CELL_SIZE = 18;
    /** Two slot rings beyond the grid — portal rift overshoot on each side. */
    public static final int RIFT_OVERSHOOT = CELL_SIZE * 2;
    /** Overlay title bar height above the slot grid. */
    public static final int OVERLAY_TITLE_HEIGHT = 18;
    /** RGB scale for slot/frame outlines (~30% darker). */
    private static final float FRAME_RGB_DEPTH = 0.7f;
    /** Thin slot outline — low alpha so portal background stays visible. */
    private static final int SLOT_EDGE_COLOR = deepenArgb(0x4088AAFF);
    /** Very faint slot tint (ARGB ~9% alpha). */
    private static final int SLOT_TINT_COLOR = deepenArgb(0x187098D0);
    /** Pocket slot grid origin within a {@link com.imgood.advancedatamonitor.gui.guiscreen.GuiPocketStorage} panel. */
    public static final int STORAGE_SLOT_ORIGIN_X = 8;
    public static final int STORAGE_SLOT_ORIGIN_Y = 18;

    private static final ResourceLocation CHEST_GUI_TEXTURE = new ResourceLocation(
        "minecraft",
        "textures/gui/container/generic54.png");
    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation(
        "minecraft",
        "textures/gui/container/inventory.png");
    private static final int VANILLA_CELL_U = 7;
    private static final int VANILLA_CELL_V = 83;
    /** Config GUI upper panel height (vanilla inventory.png header area). */
    private static final int CONFIG_PANEL_HEIGHT = 125;
    /** Player inventory strip top within config GUI (aligns with container slot Y 126). */
    private static final int CONFIG_PLAYER_STRIP_Y = 126;
    /** Overlay wobble/ripple intensity (1.0 = original baseline). */
    private static final float OVERLAY_RIPPLE_SCALE = 1.3f;

    private PocketPortalGuiRenderer() {}

    /**
     * Renders the portal frame stretched to {@code width} x {@code height}.
     */
    public static void drawPanel(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        int b = Math.min(BORDER, Math.min(width, height) / 2);
        drawNineSlicePanel(x, y, width, height, b);
    }

    /** Maximum pocket slots per page — panel/rift always sized for this cap. */
    public static int maxDisplaySlots() {
        return PocketState.SLOTS_PER_PAGE_CAP;
    }

    /**
     * Portal background for the slot grid: full 9-slice panel (border + center tint)
     * two cell rings larger than the grid on each side. Always uses max grid size
     * regardless of current upgrade count; only {@link #drawSlotGrid} draws individual cells.
     */
    public static void drawGridPortalRift(int gridOriginX, int gridOriginY) {
        int gridW = gridPixelWidth();
        int gridH = gridPixelHeight(maxDisplaySlots());
        int ring = RIFT_OVERSHOOT;
        int outerX = gridOriginX - ring;
        int outerY = gridOriginY - ring;
        int outerW = gridW + ring * 2;
        int outerH = gridH + ring * 2;
        int border = Math.min(ring, Math.min(outerW, outerH) / 2);
        drawNineSlicePanel(outerX, outerY, outerW, outerH, border);
    }

    /**
     * Overlay-only portal rift: wavy center UV strips plus procedural ripple rings/shimmer
     * on top of the existing atlas — no texture edits required.
     */
    public static void drawOverlayGridPortalRift(int gridOriginX, int gridOriginY) {
        int gridW = gridPixelWidth();
        int gridH = gridPixelHeight(maxDisplaySlots());
        int ring = RIFT_OVERSHOOT;
        int outerX = gridOriginX - ring;
        int outerY = gridOriginY - ring;
        int outerW = gridW + ring * 2;
        int outerH = gridH + ring * 2;
        int border = Math.min(ring, Math.min(outerW, outerH) / 2);
        double phase = animationPhase();
        drawNineSlicePanelWavy(outerX, outerY, outerW, outerH, border, phase);
        int innerX = outerX + border;
        int innerY = outerY + border;
        int innerW = outerW - border * 2;
        int innerH = outerH - border * 2;
        if (innerW > 0 && innerH > 0) {
            drawPortalRippleOverlay(innerX, innerY, innerW, innerH, phase);
        }
    }

    private static void drawNineSlicePanel(int x, int y, int width, int height, int borderPx) {
        if (width <= 0 || height <= 0 || borderPx <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(PANEL_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int b = borderPx;
        int midW = width - b * 2;
        int midH = height - b * 2;
        int srcMid = TEX_SIZE - BORDER * 2;

        blit(x, y, b, b, 0, 0, BORDER, BORDER);
        blit(x + width - b, y, b, b, TEX_SIZE - BORDER, 0, BORDER, BORDER);
        blit(x, y + height - b, b, b, 0, TEX_SIZE - BORDER, BORDER, BORDER);
        blit(x + width - b, y + height - b, b, b, TEX_SIZE - BORDER, TEX_SIZE - BORDER, BORDER, BORDER);

        if (midW > 0) {
            blit(x + b, y, midW, b, BORDER, 0, srcMid, BORDER);
            blit(x + b, y + height - b, midW, b, BORDER, TEX_SIZE - BORDER, srcMid, BORDER);
        }
        if (midH > 0) {
            blit(x, y + b, b, midH, 0, BORDER, BORDER, srcMid);
            blit(x + width - b, y + b, b, midH, TEX_SIZE - BORDER, BORDER, BORDER, srcMid);
        }
        if (midW > 0 && midH > 0) {
            blit(x + b, y + b, midW, midH, BORDER, BORDER, srcMid, srcMid);
        }

        GL11.glDisable(GL11.GL_BLEND);
    }

    /** Nine-slice panel with horizontal UV strip offset on the center — subtle liquid wobble. */
    private static void drawNineSlicePanelWavy(int x, int y, int width, int height, int borderPx, double phase) {
        if (width <= 0 || height <= 0 || borderPx <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(PANEL_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int b = borderPx;
        int midW = width - b * 2;
        int midH = height - b * 2;
        int srcMid = TEX_SIZE - BORDER * 2;

        blit(x, y, b, b, 0, 0, BORDER, BORDER);
        blit(x + width - b, y, b, b, TEX_SIZE - BORDER, 0, BORDER, BORDER);
        blit(x, y + height - b, b, b, 0, TEX_SIZE - BORDER, BORDER, BORDER);
        blit(x + width - b, y + height - b, b, b, TEX_SIZE - BORDER, TEX_SIZE - BORDER, BORDER, BORDER);

        if (midW > 0) {
            blit(x + b, y, midW, b, BORDER, 0, srcMid, BORDER);
            blit(x + b, y + height - b, midW, b, BORDER, TEX_SIZE - BORDER, srcMid, BORDER);
        }
        if (midH > 0) {
            blit(x, y + b, b, midH, 0, BORDER, BORDER, srcMid);
            blit(x + width - b, y + b, b, midH, TEX_SIZE - BORDER, BORDER, BORDER, srcMid);
        }
        if (midW > 0 && midH > 0) {
            blitWavyCenter(x + b, y + b, midW, midH, BORDER, BORDER, srcMid, srcMid, phase);
        }

        GL11.glDisable(GL11.GL_BLEND);
    }

    /** Expanding ripple rings + drifting shimmer bands — pure color, no extra textures. */
    private static void drawPortalRippleOverlay(int x, int y, int w, int h, double phase) {
        if (w <= 2 || h <= 2) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float cx = x + w * 0.5f;
        float cy = y + h * 0.52f;
        float maxRadius = Math.min(w, h) * 0.62f * OVERLAY_RIPPLE_SCALE;
        double time = Minecraft.getSystemTime() / 1000.0;

        for (int ring = 0; ring < 4; ring++) {
            double travel = (time * 0.55 * OVERLAY_RIPPLE_SCALE + ring * 0.28) % 1.0;
            float radius = (float) (travel * maxRadius);
            float fade = (float) (Math.sin(travel * Math.PI) * 0.42 * OVERLAY_RIPPLE_SCALE);
            if (fade <= 0.02f) continue;
            int alpha = Math.min(255, (int) (fade * 0x55 * OVERLAY_RIPPLE_SCALE));
            float blend = (float) ((Math.sin(phase + ring * 1.1) + 1.0) * 0.5);
            drawRippleRing(cx, cy, radius, gradientArgb(blend, alpha));
        }

        int bandCount = 6;
        for (int band = 0; band < bandCount; band++) {
            double travel = (time * 0.35 * OVERLAY_RIPPLE_SCALE + band * (1.0 / bandCount)) % 1.0;
            int by = y + (int) (travel * (h - 3));
            float fade = (float) (Math.sin(travel * Math.PI) * 0.55 * OVERLAY_RIPPLE_SCALE);
            int alpha = Math.min(255, (int) (fade * 0x20 * OVERLAY_RIPPLE_SCALE));
            if (alpha < 4) continue;
            float waveShift = (float) (Math.sin(phase * 1.4 + band * 0.9) * 3.0 * OVERLAY_RIPPLE_SCALE);
            int color = gradientArgb((float) band / bandCount, alpha);
            Gui.drawRect(x + 6 + (int) waveShift, by, x + w - 6 + (int) waveShift, by + 2, color);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void blitWavyCenter(
        int x, int y, int w, int h, int u, int v, int sw, int sh, double phase) {
        int strips = Math.max(12, h / 3);
        for (int i = 0; i < strips; i++) {
            float t = i / (float) strips;
            float wave = (float) (Math.sin(phase * 1.25 * OVERLAY_RIPPLE_SCALE + t * Math.PI * 4.0 * OVERLAY_RIPPLE_SCALE)
                * 2.0 * OVERLAY_RIPPLE_SCALE);
            int sy = y + (int) (t * h);
            int nextY = (i + 1 == strips) ? y + h : y + (int) ((i + 1) / (float) strips * h);
            int shStrip = Math.max(1, nextY - sy);
            int sv = v + (int) (t * sh);
            int nextV = (i + 1 == strips) ? v + sh : v + (int) ((i + 1) / (float) strips * sh);
            int ssh = Math.max(1, nextV - sv);
            blit(x + (int) wave, sy, w, shStrip, u, sv, sw, ssh);
        }
    }

    private static void drawRippleRing(float cx, float cy, float radius, int color) {
        if (radius < 1.5f) return;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a <= 0.01f) return;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float rx = radius;
        float ry = radius * 0.72f;
        int segments = 48;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINE_LOOP);
        tessellator.setColorRGBA_F(r, g, b, a);
        for (int i = 0; i < segments; i++) {
            double ang = i * Math.PI * 2.0 / segments;
            tessellator.addVertex(cx + Math.cos(ang) * rx, cy + Math.sin(ang) * ry, 0.0);
        }
        tessellator.draw();
    }

    /**
     * Faint blue-violet slot markers — thin outline + minimal tint, portal texture visible underneath.
     */
    public static void drawSlotGrid(int originX, int originY, int slotCount) {
        if (slotCount <= 0) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int cols = 9;
        for (int i = 0; i < slotCount; i++) {
            int row = i / cols;
            int col = i % cols;
            int sx = originX + col * CELL_SIZE;
            int sy = originY + row * CELL_SIZE;
            int ex = sx + CELL_SIZE;
            int ey = sy + CELL_SIZE;
            Gui.drawRect(sx + 1, sy + 1, ex - 1, ey - 1, SLOT_TINT_COLOR);
            Gui.drawRect(sx, sy, ex, sy + 1, SLOT_EDGE_COLOR);
            Gui.drawRect(sx, ey - 1, ex, ey, SLOT_EDGE_COLOR);
            Gui.drawRect(sx, sy, sx + 1, ey, SLOT_EDGE_COLOR);
            Gui.drawRect(ex - 1, sy, ex, ey, SLOT_EDGE_COLOR);
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** X of the slot grid within an overlay panel anchored at {@code panelX}. */
    public static int overlayGridOriginX(int panelX) {
        return panelX + RIFT_OVERSHOOT;
    }

    /** Y of the slot grid within an overlay panel anchored at {@code panelY}. */
    public static int overlayGridOriginY(int panelY) {
        return panelY + OVERLAY_TITLE_HEIGHT;
    }

    public static int gridPixelWidth() {
        return 9 * CELL_SIZE;
    }

    public static int gridPixelHeight(int slotCount) {
        return ((slotCount + 8) / 9) * CELL_SIZE;
    }

    /** Overlay panel width: rift overshoot + grid + rift overshoot. */
    public static int overlayPanelWidth() {
        return RIFT_OVERSHOOT * 2 + gridPixelWidth();
    }

    /** Overlay panel height at max grid size (title + 7-row grid + bottom rift band). */
    public static int overlayPanelHeight() {
        return OVERLAY_TITLE_HEIGHT + gridPixelHeight(maxDisplaySlots()) + RIFT_OVERSHOOT;
    }

    /** Player inventory strip Y — fixed below the max-size pocket grid. */
    public static int storagePlayerInventoryOriginY() {
        int rows = (maxDisplaySlots() + 8) / 9;
        return STORAGE_SLOT_ORIGIN_Y + rows * CELL_SIZE + 14;
    }

    /** Vanilla single inventory slot cell from {@code inventory.png}. */
    public static void drawVanillaSlotCell(int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(INVENTORY_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.ingameGUI.drawTexturedModalRect(x, y, VANILLA_CELL_U, VANILLA_CELL_V, CELL_SIZE, CELL_SIZE);
    }

    /** Vanilla upgrade-config GUI: inventory header + player inventory strip. */
    public static void drawVanillaConfigBackground(int guiLeft, int guiTop) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(INVENTORY_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.ingameGUI.drawTexturedModalRect(guiLeft, guiTop, 0, 0, 176, CONFIG_PANEL_HEIGHT);
        drawVanillaPlayerInventoryStrip(guiLeft, guiTop + CONFIG_PLAYER_STRIP_Y);
    }

    /** Vanilla player inventory + hotbar strip ({@code generic54.png} v=126). */
    public static void drawVanillaPlayerInventoryStrip(int guiLeft, int stripTop) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(CHEST_GUI_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.ingameGUI.drawTexturedModalRect(guiLeft, stripTop, 0, 126, 176, 96);
    }

    /**
     * Y offset within {@link com.imgood.advancedatamonitor.gui.guiscreen.GuiPocketStorage} where the
     * vanilla chest-style player-inventory strip ({@code generic54.png} v=126) should begin so
     * slot backgrounds align with {@link com.imgood.advancedatamonitor.gui.container.ContainerPocketStorage}.
     */
    public static int storagePlayerInventorySplitY() {
        return storagePlayerInventoryOriginY();
    }

    public static void drawStorageRift(int guiLeft, int guiTop, int unlockedSlots) {
        int gridX = guiLeft + STORAGE_SLOT_ORIGIN_X;
        int gridY = guiTop + STORAGE_SLOT_ORIGIN_Y;
        drawGridPortalRift(gridX, gridY);
        drawSlotGrid(gridX, gridY, unlockedSlots);
    }

    /** Vanilla player inventory + hotbar strip for the storage GUI lower half. */
    public static void drawVanillaPlayerInventoryBackground(int guiLeft, int guiTop) {
        drawStoragePlayerInventoryGradient(guiLeft, guiTop);
    }

    /** Player hotbar row Y within storage GUI (container-relative). */
    public static int storagePlayerHotbarOriginY() {
        return storagePlayerInventoryOriginY() + 3 * CELL_SIZE + 4;
    }

    /**
     * Storage GUI player inventory + hotbar: animated blue-violet gradient frame and slot cells.
     */
    public static void drawStoragePlayerInventoryGradient(int guiLeft, int guiTop) {
        int slotX = guiLeft + STORAGE_SLOT_ORIGIN_X;
        int playerY = guiTop + storagePlayerInventoryOriginY();
        int hotbarY = guiTop + storagePlayerHotbarOriginY();
        int gridW = gridPixelWidth();
        int framePad = 3;
        int glowPad = 2;
        int frameX = slotX - framePad;
        int frameY = playerY - framePad;
        int frameW = gridW + framePad * 2;
        int frameH = hotbarY - playerY + CELL_SIZE + framePad * 2;
        double phase = animationPhase();

        drawAnimatedGradientFrame(frameX - glowPad, frameY - glowPad, frameW + glowPad * 2, frameH + glowPad * 2, phase);
        drawAnimatedGradientSlotGrid(slotX, playerY, 9, 3, phase, 0);
        drawAnimatedGradientSlotGrid(slotX, hotbarY, 9, 1, phase, 27);
    }

    private static double animationPhase() {
        return (Minecraft.getSystemTime() % 4000L) / 4000.0 * Math.PI * 2.0;
    }

    private static int gradientArgb(float blend, int alpha) {
        int r = blendChannel(scaleRgbChannel(0x55), scaleRgbChannel(0xCC), blend);
        int g = blendChannel(scaleRgbChannel(0x88), scaleRgbChannel(0x55), blend);
        int b = scaleRgbChannel(0xFF);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static int scaleRgbChannel(int channel) {
        return (int) (channel * FRAME_RGB_DEPTH);
    }

    private static int deepenArgb(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = scaleRgbChannel((argb >> 16) & 0xFF);
        int g = scaleRgbChannel((argb >> 8) & 0xFF);
        int b = scaleRgbChannel(argb & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendChannel(int from, int to, float t) {
        return (int) (from + (to - from) * t);
    }

    /** Pulsing outer glow + shifting blue-violet border. */
    private static void drawAnimatedGradientFrame(int x, int y, int w, int h, double phase) {
        if (w <= 0 || h <= 0) return;
        float pulse = (float) ((Math.sin(phase) + 1.0) * 0.5);
        float pulse2 = (float) ((Math.sin(phase * 1.35 + 1.2) + 1.0) * 0.5);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int glowA = (int) (0x14 + pulse * 0x28);
        int glow = gradientArgb(pulse2, glowA);
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, glow);

        int outerA = (int) (0x88 + pulse * 0x40);
        int innerA = (int) (0x50 + pulse2 * 0x30);
        int topColor = gradientArgb(pulse, outerA);
        int bottomColor = gradientArgb(1.0f - pulse, outerA);
        int midBlend = (topColor & 0x00FFFFFF) | (innerA << 24);

        Gui.drawRect(x, y, x + w, y + 1, topColor);
        Gui.drawRect(x, y + h - 1, x + w, y + h, bottomColor);
        Gui.drawRect(x, y, x + 1, y + h, topColor);
        Gui.drawRect(x + w - 1, y, x + w, y + h, bottomColor);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + 2, midBlend);
        Gui.drawRect(x + 1, y + h - 2, x + w - 1, y + h - 1, midBlend);

        int fillA = (int) (0x08 + pulse * 0x10);
        Gui.drawRect(x + 2, y + 2, x + w - 2, y + h - 2, gradientArgb(pulse2, fillA));

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void drawAnimatedGradientSlotGrid(
        int originX, int originY, int cols, int rows, double phase, int indexOffset) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = indexOffset + row * cols + col;
                double wave = Math.sin(phase + idx * 0.42);
                float blend = (float) ((wave + 1.0) * 0.5);
                int sx = originX + col * CELL_SIZE;
                int sy = originY + row * CELL_SIZE;
                int ex = sx + CELL_SIZE;
                int ey = sy + CELL_SIZE;
                int rim = gradientArgb(blend, 0x70);
                int fill = gradientArgb(blend, 0x28);
                int shine = gradientArgb(1.0f - blend, 0x18);
                Gui.drawRect(sx + 1, sy + 1, ex - 1, ey - 1, fill);
                Gui.drawRect(sx + 1, sy + 1, ex - 1, sy + 2, shine);
                Gui.drawRect(sx, sy, ex, sy + 1, rim);
                Gui.drawRect(sx, ey - 1, ex, ey, rim);
                Gui.drawRect(sx, sy, sx + 1, ey, rim);
                Gui.drawRect(ex - 1, sy, ex, ey, rim);
            }
        }
    }

    private static void blit(int x, int y, int w, int h, int u, int v, int sw, int sh) {
        float tex = (float) TEX_SIZE;
        float u0 = u / tex;
        float v0 = v / tex;
        float u1 = (u + sw) / tex;
        float v1 = (v + sh) / tex;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + h, 0, u0, v1);
        tessellator.addVertexWithUV(x + w, y + h, 0, u1, v1);
        tessellator.addVertexWithUV(x + w, y, 0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0, u0, v0);
        tessellator.draw();
    }
}
