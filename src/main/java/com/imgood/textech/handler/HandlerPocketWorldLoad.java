package com.imgood.textech.handler;

import java.io.File;

import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Sets per-world save directory for {@link PocketStore} when a server world loads.
 */
public class HandlerPocketWorldLoad {

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world instanceof WorldServer && !event.world.isRemote) {
            File worldDir = ((WorldServer) event.world).getSaveHandler()
                .getWorldDirectory();
            PocketStore.setWorldDirectory(worldDir);
        }
    }
}
