package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import fox.spiteful.avaritia.render.ICosmicRenderItem;

/**
 * Avaritia {@code CosmicItemRenderer} style draw with Universium shader for Empyrean Holy Judgment.
 */
@SideOnly(Side.CLIENT)
public class CosmicStarrySwordRenderer implements IItemRenderer {

    public static final CosmicStarrySwordRenderer INSTANCE = new CosmicStarrySwordRenderer();

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        Minecraft mc = Minecraft.getMinecraft();
        processLightLevel(type, item, data);
        switch (type) {
            case ENTITY: {
                GL11.glPushMatrix();
                GL11.glTranslatef(-0.5F, 0.0F, 0.0F);
                if (item.isOnItemFrame()) {
                    GL11.glTranslatef(0.0F, -0.3F, 0.01F);
                }
                render(item, data[1] instanceof EntityPlayer ? (EntityPlayer) data[1] : null);
                GL11.glPopMatrix();
                break;
            }
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON: {
                render(item, data[1] instanceof EntityPlayer ? (EntityPlayer) data[1] : null);
                break;
            }
            case INVENTORY: {
                GL11.glPushMatrix();
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);

                mc.getTextureManager().bindTexture(TextureMap.locationItemsTexture);
                drawInventoryIcon(item, item.getItem().getIcon(item, 0));

                if (item.getItem() instanceof ICosmicRenderItem) {
                    ICosmicRenderItem icri = (ICosmicRenderItem) item.getItem();
                    StarryCosmicRenderUtil.cosmicOpacity = icri.getMaskMultiplier(item, null);
                    StarryCosmicRenderUtil.inventoryRender = true;
                    StarryCosmicRenderUtil.useShader();
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    drawInventoryIcon(item, icri.getMaskTexture(item, null));
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    StarryCosmicRenderUtil.releaseShader();
                    StarryCosmicRenderUtil.inventoryRender = false;
                }

                if (item.hasEffect(0)) {
                    RenderItem.getInstance().renderEffect(mc.getTextureManager(), 0, 0);
                }

                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glPopMatrix();
                break;
            }
            default:
                break;
        }
    }

    public void renderInWorld(ItemStack item, Entity entity) {
        if (entity != null) {
            StarryCosmicRenderUtil.setLightFromLocation(
                entity.worldObj,
                MathHelper.floor_double(entity.posX),
                MathHelper.floor_double(entity.posY),
                MathHelper.floor_double(entity.posZ));
        } else {
            StarryCosmicRenderUtil.setLightLevel(1.0F);
        }
        render(item, null);
    }

    public void render(ItemStack item, EntityPlayer player) {
        int passes = 1;
        if (item.getItem()
            .requiresMultipleRenderPasses()) {
            passes = item.getItem()
                .getRenderPasses(item.getItemDamage());
        }

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        float r;
        float g;
        float b;
        IIcon icon;
        float f;
        float f1;
        float f2;
        float f3;
        float scale = 1.0F / 16.0F;

        Tessellator tess = Tessellator.instance;
        for (int i = 0; i < passes; i++) {
            icon = item.getItem()
                .getIcon(item, i);
            f = icon.getMinU();
            f1 = icon.getMaxU();
            f2 = icon.getMinV();
            f3 = icon.getMaxV();

            int colour = item.getItem()
                .getColorFromItemStack(item, i);
            r = (float) (colour >> 16 & 255) / 255.0F;
            g = (float) (colour >> 8 & 255) / 255.0F;
            b = (float) (colour & 255) / 255.0F;
            GL11.glColor4f(r, g, b, 1.0F);
            ItemRenderer.renderItemIn2D(tess, f1, f2, f, f3, icon.getIconWidth(), icon.getIconHeight(), scale);
        }

        if (item.getItem() instanceof ICosmicRenderItem) {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            ICosmicRenderItem icri = (ICosmicRenderItem) item.getItem();
            StarryCosmicRenderUtil.cosmicOpacity = icri.getMaskMultiplier(item, player);
            StarryCosmicRenderUtil.useShader();

            IIcon cosmicicon = icri.getMaskTexture(item, player);
            float minu = cosmicicon.getMinU();
            float maxu = cosmicicon.getMaxU();
            float minv = cosmicicon.getMinV();
            float maxv = cosmicicon.getMaxV();
            ItemRenderer.renderItemIn2D(
                tess,
                maxu,
                minv,
                minu,
                maxv,
                cosmicicon.getIconWidth(),
                cosmicicon.getIconHeight(),
                scale);
            StarryCosmicRenderUtil.releaseShader();
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void processLightLevel(ItemRenderType type, ItemStack item, Object... data) {
        switch (type) {
            case ENTITY:
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON: {
                Entity ent = (Entity) data[1];
                if (ent != null) {
                    StarryCosmicRenderUtil.setLightFromLocation(
                        ent.worldObj,
                        MathHelper.floor_double(ent.posX),
                        MathHelper.floor_double(ent.posY),
                        MathHelper.floor_double(ent.posZ));
                }
                break;
            }
            default: {
                StarryCosmicRenderUtil.setLightLevel(1.0F);
            }
        }
    }

    /** Vanilla-equivalent 16×16 GUI icon quad; avoids recursive {@link RenderItem#renderItemIntoGUI}. */
    private static void drawInventoryIcon(ItemStack stack, IIcon icon) {
        if (stack == null || icon == null) {
            return;
        }

        int color = stack.getItem().getColorFromItemStack(stack, 0);
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glColor4f(r, g, b, 1.0F);

        float minU = icon.getMinU();
        float maxU = icon.getMaxU();
        float minV = icon.getMinV();
        float maxV = icon.getMaxV();

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(0.0D, 16.0D, 0.0D, minU, maxV);
        tess.addVertexWithUV(16.0D, 16.0D, 0.0D, maxU, maxV);
        tess.addVertexWithUV(16.0D, 0.0D, 0.0D, maxU, minV);
        tess.addVertexWithUV(0.0D, 0.0D, 0.0D, minU, minV);
        tess.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
