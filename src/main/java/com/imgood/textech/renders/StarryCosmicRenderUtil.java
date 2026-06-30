package com.imgood.textech.renders;

import net.minecraft.world.World;

import fox.spiteful.avaritia.render.CosmicRenderShenanigans;

/**
 * Delegates to Avaritia cosmic shader pipeline (Universium via GTNHLib at runtime).
 */
public final class StarryCosmicRenderUtil {

    public static boolean inventoryRender = false;
    public static float cosmicOpacity = 1.0f;

    private StarryCosmicRenderUtil() {}

    public static void useShader() {
        CosmicRenderShenanigans.inventoryRender = inventoryRender;
        CosmicRenderShenanigans.cosmicOpacity = cosmicOpacity;
        CosmicRenderShenanigans.useShader();
    }

    public static void releaseShader() {
        CosmicRenderShenanigans.releaseShader();
    }

    public static void setLightFromLocation(World world, int x, int y, int z) {
        CosmicRenderShenanigans.setLightFromLocation(world, x, y, z);
    }

    public static void setLightLevel(float level) {
        CosmicRenderShenanigans.setLightLevel(level);
    }
}
