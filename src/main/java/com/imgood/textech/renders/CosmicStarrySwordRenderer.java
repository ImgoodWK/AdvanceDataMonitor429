package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
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

                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);

                RenderItem r = RenderItem.getInstance();
                r.renderItemIntoGUI(mc.fontRenderer, mc.getTextureManager(), item, 0, 0, true);

                if (item.getItem() instanceof ICosmicRenderItem) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    RenderHelper.enableGUIStandardItemLighting();
                    GL11.glDisable(GL11.GL_ALPHA_TEST);
                    GL11.glDisable(GL11.GL_DEPTH_TEST);

                    ICosmicRenderItem icri = (ICosmicRenderItem) item.getItem();
                    StarryCosmicRenderUtil.cosmicOpacity = icri.getMaskMultiplier(item, null);
                    StarryCosmicRenderUtil.inventoryRender = true;
                    StarryCosmicRenderUtil.useShader();

                    IIcon cosmicicon = icri.getMaskTexture(item, null);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                    float minu = cosmicicon.getMinU();
                    float maxu = cosmicicon.getMaxU();
                    float minv = cosmicicon.getMinV();
                    float maxv = cosmicicon.getMaxV();

                    Tessellator t = Tessellator.instance;
                    t.startDrawingQuads();
                    t.addVertexWithUV(0.0D, 0.0D, 0.0D, minu, minv);
                    t.addVertexWithUV(0.0D, 16.0D, 0.0D, minu, maxv);
                    t.addVertexWithUV(16.0D, 16.0D, 0.0D, maxu, maxv);
                    t.addVertexWithUV(16.0D, 0.0D, 0.0D, maxu, minv);
                    t.draw();

                    StarryCosmicRenderUtil.releaseShader();
                    StarryCosmicRenderUtil.inventoryRender = false;
                }

                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                r.renderWithColor = true;
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
}
