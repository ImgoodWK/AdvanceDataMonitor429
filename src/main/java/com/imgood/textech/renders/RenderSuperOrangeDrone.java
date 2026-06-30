package com.imgood.textech.renders;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.textech.loader.LoaderItem;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Renders the Super Orange drone using the item's 3D entity model, billboarded toward the player.
 */
@SideOnly(Side.CLIENT)
public class RenderSuperOrangeDrone extends Render {

    private static final float ITEM_SCALE = 0.5F;
    private static final float ITEM_THICKNESS = 0.0625F;

    public RenderSuperOrangeDrone() {
        this.shadowSize = 0.3f;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        Item item = LoaderItem.orange;
        if (item == null) {
            return;
        }

        ItemStack stack = new ItemStack(item);
        float age = entity.ticksExisted + partialTicks;
        float bob = (float) Math.sin(age / 10.0F) * 0.1F + 0.1F;

        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y + bob, (float) z);

        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        int brightness = entity.getBrightnessForRender(partialTicks);
        int lightU = brightness % 65536;
        int lightV = brightness / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) lightU, (float) lightV);

        GL11.glRotatef(180.0F - RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-RenderManager.instance.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);

        IItemRenderer customRenderer = MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.ENTITY);
        if (customRenderer != null && customRenderer.handleRenderType(stack, IItemRenderer.ItemRenderType.ENTITY)) {
            GL11.glEnable(GL11.GL_LIGHTING);
            customRenderer.renderItem(IItemRenderer.ItemRenderType.ENTITY, stack);
        } else {
            renderVanillaItemModel(stack);
        }

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    private void renderVanillaItemModel(ItemStack stack) {
        Item item = stack.getItem();
        IIcon icon = item.getIconIndex(stack);
        if (icon == null) {
            return;
        }

        int color = item.getColorFromItemStack(stack, 0);
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glColor4f(r, g, b, 1.0F);

        bindTexture(TextureMap.locationItemsTexture);
        GL11.glEnable(GL11.GL_LIGHTING);

        float minU = icon.getMinU();
        float maxU = icon.getMaxU();
        float minV = icon.getMinV();
        float maxV = icon.getMaxV();

        ItemRenderer.renderItemIn2D(
            Tessellator.instance,
            maxU,
            minV,
            minU,
            maxV,
            icon.getIconWidth(),
            icon.getIconHeight(),
            ITEM_THICKNESS);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TextureMap.locationItemsTexture;
    }
}
