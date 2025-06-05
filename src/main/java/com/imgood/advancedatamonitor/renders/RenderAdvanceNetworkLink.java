package com.imgood.advancedatamonitor.renders;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class RenderAdvanceNetworkLink extends TileEntitySpecialRenderer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AdvanceDataMonitor.MODID + ":textures/model/AdvanceDataMonitor.png"
    );

    private static final IModelCustom MODEL = AdvancedModelLoader.loadModel(
            new ResourceLocation(AdvanceDataMonitor.MODID + ":model/AdvanceNetworkLink.obj")
    );

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityAdvanceNetworkLink)) return;
        TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) te;

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);

        // 修复：获取正确旋转角度
        int facing = link.getWorldObj().getBlockMetadata(link.xCoord, link.yCoord, link.zCoord);
        float rotation = getRotationFromFacing(facing);
        GL11.glRotatef(rotation, 0, 1, 0); // 绕Y轴旋转

        GL11.glTranslated(0, -0.5, 0); // 调整模型位置

        // 保存当前光贴图状态
        float lastBrightnessX = OpenGlHelper.lastBrightnessX;
        float lastBrightnessY = OpenGlHelper.lastBrightnessY;

        // 渲染基础部件
        renderBaseParts(link);

        // 渲染发光部件
        renderEmissiveParts();

        // 恢复光贴图状态
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastBrightnessX, lastBrightnessY);

        GL11.glPopMatrix();
    }

    private void renderBaseParts(TileEntityAdvanceNetworkLink link) {
        // 基础渲染设置
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GlStateManager.enableLighting();

        bindTexture(TEXTURE);
        MODEL.renderAllExcept("Lighting", "CableLighting", "RollLighting"); // 排除发光部件
    }

    private void renderEmissiveParts() {
        // 发光部件设置
        GL11.glDepthMask(false);
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f); // 最大亮度
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // 渲染发光部件
        MODEL.renderPart("Lighting");
        MODEL.renderPart("CableLighting");
        MODEL.renderPart("RollLighting"); // 添加滚轮发光部件

        // 恢复状态
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GlStateManager.enableLighting();
    }

    private float getRotationFromFacing(int facing) {
        switch (facing) {
            case 0: return 180.0f; // North
            case 1: return 90.0f;   // East
            case 2: return 0.0f;    // South
            case 3: return -90.0f;  // West
            default: return 0.0f;
        }
    }
}