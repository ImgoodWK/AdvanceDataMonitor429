package com.imgood.textech.renders;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Client-only, event-driven HUD for owner-scoped WebAE alert packets. */
public final class WebAlertHudRenderer {

    private static final WebAlertHudRenderer INSTANCE = new WebAlertHudRenderer();
    private static final int WIDTH = 260;
    private static final int PADDING = 5;
    private static final int GAP = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_MESSAGE_LINES = 3;

    private final Deque<HudEntry> entries = new ArrayDeque<HudEntry>();
    private int maxVisible = 3;
    private String position = "top_right";

    private WebAlertHudRenderer() {}

    public static WebAlertHudRenderer instance() {
        return INSTANCE;
    }

    public synchronized void push(String severity, String title, String message, int durationSeconds, int maxVisible,
        String position, boolean soundEnabled) {
        long now = System.currentTimeMillis();
        prune(now);
        this.maxVisible = Math.max(1, Math.min(8, maxVisible));
        this.position = normalizePosition(position);
        entries.addFirst(
            new HudEntry(
                safe(severity),
                safe(title),
                safe(message),
                now + (long) Math.max(2, Math.min(120, durationSeconds)) * 1000L));
        while (entries.size() > 8) entries.removeLast();
        if (soundEnabled) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.thePlayer != null) {
                minecraft.thePlayer.playSound("random.orb", 0.45F, "error".equals(severity) ? 0.7F : 1.0F);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.currentScreen != null) return;

        List<HudEntry> visible;
        int visibleLimit;
        String anchor;
        synchronized (this) {
            prune(System.currentTimeMillis());
            if (entries.isEmpty()) return;
            visibleLimit = maxVisible;
            anchor = position;
            visible = new ArrayList<HudEntry>();
            Iterator<HudEntry> iterator = entries.iterator();
            while (iterator.hasNext() && visible.size() < visibleLimit) {
                visible.add(iterator.next());
            }
        }

        FontRenderer font = minecraft.fontRenderer;
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        List<RenderedEntry> rendered = new ArrayList<RenderedEntry>();
        int totalHeight = 0;
        for (HudEntry entry : visible) {
            RenderedEntry item = renderData(font, entry);
            rendered.add(item);
            totalHeight += item.height + GAP;
        }
        if (totalHeight > 0) totalHeight -= GAP;

        boolean right = anchor.endsWith("right");
        boolean bottom = anchor.startsWith("bottom");
        int x = right ? resolution.getScaledWidth() - WIDTH - 6 : 6;
        int y = bottom ? resolution.getScaledHeight() - totalHeight - 6 : 6;
        for (RenderedEntry item : rendered) {
            HudRenderUtil.drawBackground(x, y, WIDTH, item.height, 0xC0101010);
            HudRenderUtil.drawBackground(x, y, 3, item.height, 0xE0000000 | severityColor(item.entry.severity));
            font.drawStringWithShadow(
                I18n.format("adm.webae.alert.hud.title") + " · " + item.entry.title,
                x + PADDING + 3,
                y + PADDING,
                severityColor(item.entry.severity));
            int lineY = y + PADDING + LINE_HEIGHT + 2;
            for (String line : item.lines) {
                font.drawStringWithShadow(line, x + PADDING + 3, lineY, 0xFFFFFF);
                lineY += LINE_HEIGHT;
            }
            y += item.height + GAP;
        }
    }

    private static RenderedEntry renderData(FontRenderer font, HudEntry entry) {
        List<String> wrapped = font.listFormattedStringToWidth(entry.message, WIDTH - PADDING * 2 - 6);
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < wrapped.size() && i < MAX_MESSAGE_LINES; i++) {
            lines.add(wrapped.get(i));
        }
        if (wrapped.size() > MAX_MESSAGE_LINES && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, lines.get(last) + "…");
        }
        if (lines.isEmpty()) lines.add("");
        int height = PADDING * 2 + LINE_HEIGHT + 2 + lines.size() * LINE_HEIGHT;
        return new RenderedEntry(entry, lines, height);
    }

    private synchronized void prune(long now) {
        Iterator<HudEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAtMs <= now) iterator.remove();
        }
    }

    private static int severityColor(String severity) {
        if ("error".equals(severity)) return 0xFF5555;
        if ("warning".equals(severity)) return 0xFFAA00;
        return 0x55FFFF;
    }

    private static String normalizePosition(String value) {
        String position = safe(value).toLowerCase();
        if ("top_left".equals(position) || "bottom_left".equals(position) || "bottom_right".equals(position)) {
            return position;
        }
        return "top_right";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class HudEntry {

        final String severity;
        final String title;
        final String message;
        final long expiresAtMs;

        HudEntry(String severity, String title, String message, long expiresAtMs) {
            this.severity = severity;
            this.title = title;
            this.message = message;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class RenderedEntry {

        final HudEntry entry;
        final List<String> lines;
        final int height;

        RenderedEntry(HudEntry entry, List<String> lines, int height) {
            this.entry = entry;
            this.lines = lines;
            this.height = height;
        }
    }
}
