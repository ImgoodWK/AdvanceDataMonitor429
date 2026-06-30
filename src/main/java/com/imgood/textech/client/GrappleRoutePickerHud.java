package com.imgood.textech.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;

import com.imgood.textech.handler.GrappleRouteMatcher;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * HUD for choosing among multiple matching saved routes before travel (scroll wheel + highlight).
 */
@SideOnly(Side.CLIENT)
public class GrappleRoutePickerHud {

    private static List<GrappleRouteMatcher.Match> pendingMatches;
    private static int selectedIndex = 0;

    public GrappleRoutePickerHud() {}

    public static void open(List<GrappleRouteMatcher.Match> matches) {
        if (matches == null || matches.isEmpty()) {
            clear();
            return;
        }
        pendingMatches = matches;
        selectedIndex = 0;
    }

    public static void clear() {
        pendingMatches = null;
        selectedIndex = 0;
    }

    public static boolean isOpen() {
        return pendingMatches != null && !pendingMatches.isEmpty();
    }

    public static GrappleRouteMatcher.Match getSelectedMatch() {
        if (!isOpen()) {
            return null;
        }
        if (selectedIndex < 0 || selectedIndex >= pendingMatches.size()) {
            selectedIndex = 0;
        }
        return pendingMatches.get(selectedIndex);
    }

    public static void scrollSelection(int delta) {
        if (!isOpen()) {
            return;
        }
        int size = pendingMatches.size();
        selectedIndex = (selectedIndex + delta + size * 16) % size;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != ElementType.ALL || !isOpen()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) {
            return;
        }
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int x = res.getScaledWidth() - 210;
        int y = res.getScaledHeight() / 2 - 40;
        mc.fontRenderer.drawStringWithShadow(I18n.format("adm.grapple.route_picker_title"), x, y - 14, 0x55FFFF);
        mc.fontRenderer.drawStringWithShadow(I18n.format("adm.grapple.route_picker_hint"), x, y, 0xAAAAAA);
        for (int i = 0; i < pendingMatches.size(); i++) {
            GrappleRouteMatcher.Match match = pendingMatches.get(i);
            String prefix = i == selectedIndex ? "> " : "  ";
            String dir = match.reversed ? I18n.format("adm.grapple.direction_reverse")
                : I18n.format("adm.grapple.direction_forward");
            String line = prefix + match.routeName + " (" + match.subPath.size() + ", " + dir + ")";
            int color = i == selectedIndex ? 0xFFFFFF : 0x888888;
            mc.fontRenderer.drawStringWithShadow(line, x, y + 14 + i * 12, color);
        }
    }
}
