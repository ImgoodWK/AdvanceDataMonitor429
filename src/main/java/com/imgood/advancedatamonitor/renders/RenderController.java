package com.imgood.advancedatamonitor.renders;

import java.util.HashMap;
import java.util.Map;

public class RenderController {

    private static final Map<String, IADMRender> RENDERERS = new HashMap<>();

    public static void registerRenderer(String type, IADMRender renderer) {
        RENDERERS.put(type, renderer);
    }

    public static IADMRender getRenderer(String type) {
        return RENDERERS.getOrDefault(type, null);
    }
}
