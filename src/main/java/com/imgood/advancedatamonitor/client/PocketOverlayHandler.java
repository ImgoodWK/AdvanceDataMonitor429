package com.imgood.advancedatamonitor.client;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.imgood.advancedatamonitor.items.ItemDimensionalPocket;
import com.imgood.advancedatamonitor.network.packet.PacketPocketAction;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side event hub for the Dimensional Pocket overlay. Detects when the
 * player has a GuiContainer open AND a pocket in the inventory AND the switch
 * is on, then ensures the extension slots are injected into the open
 * Container.inventorySlots so Shift/drag interop flows natively via windowClick.
 *
 * The injection uses reflection on the protected fields; failures degrade
 * gracefully to a read-only overlay (no slots added) so other mods' containers
 * never break.
 */
@SideOnly(Side.CLIENT)
public class PocketOverlayHandler {

    private static PocketOverlayHandler instance;

    public static PocketOverlayHandler instance() {
        if (instance == null) instance = new PocketOverlayHandler();
        return instance;
    }

    private GuiPocketOverlay overlay;
    private Container injectedContainer;
    private int injectedSlotBase; // first injected slot index in inventorySlots
    private int injectedSlotCount;

    private boolean overlayActive = false;

    public GuiPocketOverlay getOverlay() {
        return overlay;
    }

    public boolean isOverlayActive() {
        return overlayActive;
    }

    public int getInjectedSlotBase() {
        return injectedSlotBase;
    }

    public int getInjectedSlotCount() {
        return injectedSlotCount;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            teardown();
            return;
        }
        GuiScreen screen = mc.currentScreen;
        boolean shouldShow = screen instanceof GuiContainer && ItemDimensionalPocket.hasPocketInInventory(mc.thePlayer)
            && PocketClientCache.isEnabled();

        if (shouldShow) {
            ensureOverlay((GuiContainer) screen);
            // Update drag state: if left button released, stop dragging.
            if (overlay != null && overlay.isDragging() && !org.lwjgl.input.Mouse.isButtonDown(0)) {
                overlay.setDragging(false);
            }
        } else {
            teardown();
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!overlayActive || overlay == null) return;
        overlay.draw(event.partialTicks);
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (!overlayActive || overlay == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiContainer)) return;
        int mouseX = event.x;
        int mouseY = event.y;
        if (event.dwheel != 0) {
            if (overlay.onMouseWheel(mouseX, mouseY, event.dwheel)) {
                event.setCanceled(true);
            }
        } else if (event.button >= 0) {
            // MouseEvent fires on press; treat as a click. Dragging is handled in tick.
            if (event.button == 0) {
                overlay.onMouseClicked(mouseX, mouseY, 0);
                // Don't cancel — let the host container also see the click so slot
                // interaction via windowClick flows naturally when the cursor is on
                // an injected slot. The overlay's own button/drag handling only
                // consumes clicks that hit non-slot regions (title bar, arrows, collapse).
            }
        }
    }

    private void ensureOverlay(GuiContainer gui) {
        // Request a sync periodically if we haven't yet received state.
        if (!PocketClientCache.isReceived()) {
            com.imgood.advancedatamonitor.AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.requestSync());
        }
        tryInject(gui);
        if (overlay == null) {
            overlay = new GuiPocketOverlay(this, gui);
        } else {
            overlay.attach(gui);
        }
        overlayActive = true;
    }

    @SuppressWarnings("unchecked")
    private void tryInject(GuiContainer gui) {
        Container container = gui.inventorySlots;
        if (container == null) return;
        if (container == injectedContainer) return; // already injected into this one

        // If switching to a new container, tear down the previous injection first.
        if (injectedContainer != null) {
            cleanupInjection();
        }

        try {
            List<Slot> slots = (List<Slot>) getField(container.getClass(), "inventorySlots").get(container);
            int base = slots.size();
            int slotsPerPage = Math.max(1, PocketClientCache.getSlotsPerPage());
            // addSlotToContainer is protected in 1.7.10; reflect into it so slotNumber
            // and inventoryItemStacks stay in sync with inventorySlots.
            java.lang.reflect.Method addSlot = Container.class.getDeclaredMethod("addSlotToContainer", Slot.class);
            addSlot.setAccessible(true);
            for (int i = 0; i < slotsPerPage; i++) {
                SlotPocketPage slot = new SlotPocketPage(this, i);
                addSlot.invoke(container, slot);
            }
            injectedContainer = container;
            injectedSlotBase = base;
            injectedSlotCount = slotsPerPage;
        } catch (Throwable t) {
            // Reflection failed or container hostile to injection — degrade to read-only.
            injectedContainer = null;
            injectedSlotBase = -1;
            injectedSlotCount = 0;
        }
    }

    @SuppressWarnings("unchecked")
    private void cleanupInjection() {
        if (injectedContainer == null) return;
        try {
            List<Slot> slots = (List<Slot>) getField(injectedContainer.getClass(), "inventorySlots")
                .get(injectedContainer);
            if (injectedSlotBase >= 0 && injectedSlotBase < slots.size()) {
                while (slots.size() > injectedSlotBase) {
                    slots.remove(slots.size() - 1);
                }
            }
        } catch (Throwable ignored) {}
        injectedContainer = null;
        injectedSlotBase = -1;
        injectedSlotCount = 0;
    }

    private void teardown() {
        cleanupInjection();
        overlay = null;
        overlayActive = false;
    }

    private static Field getField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
