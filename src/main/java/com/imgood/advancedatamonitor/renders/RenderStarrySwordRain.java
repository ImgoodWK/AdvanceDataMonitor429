package com.imgood.advancedatamonitor.renders;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.entity.EntityStarrySwordRain;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderStarrySwordRain extends Render {

    public RenderStarrySwordRain() {
        shadowSize = 0.15F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (!(entity instanceof EntityStarrySwordRain)) {
            return;
        }
        EntityStarrySwordRain rain = (EntityStarrySwordRain) entity;
        ItemStack stack = rain.getDisplayedStack();
        if (stack == null) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        if (rain.isStuck()) {
            StarryCosmosWorldRenderer
                .renderRainStuck(rain, stack, rain.getStickYaw(), rain.getStickPitch(), rain.getStickRoll());
        } else {
            StarryCosmosWorldRenderer.renderRainFalling(rain, stack, rain.getFallYaw());
        }

        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TextureMap.locationItemsTexture;
    }
}
