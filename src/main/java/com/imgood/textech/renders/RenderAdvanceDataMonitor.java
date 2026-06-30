package com.imgood.textech.renders;

import java.util.Map;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularui.api.GlStateManager;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderAdvanceDataMonitor extends TileEntitySpecialRenderer {

    private static ResourceLocation advanceDataDisplayTexture = new ResourceLocation(
        AdvanceDataMonitor.MODID + ":textures/model/AdvanceDataMonitor.png");
    private static IModelCustom advanceDtaDisplayModel = AdvancedModelLoader
        .loadModel(new ResourceLocation(AdvanceDataMonitor.MODID + ":model/AdvanceDataMonitor2.obj"));

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityAdvanceDataMonitor)) return;
        TileEntityAdvanceDataMonitor monitor = (TileEntityAdvanceDataMonitor) te;
        int facing = monitor.getFacing();

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(getRotationFromFacing(facing), 0, 1, 0);

        // 渲染基础模型，传入TileEntity实例以获取旋转角�?
        GL11.glPushMatrix();
        GL11.glTranslated(0, -0.5, 0);
        if (monitor.isVisableBody()) {
            renderBaseModel(monitor);
        }

        GL11.glPopMatrix();

        // 渲染数据条目（保持不变）
        GL11.glTranslated(0, 1.5, 0);
        renderDataEntries(monitor, x, y, z);

        GL11.glPopMatrix();
    }

    private void renderBaseModel(TileEntityAdvanceDataMonitor monitor) {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);

        this.bindTexture(advanceDataDisplayTexture);
        // 排除发光部件和RollLighting
        advanceDtaDisplayModel.renderAllExcept("Lighting", "CableLighting", "RollLighting", "Roll");

        // 渲染Roll部件并应用旋�?
        float rollAngle = monitor.getRollRotation(); // 从TileEntity获取旋转角度
        GL11.glPushMatrix();
        GL11.glTranslated(-0.1, 0.1, 0.05);
        GL11.glRotatef(rollAngle, 0, 1, 0); // 绕Y轴旋�?
        GL11.glScaled(0.8f, 0.8f, 0.8f);
        advanceDtaDisplayModel.renderOnly("Roll");
        GL11.glPopMatrix();

        // 发光部件处理
        GL11.glDepthMask(false);
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 渲染原有发光部件
        advanceDtaDisplayModel.renderOnly("Lighting", "CableLighting");

        // 渲染RollLighting并应用旋�?
        GL11.glPushMatrix();
        GL11.glTranslated(-0.1, 0.1, 0.05);
        GL11.glRotatef(rollAngle, 0, 1, 0); // 同样的旋转角�?
        GL11.glScaled(0.8f, 0.8f, 0.8f);
        advanceDtaDisplayModel.renderOnly("RollLighting");
        GL11.glPopMatrix();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private void renderDataEntries(TileEntityAdvanceDataMonitor monitor, double x, double y, double z) {
        for (Map.Entry<Integer, NBTTagCompound> entry : monitor.getDataBoundList()
            .entrySet()) {
            NBTTagCompound nbt = entry.getValue();
            String dataType = nbt.getString("dataType");

            GL11.glPushMatrix();
            applyItemTransforms(nbt, monitor.getFacing());

            IADMRender renderer = RenderController.getRenderer(dataType);
            if (renderer != null) {
                renderer.render(nbt, 0, 0, 0, monitor.getFacing()); // 传入相对坐标
            }

            GL11.glPopMatrix();
        }
    }

    private void applyItemTransforms(NBTTagCompound nbt, int facing) {
        // 先应用偏�?
        double[] adjustedOffset = adjustOffsetByFacing(nbt.getFloat("xOffset"), nbt.getFloat("zOffset"), facing);
        GL11.glTranslated(adjustedOffset[0], nbt.getFloat("yOffset"), adjustedOffset[1]);

        // 后应用旋�?
        // GL11.glRotatef(nbt.getFloat("rotationX"), 1, 0, 0);
        // GL11.glRotatef(nbt.getFloat("rotationY"), 0, 1, 0);
        // GL11.glRotatef(nbt.getFloat("rotationZ"), 0, 0, 1);

        // 最后缩�?
        // GL11.glScalef(nbt.getFloat("scale"), nbt.getFloat("scale"), 1.0f);
    }

    // 保持原样（根据问题要求不修改�?
    private double[] adjustOffsetByFacing(float xOffset, float zOffset, int facing) {
        switch (facing) {
            case 0: // North
                return new double[] { xOffset, zOffset + 0.5 };
            case 1: // East
                return new double[] { xOffset, zOffset + 0.5 };
            case 2: // South
                return new double[] { xOffset, zOffset + 0.5 };
            case 3: // West
                return new double[] { xOffset, zOffset + 0.5 };
            default:
                return new double[] { xOffset, zOffset + 0.5 };
        }
    }

    private float getRotationFromFacing(int facing) {
        switch (facing) {
            case 0:
                return 180.0f;
            case 1:
                return 90.0f;
            case 2:
                return 0.0f;
            case 3:
                return -90.0f;
            default:
                return 0.0f;
        }
    }

}
