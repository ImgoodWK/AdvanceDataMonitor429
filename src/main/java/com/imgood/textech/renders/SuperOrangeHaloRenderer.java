package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.items.ItemSuperOrange;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Renders a rotating halo texture (OrangeBuff.png) above players who carry
 * an ItemSuperOrange. Visible to other players and to yourself in third person only.
 */
public class SuperOrangeHaloRenderer {

    private static final ResourceLocation HALO_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/items/OrangeBuff.png");

    private static final float HALO_SCALE = 2.0f;
    private static final float HALO_HEIGHT = 1.8f;
    private static final float ROTATION_SPEED = 0.5f; // degrees per tick equivalent

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        float partialTicks = event.partialTicks;
        RenderManager renderManager = RenderManager.instance;

        for (Object entityObj : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) entityObj;
            if (player == null || player.isDead) continue;

            if (!hasSuperOrangeItem(player)) continue;
            if (!SuperOrangeHeadRenderUtil.shouldRenderHeadEffects(player)) continue;

            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

            x -= renderManager.viewerPosX;
            y -= renderManager.viewerPosY;
            z -= renderManager.viewerPosZ;

            renderHalo(x, y + HALO_HEIGHT, z, player, partialTicks, renderManager);
        }
    }

    private boolean hasSuperOrangeItem(EntityPlayer player) {
        if (player == null || player.inventory == null) return false;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemSuperOrange) {
                return true;
            }
        }
        return false;
    }

    private void renderHalo(double x, double y, double z, EntityPlayer player, float partialTicks,
        RenderManager renderManager) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);

            // Billboard: always face the camera, but keep Y rotation for spin effect
            GL11.glRotatef(-renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
            GL11.glRotatef(renderManager.playerViewX, 1.0f, 0.0f, 0.0f);

            // Slow continuous rotation
            float rotation = (player.ticksExisted + partialTicks) * ROTATION_SPEED;
            GL11.glRotatef(rotation, 0.0f, 0.0f, 1.0f);

            float halfSize = HALO_SCALE * 0.5f;

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(HALO_TEXTURE);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.85f);

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(-halfSize, -halfSize, 0.0, 0.0, 0.0);
            tessellator.addVertexWithUV(halfSize, -halfSize, 0.0, 1.0, 0.0);
            tessellator.addVertexWithUV(halfSize, halfSize, 0.0, 1.0, 1.0);
            tessellator.addVertexWithUV(-halfSize, halfSize, 0.0, 0.0, 1.0);
            tessellator.draw();

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
