package com.imgood.advancedatamonitor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

import com.imgood.advancedatamonitor.handler.StarryCosmosSounds;
import com.imgood.advancedatamonitor.renders.StarryCosmicRenderUtil;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Preloads Universium cosmic shader and custom sword sounds on first world join so skill use
 * does not hitch the render thread on lazy initialization.
 */
@SideOnly(Side.CLIENT)
public final class StarryCosmosClientWarmup {

    private static boolean warmedUp;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (warmedUp || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        warmedUp = true;
        warmupShader();
        warmupSounds(mc);
    }

    private static void warmupShader() {
        StarryCosmicRenderUtil.inventoryRender = true;
        StarryCosmicRenderUtil.useShader();
        StarryCosmicRenderUtil.releaseShader();

        StarryCosmicRenderUtil.inventoryRender = false;
        StarryCosmicRenderUtil.useShader();
        StarryCosmicRenderUtil.releaseShader();
    }

    private static void warmupSounds(Minecraft mc) {
        preloadSound(mc, StarryCosmosSounds.MELEE_HIT);
        preloadSound(mc, StarryCosmosSounds.SLASH);
        preloadSound(mc, StarryCosmosSounds.THROW);
        preloadSound(mc, StarryCosmosSounds.SLAM);
        preloadSound(mc, StarryCosmosSounds.STAB_FALL);
        preloadSound(mc, StarryCosmosSounds.STAB_IMPACT);
        preloadSound(mc, StarryCosmosSounds.RAIN_START);
        preloadSound(mc, StarryCosmosSounds.RAIN_TICK);
        preloadSound(mc, StarryCosmosSounds.JUDGMENT);
    }

    private static void preloadSound(Minecraft mc, String soundName) {
        int colon = soundName.indexOf(':');
        if (colon <= 0 || colon >= soundName.length() - 1) {
            return;
        }
        ResourceLocation location = new ResourceLocation(soundName.substring(0, colon), soundName.substring(colon + 1));
        mc.getSoundHandler()
            .playSound(new PositionedSoundRecord(location, 0.001F, 1.0F, 0.0F, 0.0F, 0.0F));
    }
}
