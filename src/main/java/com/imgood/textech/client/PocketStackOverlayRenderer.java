package com.imgood.textech.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Renders pocket slot items with infinity-chest-style stack count overlays (K / M / G). */
@SideOnly(Side.CLIENT)
public final class PocketStackOverlayRenderer {

    private PocketStackOverlayRenderer() {}

    public static void renderSlotItem(
        RenderItem renderItem,
        FontRenderer font,
        TextureManager textureManager,
        ItemStack stack,
        int x,
        int y) {
        if (stack == null || renderItem == null) return;
        renderItem.renderItemAndEffectIntoGUI(font, textureManager, stack, x, y);
        String overlay = PocketStackSizeFormat.formatOverlayText(stack.stackSize);
        renderItem.renderItemOverlayIntoGUI(font, textureManager, stack, x, y, overlay);
    }
}
