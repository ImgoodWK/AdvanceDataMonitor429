package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class IconRenderStrategyFirstPerson implements IconRenderStrategy {

    @Override
    public IconRenderMode getMode() {
        return IconRenderMode.FIRST_PERSON;
    }

    @Override
    public byte[] renderItem(Minecraft mc, ItemStack stack, String itemId, IconRenderContext ctx) {
        byte[] png = ctx.glFallback.renderFirstPersonIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            ctx.glFallbackCount++;
            return png;
        }
        return null;
    }
}
