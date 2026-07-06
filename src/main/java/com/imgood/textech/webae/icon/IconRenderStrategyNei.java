package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class IconRenderStrategyNei implements IconRenderStrategy {

    @Override
    public IconRenderMode getMode() {
        return IconRenderMode.NEI;
    }

    @Override
    public byte[] renderItem(Minecraft mc, ItemStack stack, String itemId, IconRenderContext ctx) {
        IconExportResolver.ResolveResult result = ctx.exportResolver.resolve(mc, stack, itemId, null);
        recordSource(ctx, result.source);
        return result.png;
    }

    private static void recordSource(IconRenderContext ctx, IconExportResolver.Source source) {
        switch (source) {
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
    }
}
