package com.imgood.textech.webae.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * NEI-style slot layout ({@code GuiContainer.drawSlot} spacing). Delegates to {@link IconExportResolver}.
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

    static byte[] render(Minecraft mc, ItemStack stack, String itemId, IconExportResolver resolver) {
        if (stack == null || resolver == null) return null;
        return resolver.resolve(mc, stack, itemId, null).png;
    }
}
