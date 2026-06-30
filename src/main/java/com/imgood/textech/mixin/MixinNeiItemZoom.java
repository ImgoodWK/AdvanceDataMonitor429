package com.imgood.textech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.imgood.textech.client.GuiPocketOverlay;
import com.imgood.textech.client.PocketOverlayHandler;

/**
 * Cancels NEI's ItemZoom (magnified item preview on the sides of the GUI)
 * when the cursor is inside the Dimensional Pocket overlay.
 *
 * ItemZoom.draw(mx, my) ignores the mx/my parameters entirely —it renders
 * based on {@code this.stack}, which was pre-set by
 * {@code ItemZoom.resize()} using {@code GuiDraw.getMousePosition()}
 * (the real LWJGL mouse position, not the passed-in parameters).
 *
 * Since @ModifyVariable on LayoutManager.renderObjects cannot stop
 * resize() from capturing the real mouse, the only reliable way to
 * suppress the zoomed preview is to cancel the draw() call itself when
 * the overlay covers the mouse.
 */
@Mixin(codechicken.nei.ItemZoom.class)
public abstract class MixinNeiItemZoom {

    @Inject(method = "draw(II)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void adm$cancelItemZoom(int mx, int my, CallbackInfo ci) {
        PocketOverlayHandler handler = PocketOverlayHandler.instance();
        if (!handler.isOverlayActive()) return;
        GuiPocketOverlay overlay = handler.getOverlay();
        if (overlay == null) return;
        if (overlay.isDragging()) {
            ci.cancel();
            return;
        }
        int realX = handler.getRealMouseX();
        int realY = handler.getRealMouseY();
        if (realX >= 0 && realY >= 0 && overlay.hitsPanel(realX, realY)) {
            ci.cancel();
        }
    }
}
