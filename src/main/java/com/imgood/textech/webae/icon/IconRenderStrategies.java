package com.imgood.textech.webae.icon;

import java.util.HashMap;
import java.util.Map;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class IconRenderStrategies {

    private static final Map<IconRenderMode, IconRenderStrategy> BY_MODE = new HashMap<IconRenderMode, IconRenderStrategy>();

    static {
        register(new IconRenderStrategyAtlas());
        register(new IconRenderStrategyHybrid());
        register(new IconRenderStrategyInventoryGl());
        register(new IconRenderStrategyInventoryFlat());
        register(new IconRenderStrategyEntity());
        register(new IconRenderStrategyBlock());
        register(new IconRenderStrategyFirstPerson());
        register(new IconRenderStrategyNei());
    }

    private IconRenderStrategies() {}

    private static void register(IconRenderStrategy strategy) {
        BY_MODE.put(strategy.getMode(), strategy);
    }

    public static IconRenderStrategy get(IconRenderMode mode) {
        if (mode == null) mode = IconRenderMode.HYBRID;
        IconRenderStrategy s = BY_MODE.get(mode);
        if (s != null) return s;
        return BY_MODE.get(IconRenderMode.HYBRID);
    }
}
