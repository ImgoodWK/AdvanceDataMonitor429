package com.imgood.advancedatamonitor.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.items.ItemOrange;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Renders a billboard nameplate above players who have an ItemOrange in their
 * inventory. The nameplate always faces the camera (like multiplayer player
 * nametags) and is visible to the player themselves.
 */
public class OrangeNameplateRenderer {

    private static final float NAME_SCALE = 0.025f;
    private static final float HEIGHT_OFFSET = 0.5f;
    private static final int NAME_COLOR = 0xFFFFAA00;
    private static final int BG_COLOR = 0x40000000;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        float partialTicks = event.partialTicks;
        RenderManager renderManager = RenderManager.instance;

        for (Object entityObj : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) entityObj;
            if (player == null || player.isDead) continue;

            ItemStack orange = findOrangeItem(player);
            if (orange == null) continue;

            String name = ItemOrange.getNameplateText(orange);
            if (name == null || name.isEmpty()) continue;

            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

            // Translate to camera-relative world coordinates
            x -= renderManager.viewerPosX;
            y -= renderManager.viewerPosY;
            z -= renderManager.viewerPosZ;

            float playerHeight = player == mc.thePlayer && mc.gameSettings.thirdPersonView == 0
                ? player.getEyeHeight() + HEIGHT_OFFSET
                : player.height + HEIGHT_OFFSET;

            renderBillboardName(name, x, y + playerHeight, z, renderManager);
        }
    }

    private ItemStack findOrangeItem(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;

        // Check main inventory (includes hotbar)
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemOrange) {
                return stack;
            }
        }
        return null;
    }

    private void renderBillboardName(String name, double x, double y, double z, RenderManager renderManager) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);

            // Billboard: always face the camera
            GL11.glNormal3f(0.0f, 1.0f, 0.0f);
            GL11.glRotatef(-renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
            GL11.glRotatef(renderManager.playerViewX, 1.0f, 0.0f, 0.0f);
            GL11.glScalef(-NAME_SCALE, -NAME_SCALE, NAME_SCALE);

            int textWidth = fontRenderer.getStringWidth(name);
            int halfWidth = textWidth / 2;

            // Disable depth test so nameplate renders through walls
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_LIGHTING);

            // Draw background rectangle
            drawNameBackground(halfWidth, fontRenderer.FONT_HEIGHT);

            // Draw text
            fontRenderer.drawString(name, -halfWidth, 0, NAME_COLOR);

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void drawNameBackground(int halfWidth, int textHeight) {
        Tessellator tessellator = Tessellator.instance;
        int padding = 2;
        int bgWidth = halfWidth * 2 + padding * 2;
        int bgHeight = textHeight + padding * 2;
        int bgX = -halfWidth - padding;
        int bgY = -padding;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(BG_COLOR, (BG_COLOR >> 24) & 0xFF);
        tessellator.addVertex(bgX, bgY + bgHeight, 0.0);
        tessellator.addVertex(bgX + bgWidth, bgY + bgHeight, 0.0);
        tessellator.addVertex(bgX + bgWidth, bgY, 0.0);
        tessellator.addVertex(bgX, bgY, 0.0);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
