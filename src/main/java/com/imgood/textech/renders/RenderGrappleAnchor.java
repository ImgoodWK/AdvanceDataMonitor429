package com.imgood.textech.renders;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.loader.LoaderBlock;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * World renderer for {@link com.imgood.textech.blocks.BlockGrappleAnchor}.
 * Uses a flat placeholder plate until a dedicated anchor OBJ model exists.
 */
@SideOnly(Side.CLIENT)
public class RenderGrappleAnchor extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/blocks/grapple_anchor.png");

    /** Flip to false once {@code GrappleAnchor.obj} is wired in. */
    public static final boolean USE_PLACEHOLDER_PLATE = true;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityGrappleAnchor)) {
            return;
        }
        if (!USE_PLACEHOLDER_PLATE) {
            // Future: load GrappleAnchor.obj + shared ADM texture sheet here.
            return;
        }
        TileEntityGrappleAnchor anchor = (TileEntityGrappleAnchor) te;
        Block block = LoaderBlock.grappleAnchor;
        if (block == null) {
            return;
        }
        IIcon icon = block.getIcon(0, 0);

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GlStateManager.enableLighting();
        Minecraft.getMinecraft().renderEngine.bindTexture(TEXTURE);
        AdvanceBlockCubeRenderUtil.renderGrappleAnchorPlate(icon, anchor.getAttachFace());
        GL11.glPopMatrix();
    }
}
