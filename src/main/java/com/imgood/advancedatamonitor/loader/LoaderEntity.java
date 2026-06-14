package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.entity.EntityDrone;

import cpw.mods.fml.common.registry.EntityRegistry;

public class LoaderEntity {

    private static int entityIdOffset = 0;

    public static void registerEntities() {
        int id = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(EntityDrone.class, "admDrone", id);
        EntityRegistry.registerModEntity(EntityDrone.class, "admDrone", id, AdvanceDataMonitor.instance, 64, 1, true);
    }
}
