package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.imgood.textech.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Throttled in-game chat (and optional action bar) progress for icon export sessions.
 */
@SideOnly(Side.CLIENT)
final class IconUploadProgress {

    private long lastChatMs;
    private int lastRenderPercent = -1;
    private int lastRenderIndex;
    private int lastUploadChunk = -1;

    void resetForMode() {
        lastRenderPercent = -1;
        lastRenderIndex = 0;
        lastUploadChunk = -1;
    }

    void onModeStart(String packName, IconRenderMode mode, int modeIndexOneBased, int modeTotal, int taskTotal) {
        resetForMode();
        chatNow(
            EnumChatFormatting.AQUA + "[WebAE] "
                + EnumChatFormatting.WHITE
                + "icon export "
                + modeIndexOneBased
                + "/"
                + modeTotal
                + " mode="
                + mode.getId()
                + " render 0/"
                + taskTotal
                + " (0%)…");
        actionBar("WebAE " + mode.getId() + " 0/" + taskTotal);
    }

    void onRenderProgress(
        IconRenderMode mode,
        int modeIndexOneBased,
        int modeTotal,
        int currentIndex,
        int taskTotal) {
        if (taskTotal <= 0) return;
        int pct = currentIndex * 100 / taskTotal;
        boolean byPercent = pct >= lastRenderPercent + 5;
        boolean byCount = currentIndex - lastRenderIndex >= 500;
        if (currentIndex < taskTotal && !byPercent && !byCount) return;
        if (!shouldThrottleChat()) return;
        lastRenderPercent = pct;
        lastRenderIndex = currentIndex;
        chat(
            EnumChatFormatting.AQUA + "[WebAE] "
                + EnumChatFormatting.WHITE
                + "icon export "
                + modeIndexOneBased
                + "/"
                + modeTotal
                + " mode="
                + mode.getId()
                + " render "
                + currentIndex
                + "/"
                + taskTotal
                + " ("
                + pct
                + "%)…");
        actionBar("WebAE " + mode.getId() + " " + currentIndex + "/" + taskTotal);
    }

    void onRenderComplete(String packName, IconRenderMode mode, int iconCount) {
        chatNow(
            EnumChatFormatting.AQUA + "[WebAE] "
                + EnumChatFormatting.WHITE
                + "icon export mode="
                + mode.getId()
                + " render done ("
                + iconCount
                + " icons), uploading…");
    }

    void onUploadChunk(IconRenderMode mode, int chunkOneBased, int totalChunks) {
        if (chunkOneBased < totalChunks && chunkOneBased % Math.max(1, totalChunks / 10) != 0
            && chunkOneBased != 1) {
            if (chunkOneBased - lastUploadChunk < 3 && !shouldThrottleChat()) return;
        }
        if (!shouldThrottleChat() && chunkOneBased < totalChunks) return;
        lastUploadChunk = chunkOneBased;
        chat(
            EnumChatFormatting.AQUA + "[WebAE] "
                + EnumChatFormatting.WHITE
                + "icon export mode="
                + mode.getId()
                + " upload chunk "
                + chunkOneBased
                + "/"
                + totalChunks
                + "…");
    }

    void onModeComplete(String packName, IconRenderMode mode, int iconCount) {
        chatNow(
            EnumChatFormatting.GREEN + "[WebAE] "
                + EnumChatFormatting.WHITE
                + "icon export complete pack="
                + packName
                + " mode="
                + mode.getId()
                + " ("
                + iconCount
                + " icons)");
    }

    void onSessionComplete(String packName, int modeTotal) {
        chatNow(
            EnumChatFormatting.GREEN + "[WebAE] "
                + EnumChatFormatting.WHITE
                + "all icon modes exported pack="
                + packName
                + " ("
                + modeTotal
                + "/"
                + modeTotal
                + ")");
        actionBar("");
    }

    private boolean shouldThrottleChat() {
        long now = System.currentTimeMillis();
        if (now - lastChatMs < Config.webIconProgressChatIntervalMs) return false;
        lastChatMs = now;
        return true;
    }

    private void chatNow(String text) {
        lastChatMs = System.currentTimeMillis();
        sendChat(text);
    }

    private void chat(String text) {
        lastChatMs = System.currentTimeMillis();
        sendChat(text);
    }

    private static void sendChat(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(text));
        }
    }

    private static void actionBar(String line) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.ingameGUI != null && line != null) {
                mc.ingameGUI.func_110326_a(line, true);
            }
        } catch (Throwable ignored) {}
    }
}
