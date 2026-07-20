package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.client.WebSurfaceClientCache;
import com.imgood.textech.client.websurface.WebSurfaceSourceRouter;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Generic textured web-surface renderer. Supports snapshot and live dashboard/url sources via
 * {@link WebSurfaceSourceRouter}; monitor transforms stay shared across modes.
 */
@SideOnly(Side.CLIENT)
public class WebSurfaceRenderer implements IADMRender {

    private static final double BASE_WIDTH = 8.0D;

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing, int bindingIndex) {
        if (!nbt.getBoolean("enable")) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) return;

        String mode = nbt.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY);
        boolean live = TileEntityAdvanceDataMonitor.MODE_DASHBOARD_LIVE.equals(mode)
            || TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(mode);
        String hash = nbt.getString(TileEntityAdvanceDataMonitor.WEB_DASHBOARD_HASH_KEY);

        if (!live && hash.length() != 64) {
            renderPlaceholder(nbt, null);
            return;
        }
        if (!live && !WebSurfaceClientCache.hasContent(hash)) {
            WebSurfaceClientCache.requestContentIfNeeded(
                minecraft.theWorld.provider.dimensionId,
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.floor(z),
                bindingIndex,
                hash);
        }

        ResourceLocation texture = WebSurfaceSourceRouter.resolveTexture(
            nbt,
            nbt.getInteger("webTextureWidth"),
            bindingIndex,
            (int) Math.floor(x),
            (int) Math.floor(y),
            (int) Math.floor(z));
        if (texture == null && !live && hash.length() == 64) {
            texture = WebSurfaceClientCache.getTexture(hash, nbt.getInteger("webTextureWidth"));
        }
        renderPlaceholder(nbt, texture);
    }

    private void renderPlaceholder(NBTTagCompound nbt, ResourceLocation texture) {
        int viewportWidth = Math.max(64, nbt.getInteger("webDashboardViewportWidth"));
        int viewportHeight = Math.max(64, nbt.getInteger("webDashboardViewportHeight"));
        double width = BASE_WIDTH;
        double height = width * viewportHeight / viewportWidth;
        float opacity = nbt.hasKey("webOpacity") ? nbt.getFloat("webOpacity") : 1.0F;
        opacity = Math.max(0.05F, Math.min(1.0F, opacity));
        float previousBrightnessX = OpenGlHelper.lastBrightnessX;
        float previousBrightnessY = OpenGlHelper.lastBrightnessY;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(nbt.getFloat("rotationX"), 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(nbt.getFloat("rotationY"), 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(nbt.getFloat("rotationZ"), 0.0F, 0.0F, 1.0F);
            float scale = nbt.hasKey("scale") ? nbt.getFloat("scale") : 0.3F;
            GL11.glScalef(scale, scale, scale);

            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (!nbt.hasKey("webFullBright") || nbt.getBoolean("webFullBright")) {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            }

            if (texture != null) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(texture);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, opacity);
                drawQuad(width, height, true);
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(0.03F, 0.12F, 0.18F, Math.min(0.9F, opacity));
                drawQuad(width, height, false);
                GL11.glLineWidth(2.0F);
                GL11.glColor4f(0.0F, 0.85F, 1.0F, opacity);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex3d(-width / 2.0D, -height / 2.0D, 0.002D);
                GL11.glVertex3d(width / 2.0D, -height / 2.0D, 0.002D);
                GL11.glVertex3d(width / 2.0D, height / 2.0D, 0.002D);
                GL11.glVertex3d(-width / 2.0D, height / 2.0D, 0.002D);
                GL11.glEnd();
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                previousBrightnessX,
                previousBrightnessY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void drawQuad(double width, double height, boolean textured) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        if (textured) {
            tessellator.addVertexWithUV(-width / 2.0D, -height / 2.0D, 0.0D, 0.0D, 1.0D);
            tessellator.addVertexWithUV(width / 2.0D, -height / 2.0D, 0.0D, 1.0D, 1.0D);
            tessellator.addVertexWithUV(width / 2.0D, height / 2.0D, 0.0D, 1.0D, 0.0D);
            tessellator.addVertexWithUV(-width / 2.0D, height / 2.0D, 0.0D, 0.0D, 0.0D);
        } else {
            tessellator.addVertex(-width / 2.0D, -height / 2.0D, 0.0D);
            tessellator.addVertex(width / 2.0D, -height / 2.0D, 0.0D);
            tessellator.addVertex(width / 2.0D, height / 2.0D, 0.0D);
            tessellator.addVertex(-width / 2.0D, height / 2.0D, 0.0D);
        }
        tessellator.draw();
    }

    @Override
    public void cleanup() {}
}
