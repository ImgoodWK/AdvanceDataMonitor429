package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;

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
        byte[] png = ctx.glFallback.renderInventoryIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            ctx.glFallbackCount++;
            return png;
        }
        png = ctx.glFallback.renderNeiSlotIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) {
            ctx.glFallbackCount++;
            return png;
        }
        AdvanceDataMonitor.LOG.debug("[WebAE] Icon GL render failed for '{}', using placeholder", itemId);
        ctx.placeholderCount++;
        return IconRenderer.createPlaceholderPng(itemId);
    }
}
