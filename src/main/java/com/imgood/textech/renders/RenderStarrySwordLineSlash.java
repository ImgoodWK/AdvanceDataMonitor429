package com.imgood.textech.renders;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Line slash coordinator â€?visuals are particles (entity client) + {@link EntityStarrySwordLineStab} swords. */
@SideOnly(Side.CLIENT)
public class RenderStarrySwordLineSlash extends Render {

    public RenderStarrySwordLineSlash() {
        shadowSize = 0.0F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {}

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
