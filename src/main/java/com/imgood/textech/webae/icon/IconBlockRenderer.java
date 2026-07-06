package com.imgood.textech.webae.icon;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Renders {@link ItemBlock} stacks as a mini block scene (RenderBlocks FBO pass).
 */
@SideOnly(Side.CLIENT)
final class IconBlockRenderer {

    private IconBlockRenderer() {}

    static byte[] render(Minecraft mc, ItemStack stack, IconGlFallback glFallback) {
        if (stack == null || glFallback == null) return null;
        Item item = stack.getItem();
        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).field_150939_a;
            if (block != null) {
                byte[] png = glFallback.renderBlockAsItem(mc, block, stack.getItemDamage());
                if (!IconAtlasSampler.isPngBlank(png)) return png;
            }
        }
        return glFallback.renderEntityIcon(mc, stack);
    }
}
