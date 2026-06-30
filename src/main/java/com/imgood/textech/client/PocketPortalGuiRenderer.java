package com.imgood.textech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.PocketState;

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

    /** Atlas size in pixels —must match pocket_portal_panel.png. */
    public static final int TEX_SIZE = 256;
    /** Non-stretching border width on each side of the 9-slice atlas. */
    public static final int BORDER = 32;
    public static final int CELL_SIZE = 18;
    /** Two slot rings beyond the grid —portal rift overshoot on each side. */
    public static final int RIFT_OVERSHOOT = CELL_SIZE * 2;
    /** Overlay title bar height above the slot grid. */
    public static final int OVERLAY_TITLE_HEIGHT = 18;
    /** RGB scale for slot/frame outlines (~30% darker). */
    private static final float FRAME_RGB_DEPTH = 0.7f;
    /** Thin slot outline —low alpha so portal background stays visible. */
    private static final int SLOT_EDGE_COLOR = deepenArgb(0x4088AAFF);
    /** Very faint slot tint (ARGB ~9% alpha). */
    private static final int SLOT_TINT_COLOR = deepenArgb(0x187098D0);
    /** Minimum along-segment alpha at line ends (high transparency); center uses full sampled alpha. */
    private static final float OVERLAY_GRID_LINE_END_ALPHA = 0.10f;
    private static final float OVERLAY_GRID_LINE_UNDERLAY_HALF = 2.75f;
    private static final float OVERLAY_GRID_LINE_GLOW_HALF = 2.0f;
    private static final float OVERLAY_GRID_LINE_CORE_HALF = 0.85f;
    private static final int OVERLAY_GRID_LINE_ALPHA_UNDERLAY = 0xD0;
    private static final int OVERLAY_GRID_LINE_ALPHA_GLOW = 0x88;
    private static final int OVERLAY_GRID_LINE_ALPHA_CORE = 0xF0;
    /** Pocket slot grid origin within a {@link com.imgood.textech.gui.guiscreen.GuiPocketStorage} panel. */
    public static final int STORAGE_SLOT_ORIGIN_X = 8;
    public static final int STORAGE_SLOT_ORIGIN_Y = 18;
    /** Extra gap between pocket grid and player inventory (1.5 slot rows). */
    public static final int STORAGE_PLAYER_INV_EXTRA_Y = CELL_SIZE + CELL_SIZE / 2;
    public static final int STORAGE_PAGE_ARROW_W = 10;
    public static final int STORAGE_CONFIG_BTN_W = 40;
    public static final int STORAGE_CONFIG_BTN_H = 11;
    public static final int CONFIG_UPGRADE_ORIGIN_X = 18;
    /** One {@link #CONFIG_LINE_HEIGHT} below legacy Y=22. */
    public static final int CONFIG_UPGRADE_ORIGIN_Y = 31;
    /** One line below legacy row-2 Y=54. */
    public static final int CONFIG_UPGRADE_ROW2_Y = 63;
    public static final int CONFIG_UPGRADE_COL_STEP = 22;
    public static final int CONFIG_LINE_HEIGHT = 9;
    public static final int CONFIG_TOGGLE_BTN_W = 24;
    public static final int CONFIG_TOGGLE_BTN_H = 11;
    public static final int CONFIG_COLLAPSE_BTN_X = 128;
    /** Two lines below legacy Y=18. */
    public static final int CONFIG_COLLAPSE_BTN_Y = 36;
    /** Back arrow in upgrade config GUI header (returns to storage GUI). */
    public static final int CONFIG_BACK_BTN_X = 6;
    public static final int CONFIG_BACK_BTN_Y = 6;
    public static final int CONFIG_PLAYER_INV_Y = 126;
    private static final int STORAGE_HEADER_ARROW_GAP = 3;
    private static final int STORAGE_HEADER_EDGE_PAD = 8;
    private static final int LABEL_BACKDROP = 0xC0101828;
    private static final int LABEL_BACKDROP_PAD_X = 4;
    private static final int LABEL_BACKDROP_PAD_Y = 2;
    private static final int PAGE_ARROW_FILL_ENABLED = 0x4A7098E0;
    private static final int PAGE_ARROW_FILL_DISABLED = 0x28709880;
    private static final int PAGE_ARROW_RIM_ENABLED = 0x5588AAFF;
    private static final int PAGE_ARROW_RIM_DISABLED = 0x4088AA60;

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
    /** Shared overlay animation period —background rift and slot grid lines use the same phase. */
    public static final long OVERLAY_ANIM_PERIOD_MS = 8000L;

    private PocketPortalGuiRenderer() {}

    /**
     * Renders the portal frame stretched to {@code width} x {@code height}.
     */
    public static void drawPanel(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        int b = Math.min(BORDER, Math.min(width, height) / 2);
        drawNineSlicePanel(x, y, width, height, b);
    }

    /** Maximum pocket slots per page —panel/rift always sized for this cap. */
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
     * on top of the existing atlas —no texture edits required.
     */
    public static void drawOverlayGridPortalRift(int gridOriginX, int gridOriginY) {
        drawOverlayGridPortalRift(gridOriginX, gridOriginY, overlayAnimationPhase());
    }

    public static void drawOverlayGridPortalRift(int gridOriginX, int gridOriginY, double phase) {
        int gridW = gridPixelWidth();
        int gridH = gridPixelHeight(maxDisplaySlots());
        int ring = RIFT_OVERSHOOT;
        int outerX = gridOriginX - ring;
        int outerY = gridOriginY - ring;
        int outerW = gridW + ring * 2;
        int outerH = gridH + ring * 2;
        int border = Math.min(ring, Math.min(outerW, outerH) / 2);
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

    /** Nine-slice panel with horizontal UV strip offset on the center —subtle liquid wobble. */
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

    /** Expanding ripple rings + drifting shimmer bands —phase-driven, loops seamlessly. */
    private static void drawPortalRippleOverlay(int x, int y, int w, int h, double phase) {
        if (w <= 2 || h <= 2) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float cx = x + w * 0.5f;
        float cy = y + h * 0.52f;
        float maxRadius = Math.min(w, h) * 0.62f * OVERLAY_RIPPLE_SCALE;

        for (int ring = 0; ring < 4; ring++) {
            double ringPhase = phase + ring * (Math.PI * 0.5);
            float travel = (float) ((1.0 + Math.cos(ringPhase)) * 0.5);
            float radius = travel * maxRadius;
            float fade = (float) (Math.sin(travel * Math.PI) * 0.42 * OVERLAY_RIPPLE_SCALE);
            if (fade <= 0.02f) continue;
            int alpha = Math.min(255, (int) (fade * 0x55 * OVERLAY_RIPPLE_SCALE));
            float blend = (float) ((Math.sin(phase + ring * 1.1) + 1.0) * 0.5);
            drawRippleRing(cx, cy, radius, gradientArgb(blend, alpha));
        }

        int bandCount = 6;
        for (int band = 0; band < bandCount; band++) {
            double bandPhase = phase + band * (Math.PI * 2.0 / bandCount);
            float travel = (float) ((1.0 + Math.sin(bandPhase)) * 0.5);
            int by = y + (int) (travel * (h - 3));
            float fade = (float) (Math.sin(travel * Math.PI) * 0.55 * OVERLAY_RIPPLE_SCALE);
            int alpha = Math.min(255, (int) (fade * 0x20 * OVERLAY_RIPPLE_SCALE));
            if (alpha < 4) continue;
            float waveShift = (float) (Math.sin(phase + band * 0.9) * 3.0 * OVERLAY_RIPPLE_SCALE);
            int color = gradientArgb((float) band / bandCount, alpha);
            Gui.drawRect(x + 6 + (int) waveShift, by, x + w - 6 + (int) waveShift, by + 2, color);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void blitWavyCenter(int x, int y, int w, int h, int u, int v, int sw, int sh, double phase) {
        int strips = Math.max(12, h / 3);
        for (int i = 0; i < strips; i++) {
            float t = i / (float) strips;
            float wave = (float) (Math.sin(phase + t * Math.PI * 4.0 * OVERLAY_RIPPLE_SCALE) * 2.0
                * OVERLAY_RIPPLE_SCALE);
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
     * Faint blue-violet slot markers —thin outline + minimal tint, portal texture visible underneath.
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

    /**
     * Overlay-only slot grid: one continuous segment per row/column boundary; color field is
     * sampled from screen position + shared {@link #overlayAnimationPhase()} so lines and rift match.
     */
    public static void drawOverlaySlotGrid(int originX, int originY, int slotCount) {
        drawOverlaySlotGrid(originX, originY, slotCount, overlayAnimationPhase());
    }

    public static void drawOverlaySlotGrid(int originX, int originY, int slotCount, double phase) {
        if (slotCount <= 0) return;
        beginOverlayLineBlend();
        int cols = 9;
        int rows = (slotCount + cols - 1) / cols;

        // Internal horizontal dividers only —skip top (r=0) and bottom (r=rows) outer ring.
        for (int r = 1; r < rows; r++) {
            int widthCols = overlayRowBoundaryWidthCols(slotCount, cols, rows, r);
            if (widthCols <= 0) continue;
            float y = originY + r * CELL_SIZE;
            drawOverlayGradientLine(originX, y, originX + widthCols * CELL_SIZE, y, phase);
        }

        // Internal vertical dividers per row —skip left (c=0) and right (c=colsInRow) outer ring.
        for (int r = 0; r < rows; r++) {
            int colsInRow = overlayColsInRow(slotCount, cols, r);
            for (int c = 1; c < colsInRow; c++) {
                float x = originX + c * CELL_SIZE;
                float yTop = originY + r * CELL_SIZE;
                float yBot = originY + (r + 1) * CELL_SIZE;
                drawOverlayGradientLine(x, yTop, x, yBot, phase);
            }
        }

        endOverlayLineBlend();
    }

    private static int overlayColsInRow(int slotCount, int cols, int row) {
        int rowStart = row * cols;
        if (rowStart >= slotCount) return 0;
        return Math.min(cols, slotCount - rowStart);
    }

    private static int overlayRowBoundaryWidthCols(int slotCount, int cols, int rows, int boundaryRow) {
        if (boundaryRow <= 0) {
            return overlayColsInRow(slotCount, cols, 0);
        }
        if (boundaryRow >= rows) {
            return overlayColsInRow(slotCount, cols, rows - 1);
        }
        return Math
            .max(overlayColsInRow(slotCount, cols, boundaryRow - 1), overlayColsInRow(slotCount, cols, boundaryRow));
    }

    private static void beginOverlayLineBlend() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void endOverlayLineBlend() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * Portal-toned grid stroke: dark indigo underlay (reads on bright patches) + saturated
     * blue-violet core (reads on black) —same {@code phase} field as the rift background.
     */
    private static void drawOverlayGradientLine(float x0, float y0, float x1, float y1, double phase) {
        drawOverlayGradientLinePass(
            x0,
            y0,
            x1,
            y1,
            phase,
            OVERLAY_GRID_LINE_UNDERLAY_HALF,
            OVERLAY_GRID_LINE_ALPHA_UNDERLAY,
            1.0f,
            true);
        drawOverlayGradientLinePass(
            x0,
            y0,
            x1,
            y1,
            phase,
            OVERLAY_GRID_LINE_GLOW_HALF,
            OVERLAY_GRID_LINE_ALPHA_GLOW,
            0.62f,
            false);
        drawOverlayGradientLinePass(
            x0,
            y0,
            x1,
            y1,
            phase,
            OVERLAY_GRID_LINE_CORE_HALF,
            OVERLAY_GRID_LINE_ALPHA_CORE,
            1.0f,
            false);
    }

    private static void drawOverlayGradientLinePass(float x0, float y0, float x1, float y1, double phase,
        float halfThickness, int peakAlpha, float alphaScale, boolean underlay) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;

        float nx = -dy / len;
        float ny = dx / len;
        int steps = Math.max(8, (int) (len / 2.5f));

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float px = x0 + dx * t;
            float py = y0 + dy * t;
            int argb = underlay ? sampleOverlayGridLineUnderlayArgb(px, py, phase, peakAlpha, alphaScale)
                : sampleOverlayGridLineArgb(px, py, phase, peakAlpha, alphaScale);
            float rf = ((argb >> 16) & 0xFF) / 255.0f;
            float gf = ((argb >> 8) & 0xFF) / 255.0f;
            float bf = (argb & 0xFF) / 255.0f;
            float a = ((argb >> 24) & 0xFF) / 255.0f * overlayGridLineAlongAlpha(t);

            tess.setColorRGBA_F(rf, gf, bf, a);
            tess.addVertex(px - nx * halfThickness, py - ny * halfThickness, 0.0);
            tess.setColorRGBA_F(rf, gf, bf, a);
            tess.addVertex(px + nx * halfThickness, py + ny * halfThickness, 0.0);
        }
        tess.draw();
    }

    /** Along each segment: ends fade out (high transparency), center stays opaque; RGB from spatial field. */
    private static float overlayGridLineAlongAlpha(float tAlongSegment) {
        float center = (float) Math.sin(tAlongSegment * Math.PI);
        return OVERLAY_GRID_LINE_END_ALPHA + (1.0f - OVERLAY_GRID_LINE_END_ALPHA) * center;
    }

    /** Shared blue-violet field for overlay grid lines —matches rift {@code phase} for seamless motion. */
    private static int sampleOverlayGridLineArgb(float px, float py, double phase, int peakAlpha, float alphaScale) {
        float hueBlend = overlayGridHueBlend(px, py, phase);
        float alphaMod = (float) (0.72 + 0.28 * Math.sin(px * 0.048 + py * 0.048 + phase * 1.05));
        int alpha = (int) (peakAlpha * alphaMod * alphaScale);
        if (alpha < 16) alpha = 16;
        return overlayGridLineGradientArgb(hueBlend, alpha);
    }

    /** Deep indigo under-stroke —same hue family as portal, adds contrast on bright background tiles. */
    private static int sampleOverlayGridLineUnderlayArgb(float px, float py, double phase, int peakAlpha,
        float alphaScale) {
        float hueBlend = overlayGridHueBlend(px, py, phase);
        float alphaMod = (float) (0.78 + 0.22 * Math.sin(px * 0.048 + py * 0.048 + phase * 1.05));
        int alpha = (int) (peakAlpha * alphaMod * alphaScale);
        if (alpha < 28) alpha = 28;
        int r = blendChannel(0x06, 0x16, hueBlend);
        int g = blendChannel(0x08, 0x12, hueBlend);
        int b = blendChannel(0x22, 0x42, hueBlend);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static float overlayGridHueBlend(float px, float py, double phase) {
        float hueBlend = (float) ((Math.sin(px * 0.065 + phase) + Math.sin(py * 0.065 + phase * 0.92)) * 0.25 + 0.5);
        if (hueBlend < 0.0f) hueBlend = 0.0f;
        if (hueBlend > 1.0f) hueBlend = 1.0f;
        return hueBlend;
    }

    /** Saturated blue-violet core —bright enough on black, underlay handles light patches. */
    private static int overlayGridLineGradientArgb(float blend, int alpha) {
        int r = blendChannel(0x55, 0xAA, blend);
        int g = blendChannel(0x68, 0x66, blend);
        int b = blendChannel(0xCC, 0xFF, blend);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /** Phase in [0, 2π) for overlay rift + slot grid; wraps without discontinuity. */
    public static double overlayAnimationPhase() {
        return (Minecraft.getSystemTime() % OVERLAY_ANIM_PERIOD_MS) / (double) OVERLAY_ANIM_PERIOD_MS * Math.PI * 2.0;
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

    /** Player inventory strip Y —fixed below the max-size pocket grid. */
    public static int storagePlayerInventoryOriginY() {
        int rows = (maxDisplaySlots() + 8) / 9;
        return STORAGE_SLOT_ORIGIN_Y + rows * CELL_SIZE + 14 + STORAGE_PLAYER_INV_EXTRA_Y;
    }

    private static final int SIMPLE_PANEL_BG = 0xF0141C2C;
    private static final int SIMPLE_SECTION_BG = 0xE01A2438;
    private static final int SIMPLE_SECTION_BORDER = 0xFF4466AA;
    private static final int SIMPLE_SLOT_FILL = 0xFF2A3344;
    private static final int SIMPLE_SLOT_HIGHLIGHT = 0xFF5A6A88;
    private static final int SIMPLE_SLOT_SHADOW = 0xFF1A2030;

    /** Procedural slot cell —no texture atlas. */
    public static void drawSimpleSlotCell(int x, int y) {
        Gui.drawRect(x, y, x + CELL_SIZE, y + CELL_SIZE, SIMPLE_SLOT_FILL);
        Gui.drawRect(x, y, x + CELL_SIZE, y + 1, SIMPLE_SLOT_HIGHLIGHT);
        Gui.drawRect(x, y, x + 1, y + CELL_SIZE, SIMPLE_SLOT_HIGHLIGHT);
        Gui.drawRect(x + CELL_SIZE - 1, y, x + CELL_SIZE, y + CELL_SIZE, SIMPLE_SLOT_SHADOW);
        Gui.drawRect(x, y + CELL_SIZE - 1, x + CELL_SIZE, y + CELL_SIZE, SIMPLE_SLOT_SHADOW);
    }

    public static void drawSimpleSlotGrid(int originX, int originY, int cols, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                drawSimpleSlotCell(originX + col * CELL_SIZE, originY + row * CELL_SIZE);
            }
        }
    }

    /**
     * Upgrade-config GUI background: flat panel, upgrade section, 2x2 upgrade slots,
     * and player inventory slot grid (no portal / vanilla chest textures).
     */
    public static void drawSimpleConfigBackground(int guiLeft, int guiTop) {
        int guiW = 176;
        int guiH = 222;
        Gui.drawRect(guiLeft, guiTop, guiLeft + guiW, guiTop + guiH, SIMPLE_PANEL_BG);

        int sectionX1 = guiLeft + 6;
        int sectionY1 = guiTop + 8;
        int sectionX2 = guiLeft + 170;
        int sectionY2 = guiTop + CONFIG_PLAYER_INV_Y - 6;
        Gui.drawRect(sectionX1, sectionY1, sectionX2, sectionY2, SIMPLE_SECTION_BG);
        Gui.drawRect(sectionX1, sectionY1, sectionX2, sectionY1 + 1, SIMPLE_SECTION_BORDER);
        Gui.drawRect(sectionX1, sectionY2 - 1, sectionX2, sectionY2, SIMPLE_SECTION_BORDER);
        Gui.drawRect(sectionX1, sectionY1, sectionX1 + 1, sectionY2, SIMPLE_SECTION_BORDER);
        Gui.drawRect(sectionX2 - 1, sectionY1, sectionX2, sectionY2, SIMPLE_SECTION_BORDER);

        int[][] upgradeSlots = new int[][] { { CONFIG_UPGRADE_ORIGIN_X, CONFIG_UPGRADE_ORIGIN_Y },
            { CONFIG_UPGRADE_ORIGIN_X + CONFIG_UPGRADE_COL_STEP, CONFIG_UPGRADE_ORIGIN_Y },
            { CONFIG_UPGRADE_ORIGIN_X, CONFIG_UPGRADE_ROW2_Y },
            { CONFIG_UPGRADE_ORIGIN_X + CONFIG_UPGRADE_COL_STEP, CONFIG_UPGRADE_ROW2_Y }, };
        for (int i = 0; i < upgradeSlots.length; i++) {
            drawSimpleSlotCell(guiLeft + upgradeSlots[i][0], guiTop + upgradeSlots[i][1]);
        }

        int playerX = guiLeft + STORAGE_SLOT_ORIGIN_X;
        int playerY = guiTop + CONFIG_PLAYER_INV_Y;
        int hotbarY = guiTop + 184;
        drawSimpleSlotGrid(playerX, playerY, 9, 3);
        drawSimpleSlotGrid(playerX, hotbarY, 9, 1);
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
     * Y offset within {@link com.imgood.textech.gui.guiscreen.GuiPocketStorage} where the
     * vanilla chest-style player-inventory strip ({@code generic54.png} v=126) should begin so
     * slot backgrounds align with {@link com.imgood.textech.gui.container.ContainerPocketStorage}.
     */
    public static int storagePlayerInventorySplitY() {
        return storagePlayerInventoryOriginY();
    }

    public static void drawStorageRift(int guiLeft, int guiTop, int unlockedSlots) {
        int gridX = guiLeft + STORAGE_SLOT_ORIGIN_X;
        int gridY = guiTop + STORAGE_SLOT_ORIGIN_Y;
        double phase = overlayAnimationPhase();
        drawOverlayGridPortalRift(gridX, gridY, phase);
        drawOverlaySlotGrid(gridX, gridY, unlockedSlots, phase);
    }

    /** Portal rift + animated slot outlines for the 2x2 upgrade grid in the config GUI. */
    public static void drawConfigUpgradePortal(int guiLeft, int guiTop) {
        double phase = overlayAnimationPhase();
        int pad = 8;
        int x1 = guiLeft + CONFIG_UPGRADE_ORIGIN_X - pad;
        int y1 = guiTop + CONFIG_UPGRADE_ORIGIN_Y - pad;
        int x2 = guiLeft + CONFIG_UPGRADE_ORIGIN_X + CELL_SIZE + CONFIG_UPGRADE_COL_STEP + CELL_SIZE + pad;
        int y2 = guiTop + CONFIG_UPGRADE_ROW2_Y + CELL_SIZE + pad;
        drawLabelBackdrop(x1, y1, x2, y2);
        int[][] slotPos = new int[][] { { CONFIG_UPGRADE_ORIGIN_X, CONFIG_UPGRADE_ORIGIN_Y },
            { CONFIG_UPGRADE_ORIGIN_X + CONFIG_UPGRADE_COL_STEP, CONFIG_UPGRADE_ORIGIN_Y },
            { CONFIG_UPGRADE_ORIGIN_X, CONFIG_UPGRADE_ROW2_Y },
            { CONFIG_UPGRADE_ORIGIN_X + CONFIG_UPGRADE_COL_STEP, CONFIG_UPGRADE_ROW2_Y }, };
        for (int i = 0; i < slotPos.length; i++) {
            int sx = guiLeft + slotPos[i][0];
            int sy = guiTop + slotPos[i][1];
            drawOverlayGridPortalRift(sx, sy, phase);
            drawOverlaySlotGrid(sx, sy, 1, phase);
        }
    }

    /** Player inventory strip for config GUI —same portal-toned frame as storage. */
    public static void drawConfigPlayerInventoryBackground(int guiLeft, int guiTop) {
        int slotX = guiLeft + STORAGE_SLOT_ORIGIN_X;
        int playerY = guiTop + CONFIG_PLAYER_INV_Y;
        int hotbarY = playerY + 3 * CELL_SIZE + 4;
        int gridW = gridPixelWidth();
        int framePad = 3;
        int glowPad = 2;
        int frameX = slotX - framePad;
        int frameY = playerY - framePad;
        int frameW = gridW + framePad * 2;
        int frameH = hotbarY - playerY + CELL_SIZE + framePad * 2;
        double phase = animationPhase();
        drawAnimatedGradientFrame(
            frameX - glowPad,
            frameY - glowPad,
            frameW + glowPad * 2,
            frameH + glowPad * 2,
            phase);
        drawAnimatedGradientSlotGrid(slotX, playerY, 9, 3, phase, 0);
        drawAnimatedGradientSlotGrid(slotX, hotbarY, 9, 1, phase, 27);
    }

    public static void drawPortalStyleButton(Minecraft mc, int x, int y, int width, int height, String label) {
        int lineH = mc.fontRenderer.FONT_HEIGHT;
        int btnH = Math.max(height, lineH + 2);
        drawLabelBackdrop(
            x - LABEL_BACKDROP_PAD_X,
            y - LABEL_BACKDROP_PAD_Y,
            x + width + LABEL_BACKDROP_PAD_X,
            y + btnH + LABEL_BACKDROP_PAD_Y);
        Gui.drawRect(x, y, x + width, y + btnH, PAGE_ARROW_RIM_ENABLED);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + btnH - 1, PAGE_ARROW_FILL_ENABLED);
        int textW = mc.fontRenderer.getStringWidth(label);
        mc.fontRenderer.drawStringWithShadow(label, x + (width - textW) / 2, y + (btnH - lineH) / 2, 0xFFFFFF);
    }

    public static boolean hitsPortalStyleButton(int x, int y, int width, int height, int mouseX, int mouseY) {
        int btnH = Math.max(height, 9 + 2);
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + btnH;
    }

    /** Small left arrow in {@link com.imgood.textech.gui.guiscreen.GuiDimensionalPocketConfig} header. */
    public static void drawConfigBackButton(Minecraft mc, int x, int y) {
        int lineH = mc.fontRenderer.FONT_HEIGHT;
        drawLabelBackdrop(
            x - LABEL_BACKDROP_PAD_X,
            y - LABEL_BACKDROP_PAD_Y,
            x + STORAGE_PAGE_ARROW_W + LABEL_BACKDROP_PAD_X,
            y + lineH + LABEL_BACKDROP_PAD_Y);
        drawPageArrowButton(mc, x, y, true, true);
    }

    public static boolean hitsConfigBackButton(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + STORAGE_PAGE_ARROW_W && mouseY >= y && mouseY < y + CONFIG_LINE_HEIGHT;
    }

    /** Header chrome for {@link com.imgood.textech.gui.guiscreen.GuiPocketStorage}. */
    public static final class StorageHeaderLayout {

        public int lineY;
        public int titleY;
        public int leftArrowX;
        public int rightArrowX;
        public int pageTextX;
        public int pageTextY;
        public String pageText;
    }

    public static StorageHeaderLayout computeStorageHeaderLayout(Minecraft mc, int guiLeft, int guiTop, int guiWidth,
        int currentPage, int pageCount) {
        StorageHeaderLayout layout = new StorageHeaderLayout();
        int lineH = mc.fontRenderer.FONT_HEIGHT;
        layout.lineY = guiTop + (OVERLAY_TITLE_HEIGHT - lineH) / 2;
        layout.titleY = layout.lineY;
        layout.pageText = I18n.format("adm.label.pocket.pageFooter", currentPage + 1, pageCount);
        int pageTextWidth = mc.fontRenderer.getStringWidth(layout.pageText);
        int groupRight = guiLeft + guiWidth - STORAGE_HEADER_EDGE_PAD;
        layout.rightArrowX = groupRight - STORAGE_PAGE_ARROW_W;
        layout.pageTextX = layout.rightArrowX - STORAGE_HEADER_ARROW_GAP - pageTextWidth;
        layout.leftArrowX = layout.pageTextX - STORAGE_HEADER_ARROW_GAP - STORAGE_PAGE_ARROW_W;
        layout.pageTextY = layout.lineY;
        return layout;
    }

    public static void drawStorageHeader(Minecraft mc, int guiLeft, StorageHeaderLayout header, boolean canPrev,
        boolean canNext) {
        drawOverlayStyleTitle(
            mc,
            guiLeft + STORAGE_SLOT_ORIGIN_X,
            header.titleY,
            I18n.format("adm.title.pocketOverlay"));
        int lineH = mc.fontRenderer.FONT_HEIGHT;
        drawLabelBackdrop(
            header.leftArrowX - LABEL_BACKDROP_PAD_X,
            header.lineY - LABEL_BACKDROP_PAD_Y,
            header.rightArrowX + STORAGE_PAGE_ARROW_W + LABEL_BACKDROP_PAD_X,
            header.lineY + lineH + LABEL_BACKDROP_PAD_Y);
        drawPageArrowButton(mc, header.leftArrowX, header.lineY, true, canPrev);
        drawPageArrowButton(mc, header.rightArrowX, header.lineY, false, canNext);
        mc.fontRenderer.drawStringWithShadow(header.pageText, header.pageTextX, header.pageTextY, 0xAACCFF);
    }

    public static boolean hitsStoragePageArrow(StorageHeaderLayout header, int mouseX, int mouseY, boolean left,
        int lineH) {
        int x = left ? header.leftArrowX : header.rightArrowX;
        return mouseX >= x && mouseX < x + STORAGE_PAGE_ARROW_W
            && mouseY >= header.lineY
            && mouseY < header.lineY + lineH;
    }

    public static void drawStorageUpgradeButton(Minecraft mc, int x, int y, String label) {
        int lineH = mc.fontRenderer.FONT_HEIGHT;
        int btnH = Math.max(STORAGE_CONFIG_BTN_H, lineH + 2);
        drawLabelBackdrop(
            x - LABEL_BACKDROP_PAD_X,
            y - LABEL_BACKDROP_PAD_Y,
            x + STORAGE_CONFIG_BTN_W + LABEL_BACKDROP_PAD_X,
            y + btnH + LABEL_BACKDROP_PAD_Y);
        Gui.drawRect(x, y, x + STORAGE_CONFIG_BTN_W, y + btnH, PAGE_ARROW_RIM_ENABLED);
        Gui.drawRect(x + 1, y + 1, x + STORAGE_CONFIG_BTN_W - 1, y + btnH - 1, PAGE_ARROW_FILL_ENABLED);
        int textW = mc.fontRenderer.getStringWidth(label);
        mc.fontRenderer
            .drawStringWithShadow(label, x + (STORAGE_CONFIG_BTN_W - textW) / 2, y + (btnH - lineH) / 2, 0xFFFFFF);
    }

    public static boolean hitsStorageUpgradeButton(int x, int y, int mouseX, int mouseY) {
        int btnH = Math.max(STORAGE_CONFIG_BTN_H, 9 + 2);
        return mouseX >= x && mouseX < x + STORAGE_CONFIG_BTN_W && mouseY >= y && mouseY < y + btnH;
    }

    public static void drawOverlayStyleTitle(Minecraft mc, int x, int y, String text) {
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
            int b = 0xFF;
            int color = (r << 16) | (g << 8) | b;
            String segment = String.valueOf(ch);
            mc.fontRenderer.drawStringWithShadow(segment, x + offset, y, color);
            offset += mc.fontRenderer.getCharWidth(ch);
        }
    }

    private static void drawPageArrowButton(Minecraft mc, int x, int y, boolean left, boolean enabled) {
        int lineH = mc.fontRenderer.FONT_HEIGHT;
        int rim = enabled ? PAGE_ARROW_RIM_ENABLED : PAGE_ARROW_RIM_DISABLED;
        int fill = enabled ? PAGE_ARROW_FILL_ENABLED : PAGE_ARROW_FILL_DISABLED;
        Gui.drawRect(x, y, x + STORAGE_PAGE_ARROW_W, y + lineH, rim);
        Gui.drawRect(x + 1, y + 1, x + STORAGE_PAGE_ARROW_W - 1, y + lineH - 1, fill);
        String glyph = left ? "<" : ">";
        int glyphW = mc.fontRenderer.getStringWidth(glyph);
        mc.fontRenderer.drawStringWithShadow(
            glyph,
            x + (STORAGE_PAGE_ARROW_W - glyphW) / 2,
            y + (lineH - mc.fontRenderer.FONT_HEIGHT) / 2,
            enabled ? 0xFFFFFF : 0x808080);
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

        drawAnimatedGradientFrame(
            frameX - glowPad,
            frameY - glowPad,
            frameW + glowPad * 2,
            frameH + glowPad * 2,
            phase);
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

    private static void drawAnimatedGradientSlotGrid(int originX, int originY, int cols, int rows, double phase,
        int indexOffset) {
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
