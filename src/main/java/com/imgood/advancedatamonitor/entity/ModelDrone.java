package com.imgood.advancedatamonitor.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

/**
 * Placeholder cube model for the drone. A simple 6×6×6 white cube.
 * Replace with proper model once art is ready.
 */
public class ModelDrone extends ModelBase {

    private final ModelRenderer cube;

    public ModelDrone() {
        this.textureWidth = 64;
        this.textureHeight = 32;

        cube = new ModelRenderer(this, 0, 0);
        cube.addBox(-3.0f, -3.0f, -3.0f, 6, 6, 6);
        cube.setRotationPoint(0.0f, 3.0f, 0.0f);
    }

    public void render(float time) {
        cube.render(0.0625f);
    }
}
