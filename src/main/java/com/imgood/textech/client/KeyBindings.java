package com.imgood.textech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantMonitorRegistry;
import com.imgood.textech.gui.guiscreen.GuiAIChat;
import com.imgood.textech.gui.guiscreen.GuiAdvancePlanner;
import com.imgood.textech.gui.guiscreen.GuiMainAdvanceDataMonitor;
import com.imgood.textech.items.ItemAdvancePlanner;
import com.imgood.textech.network.packet.PacketMonitorRecord;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

/**
 * Manages all AdvanceDataMonitor key bindings registered in the Controls menu.
 */
public class KeyBindings {

    private static final int MONITOR_SEARCH_RADIUS = 32;
    private static final ResourceLocation MONITOR_MAIN_BACKGROUND = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_AdvanceDataMonitor_Main.png");

    public final KeyBinding openAiChat = new KeyBinding(
        "key.textech.open_ai_chat",
        Keyboard.KEY_O,
        "key.categories.textech");

    public final KeyBinding openPlanner = new KeyBinding(
        "key.textech.open_planner",
        Keyboard.KEY_P,
        "key.categories.textech");

    public final KeyBinding toggleHud = new KeyBinding(
        "key.textech.toggle_hud",
        Keyboard.KEY_H,
        "key.categories.textech");

    public final KeyBinding openMonitorAi = new KeyBinding(
        "key.textech.open_monitor_ai",
        Keyboard.KEY_NONE,
        "key.categories.textech");

    public void register() {
        ClientRegistry.registerKeyBinding(openAiChat);
        ClientRegistry.registerKeyBinding(openPlanner);
        ClientRegistry.registerKeyBinding(toggleHud);
        ClientRegistry.registerKeyBinding(openMonitorAi);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openAiChat.isPressed()) {
            openAiChatGui();
        } else if (openPlanner.isPressed()) {
            openPlannerGui();
        } else if (toggleHud.isPressed()) {
            togglePlannerHud();
        } else if (openMonitorAi.isPressed()) {
            openNearbyMonitorAiGui();
        }
    }

    private void openAiChatGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        AdvanceDataMonitor.LOG.info("[TeXTech] AI Chat key pressed");
        mc.displayGuiScreen(new GuiAIChat(mc.currentScreen));
    }

    private void openNearbyMonitorAiGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        AdvanceDataMonitor.LOG.info("[TeXTech] Nearby monitor AI key pressed");
        TileEntityAdvanceDataMonitor monitor = AssistantMonitorRegistry
            .findNearest(mc.thePlayer, MONITOR_SEARCH_RADIUS);
        if (monitor == null) {
            notifyPlayer(I18n.format("adm.error.no_nearby_monitor"));
            return;
        }
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(new PacketMonitorRecord(monitor.xCoord, monitor.yCoord, monitor.zCoord));
        GuiMainAdvanceDataMonitor monitorGui = new GuiMainAdvanceDataMonitor(mc.thePlayer, mc.theWorld, monitor);
        monitorGui.setPosition(-10, 30);
        monitorGui.setSize(470, 270);
        monitorGui.setStretch(false);
        monitorGui.setBackgroundTexture(MONITOR_MAIN_BACKGROUND);
        mc.displayGuiScreen(new GuiAIChat(monitorGui));
    }

    private void openPlannerGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        AdvanceDataMonitor.LOG.info("[TeXTech] Planner key pressed");
        // Find first planner in inventory
        for (int i = 0; i < mc.thePlayer.inventory.getSizeInventory(); i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                mc.displayGuiScreen(new GuiAdvancePlanner(stack, mc.thePlayer));
                return;
            }
        }
        notifyPlayer("No Advance Planner found in inventory.");
    }

    private void togglePlannerHud() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        AdvanceDataMonitor.LOG.info("[TeXTech] HUD toggle key pressed");
        for (int i = 0; i < mc.thePlayer.inventory.getSizeInventory(); i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                boolean wasEnabled = ItemAdvancePlanner.isHudEnabled(stack);
                ItemAdvancePlanner.setHudEnabled(stack, !wasEnabled);
                notifyPlayer("Planner HUD: " + (!wasEnabled ? "ON" : "OFF"));
                return;
            }
        }
        notifyPlayer("No Advance Planner found in inventory.");
    }

    private void notifyPlayer(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[TeXTech] " + text));
        }
    }
}
