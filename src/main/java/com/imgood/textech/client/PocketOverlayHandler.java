package com.imgood.textech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.imgood.textech.client.PocketStackSizeFormat;

import com.imgood.textech.gui.guiscreen.GuiDimensionalPocketConfig;
import com.imgood.textech.gui.guiscreen.GuiPocketStorage;
import com.imgood.textech.items.ItemDimensionalPocket;
import com.imgood.textech.network.packet.PacketPocketAction;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;

/**
 * Client-side event hub for the Dimensional Pocket overlay. Detects when the
 * player has a GuiContainer open AND a pocket in the inventory AND the switch
 * is on, then renders the overlay on top. Item movement goes through custom
 * packet types (DEPOSIT / WITHDRAW) instead of vanilla windowClick, avoiding
 * the reflection-based slot injection that broke with AE2 and other mods.
 */
@SideOnly(Side.CLIENT)
public class PocketOverlayHandler {

    private static PocketOverlayHandler instance;

    public static PocketOverlayHandler instance() {
        if (instance == null) instance = new PocketOverlayHandler();
        return instance;
    }

    private GuiPocketOverlay overlay;
    private boolean overlayActive = false;
    private int realMouseX = -1;
    private int realMouseY = -1;

    public GuiPocketOverlay getOverlay() {
        return overlay;
    }

    public boolean isOverlayActive() {
        return overlayActive;
    }

    public void setRealMouse(int x, int y) {
        realMouseX = x;
        realMouseY = y;
    }

    public void clearRealMouse() {
        realMouseX = -1;
        realMouseY = -1;
    }

    public int getRealMouseX() {
        return realMouseX;
    }

    public int getRealMouseY() {
        return realMouseY;
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
        boolean shouldShow = screen instanceof GuiContainer
            && !isPocketNativeGui(screen)
            && ItemDimensionalPocket.hasPocketInInventory(mc.thePlayer);

        if (shouldShow) {
            ensureOverlay((GuiContainer) screen);
            if (overlay != null && overlay.isDragging() && !org.lwjgl.input.Mouse.isButtonDown(0)) {
                overlay.onDragFinished();
            }
        } else {
            teardown();
        }
    }

    /**
     * Render AFTER the entire GuiScreen.drawScreen() finishes â€?including NEI's
     * item panel, search bar, and transparent overlay which are drawn via the
     * GuiContainer rendering pipeline (not via Forge's RenderGameOverlayEvent).
     * This guarantees the pocket overlay is always on top, never behind NEI.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!overlayActive || overlay == null) return;
        // Use saved real mouse coordinates; drawScreen parameters are suppressed
        // to -9999 by @ModifyArgs when cursor is over the overlay.
        int realMx = realMouseX >= 0 ? realMouseX : event.mouseX;
        int realMy = realMouseY >= 0 ? realMouseY : event.mouseY;
        renderOverlayPass(realMx, realMy, event.renderPartialTicks);
    }

    /**
     * Re-renders the overlay from a MixinGuiScreen-less RETURN inject on
     * {@code GuiContainer.drawScreen}. NEI draws its hover highlights (item panel,
     * delete button, bookmarks) inside drawScreen AFTER Forge's
     * DrawScreenEvent.Post fires, so the Post render ends up beneath NEI. The RETURN
     * inject runs after all of NEI's in-drawScreen injections, so re-rendering here
     * paints the opaque overlay on top of NEI's highlights. Tooltip and held cursor
     * are drawn last so they stay above the overlay panel.
     */
    public void renderOverlayAtReturn(int mouseX, int mouseY, float partialTicks) {
        if (!overlayActive || overlay == null) return;
        renderOverlayPass(mouseX, mouseY, partialTicks);
    }

    private void renderOverlayPass(int mouseX, int mouseY, float partialTicks) {
        if (overlay == null) return;
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        overlay.draw(partialTicks);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
        renderCursorStack(mouseX, mouseY);
        overlay.drawHoveredItemTooltip(mouseX, mouseY);
    }

