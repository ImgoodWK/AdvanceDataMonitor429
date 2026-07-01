package com.imgood.textech.renders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.loader.LoaderBlock;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderMatterBallDecompressor extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/blocks/adv_matter_ball_decompressor.png");

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (LoaderBlock.matterBallDecompressor == null) {
            return;
        }
        IIcon icon = LoaderBlock.matterBallDecompressor.getIcon(0, 0);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GlStateManager.enableLighting();
        Minecraft.getMinecraft().renderEngine.bindTexture(TEXTURE);
        AdvanceBlockCubeRenderUtil.renderUnitCube(icon);
        GL11.glPopMatrix();
    }
}
