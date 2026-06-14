package com.imgood.advancedatamonitor.entity;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * Placeholder renderer for the drone — renders a solid white cube.
 * No texture file needed. Overrides doRender to avoid texture binding.
 * Replace with proper model renderer once artwork is ready.
 */
public class RenderDrone extends RenderLiving {

    public RenderDrone() {
        super(new ModelDrone(), 0.3f);
        this.shadowSize = 0.2f;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    @Override
    public void doRender(EntityLivingBase entity, double x, double y, double z,
        float entityYaw, float partialTicks) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.95f);
            GL11.glDisable(GL11.GL_LIGHTING);

            GL11.glTranslatef((float) x, (float) y, (float) z);

            // Billboard rotation to face camera
            GL11.glRotatef(180.0f - this.renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
            GL11.glRotatef(-this.renderManager.playerViewX, 1.0f, 0.0f, 0.0f);

            // Hover animation
            float hover = (float) Math.sin((entity.ticksExisted + partialTicks) * 0.1f) * 0.1f;
            GL11.glTranslatef(0.0f, hover, 0.0f);

            this.mainModel.render(entity, 0.0f, 0.0f, entity.ticksExisted + partialTicks, 0.0f, 0.0f, 0.0625f);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }

        // Debug bounding box not supported in 1.7.10 RenderManager API
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z,
        float entityYaw, float partialTicks) {
        this.doRender((EntityLivingBase) entity, x, y, z, entityYaw, partialTicks);
    }

}
