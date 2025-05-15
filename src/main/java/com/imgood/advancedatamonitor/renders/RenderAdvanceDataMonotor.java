package com.imgood.advancedatamonitor.renders;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import org.lwjgl.opengl.GL15;

@SideOnly(Side.CLIENT)
public class RenderAdvanceDataMonotor extends TileEntitySpecialRenderer {

    private int facing;


    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityAdvanceDataMonotor)) return;
        TileEntityAdvanceDataMonotor monitor = (TileEntityAdvanceDataMonotor) te;

        for (Map.Entry<Integer, NBTTagCompound> entry : monitor.getDataBoundList().entrySet()) {
            NBTTagCompound nbt = entry.getValue();
            String dataType = nbt.getString("dataType");

            // 执行基础变换
            GL11.glPushMatrix();
            applyBaseTransforms(nbt, x, y, z, monitor.getFacing());

            // 获取对应渲染器
            IADMRender renderer = RenderController.getRenderer(dataType);
            if (renderer != null) {
                renderer.render(nbt, x, y, z, monitor.getFacing());
            }

            GL11.glPopMatrix();
        }
    }

    private void applyBaseTransforms(NBTTagCompound nbt, double x, double y, double z, int facing) {
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(180, 0, 1, 0);
        GL11.glRotatef(getRotationFromFacing(facing), 0, 1, 0);
        GL11.glRotatef(nbt.getFloat("rotationX"), 1, 0, 0);
        GL11.glRotatef(nbt.getFloat("rotationY"), 0, 1, 0);
        GL11.glRotatef(nbt.getFloat("rotationZ"), 0, 0, 1);

        double[] adjustedOffset = adjustOffsetByFacing(
                nbt.getFloat("xOffset"),
                nbt.getFloat("zOffset"),
                facing
        );
        GL11.glTranslated(adjustedOffset[0], nbt.getFloat("yOffset"), adjustedOffset[1]);
        GL11.glScalef(nbt.getFloat("scale"), nbt.getFloat("scale"), 1.0f);
    }



    // ======================== 核心修改部分 ======================== //
    private double[] adjustOffsetByFacing(float xOffset, float zOffset, int facing) {
        switch (facing) {
            case 0: // North

                return new double[]{xOffset, zOffset + 0.5};  // X轴正方向向右，Z轴负方向向前
            case 1: // East

                return new double[]{xOffset, zOffset + 0.5};  // Z轴正方向向右，X轴正方向向前
            case 2: // South

                return new double[]{xOffset, zOffset + 0.5}; // X轴负方向向右，Z轴正方向向前
            case 3: // West

                return new double[]{xOffset, zOffset + 0.5};// Z轴负方向向右，X轴负方向向前
            default:
                return new double[]{xOffset, zOffset + 0.5};
        }
    }

    // ======================== 其余保持不变的代码 ======================== //



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