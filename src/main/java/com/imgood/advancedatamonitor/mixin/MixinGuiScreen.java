package com.imgood.advancedatamonitor.mixin;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.imgood.advancedatamonitor.client.PocketOverlayHandler;

/**
 * Hooks GuiScreen.handleMouseInput so the Dimensional Pocket overlay can
 * intercept clicks before the underlying GuiContainer (and NEI) process them.
 *
 * 1.7.10 Forge's MouseEvent only fires in-game (currentScreen == null) and
 * GuiScreenEvent has no mouse-input sub-event, so there is no pure-Forge way to
 * cancel GUI mouse input. This HEAD inject lets the overlay consume the current
 * LWJGL Mouse event and cancel handleMouseInput, blocking the click from
 * reaching the underlying container.
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void adm$onHandleMouseInput(CallbackInfo ci) {
        if (PocketOverlayHandler.instance()
            .handleMouseInputForOverlay()) {
            ci.cancel();
        }
    }
}
