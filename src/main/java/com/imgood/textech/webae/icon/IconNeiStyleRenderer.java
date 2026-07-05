package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI-style slot layout ({@code GuiContainer.drawSlot} spacing). Falls back to inventory GL when blank.
 */
@SideOnly(Side.CLIENT)
final class IconNeiStyleRenderer {

    private static volatile Boolean neiPresent;

    private IconNeiStyleRenderer() {}

    static boolean isNeiPresent() {
        if (neiPresent == null) {
            try {
                Class.forName("codechicken.nei.guihook.GuiContainerManager");
                neiPresent = Boolean.TRUE;
            } catch (Throwable ignored) {
                neiPresent = Boolean.FALSE;
            }
        }
        return neiPresent.booleanValue();
    }

    static byte[] render(Minecraft mc, ItemStack stack, IconGlFallback glFallback) {
        if (stack == null || glFallback == null) return null;
        byte[] png = glFallback.renderNeiSlotIcon(mc, stack);
        if (!IconAtlasSampler.isPngBlank(png)) return png;
        return glFallback.renderInventoryIcon(mc, stack);
    }
}
