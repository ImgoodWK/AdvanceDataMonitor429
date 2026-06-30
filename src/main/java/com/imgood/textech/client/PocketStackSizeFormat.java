package com.imgood.textech.client;

import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Stack count overlay text for pocket slots â€?same tiers as Science Not Leisure
 * {@code MixinGuiContainer#gtnl$humanReadableValue} (portable infinity chest).
 */
@SideOnly(Side.CLIENT)
public final class PocketStackSizeFormat {

    private PocketStackSizeFormat() {}

    /** Alt text for {@link net.minecraft.client.renderer.entity.RenderItem#renderItemOverlayIntoGUI}. */
    public static String formatOverlayText(int stackSize) {
        if (stackSize <= 0) {
            return EnumChatFormatting.YELLOW + "0";
        }
        if (stackSize < 1000) {
            return String.valueOf(stackSize);
        }
        if (stackSize < 1_000_000) {
            return (stackSize / 1000) + "K";
        }
        if (stackSize <= 1_000_000_000) {
            return (stackSize / 1_000_000) + "M";
        }
        return (stackSize / 1_000_000_000) + "G";
    }
}
