package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class IconRenderStrategyHybrid implements IconRenderStrategy {

    @Override
    public IconRenderMode getMode() {
        return IconRenderMode.HYBRID;
    }

    @Override
    public byte[] renderItem(Minecraft mc, ItemStack stack, String itemId, IconRenderContext ctx) {
        IconExportResolver.ResolveResult result = ctx.exportResolver.resolve(mc, stack, itemId, null);
        switch (result.source) {
            case ATLAS:
                ctx.atlasSampleCount++;
                break;
            case PLACEHOLDER:
                ctx.placeholderCount++;
                break;
            default:
                ctx.glFallbackCount++;
                break;
        }
        return result.png;
    }
}
