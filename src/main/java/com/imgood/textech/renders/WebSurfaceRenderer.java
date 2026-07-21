package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.client.WebSurfaceClientCache;
import com.imgood.textech.client.websurface.WebSurfaceFrame;
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
            renderSurface(nbt, null);
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

        WebSurfaceFrame frame = WebSurfaceSourceRouter.resolveFrame(
            nbt,
            nbt.getInteger("webTextureWidth"),
            bindingIndex,
            (int) Math.floor(x),
            (int) Math.floor(y),
            (int) Math.floor(z));
        if ((frame == null || !frame.isReady()) && !live && hash.length() == 64) {
            frame = WebSurfaceFrame
                .ofLocation(WebSurfaceClientCache.getTexture(hash, nbt.getInteger("webTextureWidth")));
        }
        renderSurface(nbt, frame);
    }

    private void renderSurface(NBTTagCompound nbt, WebSurfaceFrame frame) {
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

            if (frame != null && frame.isReady()) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                if (frame.hasGlTexture()) {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, frame.getGlTextureId());
                } else {
                    Minecraft.getMinecraft()
                        .getTextureManager()
                        .bindTexture(frame.getLocation());
                }
                GL11.glColor4f(1.0F, 1.0F, 1.0F, opacity);
                drawQuad(width, height, true, frame.isFlipV());
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(0.03F, 0.12F, 0.18F, Math.min(0.9F, opacity));
                drawQuad(width, height, false, false);
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
            OpenGlHelper
                .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousBrightnessX, previousBrightnessY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void drawQuad(double width, double height, boolean textured, boolean flipV) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        if (textured) {
            double t0 = flipV ? 0.0D : 1.0D;
            double t1 = flipV ? 1.0D : 0.0D;
            tessellator.addVertexWithUV(-width / 2.0D, -height / 2.0D, 0.0D, 0.0D, t0);
            tessellator.addVertexWithUV(width / 2.0D, -height / 2.0D, 0.0D, 1.0D, t0);
            tessellator.addVertexWithUV(width / 2.0D, height / 2.0D, 0.0D, 1.0D, t1);
            tessellator.addVertexWithUV(-width / 2.0D, height / 2.0D, 0.0D, 0.0D, t1);
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
