package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.textech.handler.StarryCosmosSwordConstants;
import com.imgood.textech.items.ItemStarryCosmosSword;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * World-space cosmic sword sprite renderer (thrown / rain / slam).
 *
 * <p>
 * {@link ItemRenderer#renderItemIn2D} with flipped U places the blade along local +X (tip to the right).
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class StarryCosmosWorldRenderer {

    private static final float ITEM_THICKNESS = StarryCosmosSwordConstants.ITEM_SPRITE_THICKNESS;

    private static final float BLADE_ICON_HEIGHT = StarryCosmosSwordConstants.BLADE_ICON_HEIGHT;

    /** Extra roll applied to every world sprite (user-tuned). */
    private static final float SPRITE_EXTRA_ROLL_DEG = 225.0F;

    private static final float BLADE_FORWARD_YAW_OFFSET = -90.0F;
    private static final float TIP_DOWN_ROLL_DEG = -90.0F;
    /** Slam: tip-down -135° + cosmetic roll composes to vertical; roll includes +135° user tune. */
    private static final float SLAM_TIP_DOWN_ROLL_DEG = TIP_DOWN_ROLL_DEG - 45.0F;
    private static final float SLAM_SPRITE_ROLL_DEG = 45.0F + 135.0F + 90.0F;

    public static final float SCALE_THROWN = 1.6F;
    public static final float SCALE_RAIN = 1.4F;
    public static final float SCALE_SLAM = StarryCosmosSwordConstants.SCALE_SLAM;

    private StarryCosmosWorldRenderer() {}

    public static float slamBladeLengthBlocks() {
        return StarryCosmosSwordConstants.slamBladeVisibleLengthBlocks();
    }

    public static void renderThrownBlade(Entity entity, ItemStack stack, double dx, double dy, double dz) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0E-6D && Math.abs(dy) < 1.0E-6D) {
            dz = 1.0D;
            horiz = 1.0D;
        }
        float yaw = (float) (Math.atan2(dx, dz) * 180.0D / Math.PI);
        float pitch = (float) (Math.atan2(dy, horiz) * 180.0D / Math.PI);

        GL11.glPushMatrix();
        GL11.glRotatef(yaw + BLADE_FORWARD_YAW_OFFSET, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-pitch, 1.0F, 0.0F, 0.0F);
        drawSprite(entity, stack, SCALE_THROWN, SPRITE_EXTRA_ROLL_DEG);
        GL11.glPopMatrix();
    }

    public static void renderRainFalling(Entity entity, ItemStack stack, float yawDeg) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        GL11.glPushMatrix();
        applyTipDownBase(yawDeg, 0.0F, 0.0F);
        drawSprite(entity, stack, SCALE_RAIN, SPRITE_EXTRA_ROLL_DEG);
        GL11.glPopMatrix();
    }

    public static void renderRainStuck(Entity entity, ItemStack stack, float yawDeg, float tiltFromVerticalDeg,
        float rollDeg) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        GL11.glPushMatrix();
        applyTipDownBase(yawDeg, tiltFromVerticalDeg, rollDeg);
        GL11.glTranslatef(0.0F, -0.35F, 0.0F);
        drawSprite(entity, stack, SCALE_RAIN, SPRITE_EXTRA_ROLL_DEG);
        GL11.glPopMatrix();
    }

    /** Giant slam: vertical insert; blade faces {@code displayYawDeg} (cast direction), tip down. */
    public static void renderSlamBlade(Entity entity, ItemStack stack, float displayYawDeg) {
        renderSlamBlade(entity, stack, displayYawDeg, SCALE_SLAM);
    }

    public static void renderSlamBlade(Entity entity, ItemStack stack, float displayYawDeg, float renderScale) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glRotatef(displayYawDeg, 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(StarryCosmosSwordConstants.slamRenderPivotOffsetX(renderScale), 0.0F, 0.0F);
        GL11.glRotatef(SLAM_TIP_DOWN_ROLL_DEG, 0.0F, 0.0F, 1.0F);
        drawSprite(entity, stack, renderScale, SLAM_SPRITE_ROLL_DEG);
        GL11.glPopMatrix();
    }

    private static void applyTipDownBase(float yawDeg, float tiltFromVerticalDeg, float rollDeg) {
        GL11.glRotatef(yawDeg, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(rollDeg, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(tiltFromVerticalDeg, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(TIP_DOWN_ROLL_DEG, 0.0F, 0.0F, 1.0F);
    }

    private static void drawSprite(Entity entity, ItemStack stack, float scale, float extraRollDeg) {
        IIcon icon = stack.getIconIndex();
        if (icon == null) {
            return;
        }

        if (extraRollDeg != 0.0F) {
            GL11.glRotatef(extraRollDeg, 0.0F, 0.0F, 1.0F);
        }
        drawSpriteBody(entity, stack, scale);
    }

    private static void drawSpriteBody(Entity entity, ItemStack stack, float scale) {
        IIcon icon = stack.getIconIndex();
        if (icon == null) {
            return;
        }

        GL11.glScalef(scale, scale, scale);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_CULL_FACE);

        if (entity != null) {
            StarryCosmicRenderUtil.setLightFromLocation(
                entity.worldObj,
                MathHelper.floor_double(entity.posX),
                MathHelper.floor_double(entity.posY),
                MathHelper.floor_double(entity.posZ));
        } else {
            StarryCosmicRenderUtil.setLightLevel(1.0F);
        }

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationItemsTexture);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tess = Tessellator.instance;
        ItemRenderer.renderItemIn2D(
            tess,
            icon.getMaxU(),
            icon.getMinV(),
            icon.getMinU(),
            icon.getMaxV(),
            icon.getIconWidth(),
            icon.getIconHeight(),
            ITEM_THICKNESS);

        if (stack.getItem() instanceof ItemStarryCosmosSword) {
            ItemStarryCosmosSword cosmicItem = (ItemStarryCosmosSword) stack.getItem();
            IIcon mask = cosmicItem.getMaskTexture(stack, null);
            if (mask != null) {
                drawCosmicOverlay(tess, mask, cosmicItem.getMaskMultiplier(stack, null));
            }
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private static void drawCosmicOverlay(Tessellator tess, IIcon mask, float opacity) {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        StarryCosmicRenderUtil.cosmicOpacity = opacity;
        StarryCosmicRenderUtil.inventoryRender = false;
        StarryCosmicRenderUtil.useShader();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        ItemRenderer.renderItemIn2D(
            tess,
            mask.getMaxU(),
            mask.getMinV(),
            mask.getMinU(),
            mask.getMaxV(),
            mask.getIconWidth(),
            mask.getIconHeight(),
            ITEM_THICKNESS);

        StarryCosmicRenderUtil.releaseShader();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }
}
