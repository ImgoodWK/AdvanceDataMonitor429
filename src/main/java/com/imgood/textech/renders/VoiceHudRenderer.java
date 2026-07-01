package com.imgood.textech.renders;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.imgood.textech.gui.guiscreen.GuiAIChat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * In-game voice assistant HUD overlay. Renders a simple translucent panel in
 * the screen corner showing the voice status, the current page of the AI
 * chat history, and a page indicator. The player retains full game control
 * (no GUI is opened) while the HUD is visible.
 *
 * State machine:
 * - HIDDEN: not drawn.
 * - ACTIVE_MANUAL: opened by the voice hotkey; never auto-closes.
 * - ACTIVE_AUTO: opened by an incoming AI reply; auto-closes after 30s.
 * - RECORDING / TRANSCRIBING: transient voice pipeline states.
 *
 * 显示名称 / Display names:
 * - EN: Voice Assistant HUD
 * - ZH: 语音助手 HUD
 * Lang keys: adm.voice.hud.*
 */
public class VoiceHudRenderer {

    private enum HudState {
        HIDDEN,
        ACTIVE_MANUAL,
        ACTIVE_AUTO,
        RECORDING,
        TRANSCRIBING
    }

    private static final int ENTRIES_PER_PAGE = 6;
    private static final long AUTO_CLOSE_MS = 30000L;
    private static final long SNAPSHOT_REFRESH_MS = 500L;
    private static final int HUD_X = 4;
    private static final int HUD_Y = 4;
    private static final int HUD_WIDTH = 280;
    private static final int HUD_PADDING = 4;
    private static final int LINE_HEIGHT = 12;
    private static final int TITLE_HEIGHT = 16;

    private static final int COLOR_TITLE = 0x00FFFF;
    private static final int COLOR_USER = 0xFFFFFF;
    private static final int COLOR_ASSISTANT = 0x55FF55;
    private static final int COLOR_PAGE = 0x888888;
    private static final int COLOR_STATUS_REC = 0xFFAA00;
    private static final int COLOR_STATUS_TRANS = 0x55AAFF;

    private static VoiceHudRenderer instance;

    private HudState state = HudState.HIDDEN;
    private long lastActiveTimeMs = 0L;
    private int currentPage = 0; // 0 = newest page
    private int totalPages = 1;

    private final List<String> cachedRoles = new ArrayList<String>();
    private final List<String> cachedContents = new ArrayList<String>();
    private int lastHistorySize = -1;
    private long lastSnapshotTimeMs = 0L;

    public static synchronized VoiceHudRenderer instance() {
        if (instance == null) {
            instance = new VoiceHudRenderer();
        }
        return instance;
    }

    private VoiceHudRenderer() {}

    public boolean isVisible() {
        return state != HudState.HIDDEN;
    }

    /** Opened by the voice hotkey — stays open until explicitly closed. */
    public void openManual() {
        state = HudState.ACTIVE_MANUAL;
        currentPage = 0;
        refreshSnapshot();
    }

    /** Opened by an incoming AI reply — auto-closes after 30 seconds. */
    public void openAuto() {
        if (state == HudState.HIDDEN || state == HudState.ACTIVE_AUTO) {
            state = HudState.ACTIVE_AUTO;
            lastActiveTimeMs = System.currentTimeMillis();
            currentPage = 0;
        }
        // If ACTIVE_MANUAL, keep manual state but jump to newest page.
        if (state == HudState.ACTIVE_MANUAL) {
            currentPage = 0;
        }
        refreshSnapshot();
    }

    public void hide() {
        state = HudState.HIDDEN;
    }

    public void setRecording(boolean recording) {
        if (recording) {
            state = HudState.RECORDING;
        } else if (state == HudState.RECORDING) {
            state = HudState.ACTIVE_MANUAL;
        }
    }

    public void setTranscribing(boolean transcribing) {
        if (transcribing) {
            state = HudState.TRANSCRIBING;
        } else if (state == HudState.TRANSCRIBING) {
            state = HudState.ACTIVE_MANUAL;
        }
    }

    /** "Next page" = view newer content = move toward the newest page. */
    public void pageNext() {
        if (currentPage > 0) {
            currentPage--;
        }
    }

    /** "Prev page" = view older content = move toward the oldest page. */
    public void pagePrev() {
        if (currentPage < totalPages - 1) {
            currentPage++;
        }
    }

