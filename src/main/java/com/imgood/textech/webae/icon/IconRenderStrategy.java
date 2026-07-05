package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Strategy for rendering a single item icon PNG for a given {@link IconRenderMode}.
 */
@SideOnly(Side.CLIENT)
public interface IconRenderStrategy {

    IconRenderMode getMode();

    /**
     * Render one item stack to PNG bytes. Returns {@code null} or blank PNG when rendering fails.
     */
    byte[] renderItem(Minecraft mc, ItemStack stack, String itemId, IconRenderContext ctx);
}
