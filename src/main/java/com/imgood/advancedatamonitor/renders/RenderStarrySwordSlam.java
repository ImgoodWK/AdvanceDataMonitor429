package com.imgood.advancedatamonitor.renders;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.entity.EntityStarrySwordLineStab;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordSlam;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderStarrySwordSlam extends Render {

    public RenderStarrySwordSlam() {
        shadowSize = 0.0F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        ItemStack stack = null;
        float displayYaw = 0.0F;
        float renderScale = StarryCosmosWorldRenderer.SCALE_SLAM;
        if (entity instanceof EntityStarrySwordSlam) {
            EntityStarrySwordSlam slam = (EntityStarrySwordSlam) entity;
            stack = slam.getDisplayedStack();
            displayYaw = slam.getDisplayYaw();
        } else if (entity instanceof EntityStarrySwordLineStab) {
            EntityStarrySwordLineStab stab = (EntityStarrySwordLineStab) entity;
            stack = stab.getDisplayedStack();
            displayYaw = stab.getDisplayYaw();
            renderScale = stab.getRenderScale();
        }
        if (stack == null) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        StarryCosmosWorldRenderer.renderSlamBlade(entity, stack, displayYaw, renderScale);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TextureMap.locationItemsTexture;
    }
}
