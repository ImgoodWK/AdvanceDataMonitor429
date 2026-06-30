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
import com.imgood.textech.tileentity.TileEntityAdvanceCraftingLink;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Placeholder world renderer for {@link com.imgood.textech.blocks.BlockAdvanceCraftingLink}.
 * Renders a textured cube until the dedicated crafting-link OBJ model is available.
 */
@SideOnly(Side.CLIENT)
public class RenderAdvanceCraftingLink extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/blocks/adv_crafting_link.png");

    /** Flip to false once {@code CraftingLink.obj} is wired in (mirror {@link RenderAdvanceNetworkLink}). */
    public static final boolean USE_PLACEHOLDER_CUBE = true;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityAdvanceCraftingLink)) {
            return;
        }
        if (!USE_PLACEHOLDER_CUBE) {
            // Future: load CraftingLink.obj + AdvanceDataMonitor.png emissive parts here.
            return;
        }
        renderPlaceholderCube(x, y, z);
    }

    static void renderPlaceholderCube(double x, double y, double z) {
        Block block = LoaderBlock.advanceCraftingLink;
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
        AdvanceBlockCubeRenderUtil.renderUnitCube(icon);
        GL11.glPopMatrix();
    }
}
