package com.imgood.textech.mixin;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.imgood.textech.client.PocketStackSizeFormat;
import com.imgood.textech.handler.PocketInventory;

/**
 * Pocket storage GUI slots use portable-infinity-chest stack overlay text (1K / 1M â€?
 * instead of raw {@link ItemStack#stackSize} or silent overlays when count exceeds 64.
 */
@Mixin(GuiContainer.class)
public abstract class MixinPocketDrawSlotOverlay {

    @Redirect(
        method = "func_146977_a(Lnet/minecraft/inventory/Slot;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V"))
    private void adm$pocketDrawSlotOverlay(
        RenderItem renderItem,
        FontRenderer font,
        TextureManager textureManager,
        ItemStack stack,
        int x,
        int y,
        String altText,
        Slot slotIn) {
        if (stack != null && slotIn != null && slotIn.inventory instanceof PocketInventory) {
            altText = PocketStackSizeFormat.formatOverlayText(stack.stackSize);
        }
        renderItem.renderItemOverlayIntoGUI(font, textureManager, stack, x, y, altText);
    }
}
