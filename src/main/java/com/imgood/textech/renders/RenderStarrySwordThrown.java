package com.imgood.textech.renders;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.entity.EntityStarrySwordThrown;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderStarrySwordThrown extends Render {

    public RenderStarrySwordThrown() {
        shadowSize = 0.25F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        if (!(entity instanceof EntityStarrySwordThrown)) {
            return;
        }
        EntityStarrySwordThrown thrown = (EntityStarrySwordThrown) entity;
        ItemStack stack = thrown.getDisplayedStack();
        if (stack == null) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        StarryCosmosWorldRenderer
            .renderThrownBlade(thrown, stack, thrown.getDirX(), thrown.getDirY(), thrown.getDirZ());
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TextureMap.locationItemsTexture;
    }
}