    private void refreshSnapshot() {
        GuiAIChat.snapshotHistory(cachedRoles, cachedContents);
        lastHistorySize = cachedRoles.size();
        lastSnapshotTimeMs = System.currentTimeMillis();
        totalPages = Math.max(1, (cachedRoles.size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE);
        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        // Only draw the HUD when no GUI screen is open, so the player keeps
        // full game control (movement, combat, etc.).
        if (mc.currentScreen != null) {
            return;
        }
        if (state == HudState.HIDDEN) {
            return;
        }

        // Auto-close logic for ACTIVE_AUTO state.
        if (state == HudState.ACTIVE_AUTO) {
            if (System.currentTimeMillis() - lastActiveTimeMs > AUTO_CLOSE_MS) {
                state = HudState.HIDDEN;
                return;
            }
        }

        // Refresh the history snapshot periodically or when history grows.
        int currentSize = GuiAIChat.getSharedHistorySize();
        if (currentSize != lastHistorySize || System.currentTimeMillis() - lastSnapshotTimeMs > SNAPSHOT_REFRESH_MS) {
            refreshSnapshot();
        }

        renderHud(mc);
    }

    private void renderHud(Minecraft mc) {
        FontRenderer fr = mc.fontRenderer;
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        int textMaxWidth = HUD_WIDTH - HUD_PADDING * 2 - 4;

        // Determine the entries for the current page.
        // Page 0 = newest (last ENTRIES_PER_PAGE entries).
        // Page n = the ENTRIES_PER_PAGE entries starting at
        // (totalEntries - (currentPage + 1) * ENTRIES_PER_PAGE).
        int totalEntries = cachedRoles.size();
        int pageEndExclusive = totalEntries - currentPage * ENTRIES_PER_PAGE;
        int pageStart = Math.max(0, pageEndExclusive - ENTRIES_PER_PAGE);
        if (pageEndExclusive <= 0) {
            // No entries on this page (shouldn't happen due to clamping).
            return;
        }

        // Compute the wrapped lines for each entry on the current page.
        List<String> pageLines = new ArrayList<String>();
        List<Integer> pageLineColors = new ArrayList<Integer>();
        for (int i = pageStart; i < pageEndExclusive; i++) {
            String role = i < cachedRoles.size() ? cachedRoles.get(i) : "assistant";
            String content = i < cachedContents.size() ? cachedContents.get(i) : "";
            String prefix;
            int color;
            if ("user".equals(role)) {
                prefix = I18n.format("adm.voice.hud.user_prefix") + " ";
                color = COLOR_USER;
            } else {
                prefix = I18n.format("adm.voice.hud.assistant_prefix") + " ";
                color = COLOR_ASSISTANT;
            }
            List<String> wrapped = fr.listFormattedStringToWidth(prefix + content, textMaxWidth);
            if (wrapped.isEmpty()) {
                wrapped = new ArrayList<String>();
                wrapped.add(prefix);
            }
            for (int li = 0; li < wrapped.size(); li++) {
                pageLines.add(wrapped.get(li));
                // Only color the first line with the role color; subsequent
                // wrapped lines use the same color for consistency.
                pageLineColors.add(color);
            }
        }

        // Build status line.
        String statusLine;
        int statusColor;
        switch (state) {
            case RECORDING:
                statusLine = "\u00a7e\u25cf " + I18n.format("adm.voice.hud.recording");
                statusColor = COLOR_STATUS_REC;
                break;
            case TRANSCRIBING:
                statusLine = "\u00a7b\u21bb " + I18n.format("adm.voice.hud.transcribing");
                statusColor = COLOR_STATUS_TRANS;
                break;
            default:
                statusLine = "\u00a7b[ADM AI]";
                statusColor = COLOR_TITLE;
                break;
        }

        // Compute background height.
        int contentHeight = pageLines.size() * LINE_HEIGHT;
        int bgHeight = HUD_PADDING + TITLE_HEIGHT + contentHeight + LINE_HEIGHT + HUD_PADDING;
        int bgWidth = HUD_WIDTH;

        // Clamp position to screen.
        int x = HUD_X;
        int y = HUD_Y;
        if (x + bgWidth > sr.getScaledWidth()) {
            x = Math.max(0, sr.getScaledWidth() - bgWidth);
        }
        if (y + bgHeight > sr.getScaledHeight()) {
            y = Math.max(0, sr.getScaledHeight() - bgHeight);
        }

        // Draw translucent background.
        HudRenderUtil.drawBackground(x, y, bgWidth, bgHeight, 0xA0000000);

        // Draw status/title line.
        fr.drawStringWithShadow(statusLine, x + HUD_PADDING, y + HUD_PADDING, statusColor);

        // Draw chat history lines.
        int lineY = y + HUD_PADDING + TITLE_HEIGHT;
        for (int i = 0; i < pageLines.size(); i++) {
            fr.drawStringWithShadow(pageLines.get(i), x + HUD_PADDING, lineY, pageLineColors.get(i));
            lineY += LINE_HEIGHT;
        }

        // Draw page indicator.
        int humanPage = totalPages - currentPage; // 1 = oldest, totalPages = newest
        String pageLine = I18n.format("adm.voice.hud.page_indicator", humanPage, totalPages);
        fr.drawStringWithShadow(pageLine, x + HUD_PADDING, lineY + 2, COLOR_PAGE);
    }
}
