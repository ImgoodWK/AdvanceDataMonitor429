package com.imgood.textech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.imgood.textech.client.GuiPocketOverlay;
import com.imgood.textech.client.PocketOverlayHandler;

/**
 * Cancels NEI tooltip rendering when the cursor is inside the Dimensional
 * Pocket overlay or when the overlay is being dragged.
 *
 * During drag, the overlay panel position lags by one frame (it is updated
 * in {@code updatePositionFromMouse()} at the RETURN inject, after NEI's
 * in-drawScreen code runs). The {@code hitsPanel} check with the stale
 * position can miss fast mouse movement, causing tooltip flashes. To
 * prevent this, tooltip rendering is unconditionally cancelled while a
 * drag is in progress.
 *
 * Uses the REAL mouse coordinates saved by {@code MixinGuiContainer} before
 * suppression, rather than the method parameters which may have been
 * partially suppressed by {@code @ModifyVariable}.
 */
@Mixin(codechicken.nei.guihook.GuiContainerManager.class)
public abstract class MixinNeiRenderToolTips {

    @Inject(method = "renderToolTips(II)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void adm$cancelNeiTooltips(int mousex, int mousey, CallbackInfo ci) {
        PocketOverlayHandler handler = PocketOverlayHandler.instance();
        if (!handler.isOverlayActive()) return;
        GuiPocketOverlay overlay = handler.getOverlay();
        if (overlay == null) return;

        // During drag, cancel tooltips unconditionally —the overlay
        // position is stale (one frame behind) and hitsPanel can miss.
        if (overlay.isDragging()) {
            ci.cancel();
            return;
        }

        // Use REAL mouse coords saved pre-suppression for the hit test.
        int realX = handler.getRealMouseX();
        int realY = handler.getRealMouseY();
        if (realX >= 0 && realY >= 0 && overlay.hitsPanel(realX, realY)) {
            ci.cancel();
        }
    }
}
