package com.imgood.textech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.imgood.textech.client.GuiPocketOverlay;
import com.imgood.textech.client.PocketOverlayHandler;

/**
 * Suppresses mouse coordinates passed to NEI widgets (item panel, bookmark
 * panel, ItemZoom, etc.) when the cursor is inside the Dimensional Pocket
 * overlay.
 *
 * NEI's coremod copies drawScreen coordinates before our
 * {@code @ModifyVariable} on GuiContainer.drawScreen runs, so the
 * {@code LayoutManager.renderObjects} callback receives real mouse
 * coordinates. This Mixin intercepts the coordinates directly at the
 * LayoutManager level, rewriting them to -9999 so that:
 *
 * - {@code widget.draw(-9999, -9999)} â†?no widget detects hover
 * - {@code ItemZoom} â†?no zoomed item preview rendered
 * - {@code ItemPanel/BookmarkPanel} â†?no slot highlight
 *
 * The NEI panel background and item grid remain visible â€?only hover-
 * dependent rendering (highlights, zoomed previews) is suppressed.
 *
 * During an overlay drag operation, suppression is unconditional because
 * the panel position lags by one frame (updatePositionFromMouse runs at
 * the RETURN inject, after NEI code).
 */
@Mixin(codechicken.nei.LayoutManager.class)
public abstract class MixinNeiLayoutManager {

    @ModifyVariable(method = "renderObjects(Lnet/minecraft/client/gui/inventory/GuiContainer;II)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private int adm$suppressWidgetMouseX(int mousex) {
        return shouldSuppress() ? -9999 : mousex;
    }

    @ModifyVariable(method = "renderObjects(Lnet/minecraft/client/gui/inventory/GuiContainer;II)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 2, remap = false)
    private int adm$suppressWidgetMouseY(int mousey) {
        return shouldSuppress() ? -9999 : mousey;
    }

    private static boolean shouldSuppress() {
        PocketOverlayHandler handler = PocketOverlayHandler.instance();
        if (!handler.isOverlayActive()) return false;
        GuiPocketOverlay overlay = handler.getOverlay();
        if (overlay == null) return false;
        // During drag, the overlay position lags by one frame because
        // updatePositionFromMouse() runs in draw() at the RETURN inject,
        // after NEI code. Suppress unconditionally while dragging to
        // prevent tooltip/metadata flashes from the item/bookmark panel
        // underneath.
        if (overlay.isDragging()) {
            return true;
        }
        int realX = handler.getRealMouseX();
        int realY = handler.getRealMouseY();
        if (realX >= 0 && realY >= 0 && overlay.hitsPanel(realX, realY)) {
            return true;
        }
        return false;
    }
}
