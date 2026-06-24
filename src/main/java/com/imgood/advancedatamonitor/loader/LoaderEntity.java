package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.entity.EntityGrappleSlide;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordLineSlash;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordLineStab;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordRain;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordRainField;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordSlam;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordThrown;
import com.imgood.advancedatamonitor.entity.EntitySuperOrangeDrone;

import cpw.mods.fml.common.registry.EntityRegistry;

public class LoaderEntity {

    private static int entityIdOffset = 0;

    public static void registerEntities() {
        int droneId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(EntitySuperOrangeDrone.class, "admSuperDrone", droneId);
        EntityRegistry.registerModEntity(
            EntitySuperOrangeDrone.class,
            "admSuperDrone",
            droneId,
            AdvanceDataMonitor.instance,
            64,
            1,
            true);

        int slideId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(EntityGrappleSlide.class, "admGrappleSlide", slideId);
        EntityRegistry.registerModEntity(
            EntityGrappleSlide.class,
            "admGrappleSlide",
            slideId,
            AdvanceDataMonitor.instance,
            80,
            1,
            false);

        registerEntity(EntityStarrySwordLineSlash.class, "admStarryLineSlash", 64, 1, false);
        registerEntity(EntityStarrySwordLineStab.class, "admStarryLineStab", 64, 2, false);
        registerEntity(EntityStarrySwordThrown.class, "admStarryThrown", 64, 2, true);
        registerEntity(EntityStarrySwordRainField.class, "admStarryRainField", 64, 5, false);
        registerEntity(EntityStarrySwordRain.class, "admStarryRain", 64, 2, true);
        registerEntity(EntityStarrySwordSlam.class, "admStarrySlam", 80, 1, false);
    }

    private static void registerEntity(Class<? extends net.minecraft.entity.Entity> clazz, String name, int range,
        int updateFreq, boolean sendVelocity) {
        int id = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(clazz, name, id);
        EntityRegistry.registerModEntity(clazz, name, id, AdvanceDataMonitor.instance, range, updateFreq, sendVelocity);
    }
}
