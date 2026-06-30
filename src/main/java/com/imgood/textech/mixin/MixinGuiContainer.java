package com.imgood.textech.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.imgood.textech.client.GuiPocketOverlay;
import com.imgood.textech.client.PocketOverlayHandler;

/**
 * Coordinates the real-mouse-coordinate pipeline for the Dimensional
 * Pocket overlay and ensures the overlay renders on top of NEI.
 *
 * Coordinate flow:
 * 1. {@code adm$saveMouseX} â€?captures the original mouseX to {@code adm_x}
 * 2. {@code adm$suppressMouseY} â€?with both coordinates known, checks
 *    {@code hitsPanel}; if true, saves the real pair to
 *    {@code handler.setRealMouse()} and returns -9999 for mouseY
 * 3. NEI's coremod-injected code reads the (possibly -9999) mouseY from
 *    the local variable slot; NEI's inline hover detection sees out-of-
 *    bounds coordinates and produces no highlights. {@code renderToolTips}
 *    is independently cancelled by MixinNeiRenderToolTips. The NEI item
 *    panel itself remains visible behind the overlay's opaque background.
 * 4. {@code adm$onDrawScreenReturn} â€?re-renders the opaque overlay
 *    on top of all NEI rendering via {@code renderOverlayAtReturn}
 *
 * Both mouseX (ordinal 0) and mouseY (ordinal 1) are intercepted at
 * {@code @At("HEAD")} of {@code drawScreen(IIF)V}.
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer {

    private static int adm_x = 0;

    /**
     * Ordinal 0 â€?mouseX.  Solely saves the original X to {@code adm_x}
     * for later use by ordinal 1 (mouseY).  Does NOT suppress X here
     * because we don't yet know mouseY and a false-positive suppression
     * would corrupt NEI tooltip coordinates (X=-9999, Y=real).
     *
     * The actual suppression and real-coord saving is done by ordinal 1
     * once both coordinates are available.  Tooltip cancellation is
     * handled by MixinNeiRenderToolTips.
     */
    @ModifyVariable(method = "drawScreen(IIF)V", at = @At("HEAD"),
        argsOnly = true, ordinal = 0)
    private int adm$saveMouseX(int mouseX) {
        adm_x = mouseX;
        return mouseX;
    }

    @ModifyVariable(method = "drawScreen(IIF)V", at = @At("HEAD"),
        argsOnly = true, ordinal = 1)
    private int adm$suppressMouseY(int mouseY) {
        int mx = adm_x;
        int my = mouseY;
        PocketOverlayHandler handler = PocketOverlayHandler.instance();
        if (handler.isOverlayActive()) {
            GuiPocketOverlay overlay = handler.getOverlay();
            if (overlay != null && overlay.hitsPanel(mx, my)) {
                handler.setRealMouse(mx, my);
                return -9999;
            }
            handler.clearRealMouse();
        }
        return mouseY;
    }

    @Inject(method = "drawScreen(IIF)V", at = @At("RETURN"))
    private void adm$onDrawScreenReturn(int mouseX, int mouseY,
                                        float partialTicks, CallbackInfo ci) {
        PocketOverlayHandler handler = PocketOverlayHandler.instance();
        int rx = handler.getRealMouseX();
        int ry = handler.getRealMouseY();
        handler.renderOverlayAtReturn(
            rx >= 0 ? rx : mouseX,
            ry >= 0 ? ry : mouseY,
            partialTicks);
    }
}
