package com.imgood.advancedatamonitor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAIChat;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAdvancePlanner;
import com.imgood.advancedatamonitor.items.ItemAdvancePlanner;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

/**
 * Manages all AdvanceDataMonitor key bindings registered in the Controls menu.
 */
public class KeyBindings {

    public final KeyBinding openAiChat = new KeyBinding(
        "key.advancedatamonitor.open_ai_chat",
        Keyboard.KEY_O,
        "key.categories.advancedatamonitor");

    public final KeyBinding openPlanner = new KeyBinding(
        "key.advancedatamonitor.open_planner",
        Keyboard.KEY_P,
        "key.categories.advancedatamonitor");

    public final KeyBinding toggleHud = new KeyBinding(
        "key.advancedatamonitor.toggle_hud",
        Keyboard.KEY_H,
        "key.categories.advancedatamonitor");

    public void register() {
        ClientRegistry.registerKeyBinding(openAiChat);
        ClientRegistry.registerKeyBinding(openPlanner);
        ClientRegistry.registerKeyBinding(toggleHud);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openAiChat.isPressed()) {
            openAiChatGui();
        } else if (openPlanner.isPressed()) {
            openPlannerGui();
        } else if (toggleHud.isPressed()) {
            togglePlannerHud();
        }
    }

    private void openAiChatGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        AdvanceDataMonitor.LOG.info("[ADM] AI Chat key pressed");
        mc.displayGuiScreen(new GuiAIChat(mc.currentScreen));
    }

    private void openPlannerGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        AdvanceDataMonitor.LOG.info("[ADM] Planner key pressed");
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
        AdvanceDataMonitor.LOG.info("[ADM] HUD toggle key pressed");
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
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[ADM] " + text));
        }
    }
}
