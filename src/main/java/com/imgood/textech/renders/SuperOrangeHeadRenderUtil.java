package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.imgood.textech.Config;

/**
 * Visibility rules for Super Orange halo / nameplate head effects.
 */
public final class SuperOrangeHeadRenderUtil {

    private SuperOrangeHeadRenderUtil() {}

    /**
     * Whether halo and nameplate should be drawn for the given player on this client.
     * Other players are always eligible; the local player is shown only in third person.
     */
    public static boolean shouldRenderHeadEffects(EntityPlayer player) {
        if (!Config.superOrangeHeadEffectsEnabled || player == null) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return false;
        }

        if (player != mc.thePlayer) {
            return true;
        }

        return mc.gameSettings.thirdPersonView != 0;
    }
}