    private static void renderCursorStack(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        ItemStack cursor = mc.thePlayer.inventory.getItemStack();
        if (cursor == null) return;
        int cx = mouseX - 8;
        int cy = mouseY - 8;
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        net.minecraft.client.gui.Gui.drawRect(cx, cy, cx + 16, cy + 16, 0x00000000);
        net.minecraft.client.renderer.entity.RenderItem renderItem = new net.minecraft.client.renderer.entity.RenderItem();
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), cursor, cx, cy);
        String overlay = PocketStackSizeFormat.formatOverlayText(cursor.stackSize);
        renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), cursor, cx, cy, overlay);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(MouseEvent event) {
        // MouseEvent only fires while currentScreen == null (in-game). The overlay only
        // shows over GuiContainer, so this handler never routes overlay clicks. Actual
        // GUI mouse routing is done by MixinGuiScreen -> handleMouseInputForOverlay().
    }

    /**
     * Invoked from MixinGuiScreen#handleMouseInput (HEAD inject). Reads the current
     * LWJGL Mouse event and routes it to the overlay. Returns true when the overlay
     * consumed the event so the mixin cancels GuiScreen.handleMouseInput, preventing
     * the underlying GuiContainer (and NEI) from receiving the click.
     */
    public boolean handleMouseInputForOverlay() {
        if (!overlayActive || overlay == null) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiContainer)) return false;

        int rawX = org.lwjgl.input.Mouse.getEventX();
        int rawY = org.lwjgl.input.Mouse.getEventY();
        int evButton = org.lwjgl.input.Mouse.getEventButton();
        boolean evButtonState = org.lwjgl.input.Mouse.getEventButtonState();
        int evDwheel = org.lwjgl.input.Mouse.getEventDWheel();

        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(
            mc, mc.displayWidth, mc.displayHeight);
        int scaledX = rawX * sr.getScaledWidth() / mc.displayWidth;
        int scaledY = sr.getScaledHeight() - rawY * sr.getScaledHeight() / mc.displayHeight - 1;

        if (evDwheel != 0) {
            boolean wheelHandled = overlay.onMouseWheel(scaledX, scaledY, evDwheel);
            return wheelHandled;
        }

        // Move events (button == -1) update drag position in draw(); don't end drag here.
        if (!evButtonState) {
            if (evButton >= 0) {
                if (overlay.isDragging() && evButton == 0) {
                    overlay.onDragFinished();
                }
                overlay.onMouseReleased(scaledX, scaledY, evButton);
            }
            // Consume releases over the panel so the underlying GUI doesn't act on a
            // release without a matching press.
            return overlay.hitsPanel(scaledX, scaledY);
        }

        boolean ctrlHit = overlay.onMouseClicked(scaledX, scaledY, evButton);
        if (ctrlHit) {
            return true;
        }

        int slotInPage = overlay.getSlotAt(scaledX, scaledY);
        if (slotInPage >= 0) {
            int page = PocketClientCache.getCurrentPage();
            ItemStack cursorStack = mc.thePlayer.inventory.getItemStack();
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            if (evButton == 0) {
                if (shift && cursorStack == null) {
                    com.imgood.textech.AdvanceDataMonitor.ADMCHANEL
                        .sendToServer(PacketPocketAction.quickWithdraw(page, slotInPage));
                } else if (cursorStack != null) {
                    com.imgood.textech.AdvanceDataMonitor.ADMCHANEL
                        .sendToServer(PacketPocketAction.depositFromCursor(page, slotInPage));
                } else {
                    com.imgood.textech.AdvanceDataMonitor.ADMCHANEL
                        .sendToServer(PacketPocketAction.withdrawToCursor(page, slotInPage));
                }
            } else if (evButton == 1) {
                if (cursorStack == null) {
                    if (mc.thePlayer.isSneaking()) {
                        com.imgood.textech.AdvanceDataMonitor.ADMCHANEL
                            .sendToServer(PacketPocketAction.quickDeposit(page, slotInPage));
                    } else {
                        com.imgood.textech.AdvanceDataMonitor.ADMCHANEL
                            .sendToServer(PacketPocketAction.withdrawToCursor(page, slotInPage));
                    }
                } else {
                    com.imgood.textech.AdvanceDataMonitor.ADMCHANEL
                        .sendToServer(PacketPocketAction.depositSingleFromCursor(page, slotInPage));
                }
            }
            return true;
        }

        return false;
    }

    private static boolean isPocketNativeGui(GuiScreen screen) {
        return screen instanceof GuiPocketStorage || screen instanceof GuiDimensionalPocketConfig;
    }

    private void ensureOverlay(GuiContainer gui) {
        if (!PocketClientCache.isReceived()) {
            com.imgood.textech.AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.requestSync());
        }
        if (overlay == null) {
            overlay = new GuiPocketOverlay(gui);
        } else {
            overlay.attach(gui);
        }
        overlayActive = true;
    }

    private void teardown() {
        overlay = null;
        overlayActive = false;
    }
}
