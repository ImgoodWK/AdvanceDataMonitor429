package com.imgood.advancedatamonitor.renders;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class RenderAdvanceDataMonotor extends TileEntitySpecialRenderer {
    private int facing;
    private Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityAdvanceDataMonotor)) return;
        TileEntityAdvanceDataMonotor monitor = (TileEntityAdvanceDataMonotor) te;
        facing = monitor.getFacing();

        for (Map.Entry<Integer, NBTTagCompound> entry : monitor.getDataBoundList().entrySet()) {
            NBTTagCompound nbt = entry.getValue();
            renderDataBound(nbt, x, y, z, facing);
        }
    }

    private void renderDataBound(NBTTagCompound nbt, double x, double y, double z, int facing) {
        String dataType = nbt.getString("dataType");
        NBTTagList dataValues = nbt.getTagList("dataValues", 10);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // 空间变换体系
        GL11.glTranslated(x+0.5, y+0.5, z+0.5);
        GL11.glRotatef(getRotationFromFacing(facing), 0, 1, 0);
        GL11.glRotatef(nbt.getFloat("rotationX"), 1, 0, 0);
        GL11.glRotatef(nbt.getFloat("rotationY"), 0, 1, 0);
        GL11.glRotatef(nbt.getFloat("rotationZ"), 0, 0, 1);

        // 动态偏移计算
        double[] adjustedOffset = adjustOffsetByFacing(
                nbt.getFloat("xOffset"),
                nbt.getFloat("zOffset"),
                facing
        );
        GL11.glTranslated(
                adjustedOffset[0],
                nbt.getFloat("yOffset"),
                adjustedOffset[1] + 0.5 + 0.001
        );

        // 动态缩放
        float scaleFactor = nbt.getFloat("scale");
        GL11.glScalef(scaleFactor, scaleFactor, 1.0f);

        // 折线绘制核心逻辑
        Tessellator tess = Tessellator.instance;
        GL11.glLineWidth(nbt.getFloat("lineWidth"));
        tess.startDrawing(GL11.GL_LINE_STRIP);
        int color = Integer.parseInt(nbt.getString("lineColor"), 16);
        tess.setColorRGBA(
                (color >> 16) & 0xFF,
                (color >> 8) & 0xFF,
                color & 0xFF,
                255
        );

        // 动态坐标系参数
        double xRange = nbt.getDouble("xRange");  // 新增动态X轴长度
        double yRange = 0.8;
        double yMin = nbt.getDouble("yMin");
        double yMax = nbt.getDouble("yMax");
        int dataLimit = nbt.getInteger("dataLimit");
        double xStart = -xRange / 2;  // 动态起始点
        double xStep = xRange / (dataLimit - 1);

        // 数据点遍历
        for (int i = 0; i < dataValues.tagCount(); i++) {
            NBTTagCompound dataPoint = dataValues.getCompoundTagAt(i);
            double value = dataPoint.getDouble("data");

            // 动态坐标计算
            double xPos = xStart + (i * xStep);
            double yPos = ((value - yMin) / (yMax - yMin)) * yRange - 0.4;
            yPos = Math.max(-0.4, Math.min(0.4, yPos));
            tess.addVertex(xPos, yPos, 0);
        }

        tess.draw();
        renderAxis(nbt, facing);  // 调用坐标系渲染

        // 恢复渲染状态
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }
    private void renderAxis(NBTTagCompound nbt, int facing) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        //GL11.glDisable(GL11.GL_DEPTH_TEST);

        Tessellator tess = Tessellator.instance;
        double zOffset = 0.1;

        // 坐标系参数
        double xRange = nbt.getDouble("xRange");
        double xStart = -xRange / 2;
        double xEnd = xRange / 2;
        int dataLimit = nbt.getInteger("dataLimit");

        // 绘制坐标轴线
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA(255, 255, 255, 255);
        // X轴
        tess.addVertex(xStart, -0.4, zOffset);
        tess.addVertex(xEnd, -0.4, zOffset);
        // Y轴
        tess.addVertex(xStart, -0.4, zOffset);
        tess.addVertex(xStart, 0.4, zOffset);
        tess.draw();

        // Y轴刻度系统保持不变...
        // Y轴刻度系统
        double yMin = nbt.getDouble("yMin");
        double yMax = nbt.getDouble("yMax");
        double yInterval = computeOptimalInterval(yMax - yMin);
        for (double value = yMin; value <= yMax; value += yInterval) {
            double yPos = ((value - yMin) / (yMax - yMin)) * 0.8 - 0.4;
            yPos = Math.max(-0.4, Math.min(0.4, yPos));

            tess.startDrawing(GL11.GL_LINES);
            tess.addVertex(xStart, yPos, zOffset);
            tess.addVertex(xStart - 0.03, yPos, zOffset);
            tess.draw();

            if ((value / yInterval) % 2 == 0 && value != 0) {
                renderText(
                        String.format("%.1f", value),
                        xStart - 0.06,
                        yPos - 0.02,
                        zOffset,
                        0xFFFFFF,
                        0.5f,
                        true,
                        facing // 添加朝向参数
                );
            }
        }
        // X轴动态刻度系统
        if (dataLimit > 1) {
            double xStep = xRange / (dataLimit - 1);
            int labelInterval = computeOptimalXInterval(dataLimit);

            // 主刻度线
            for (int i = 0; i < dataLimit; i++) {
                double xPos = xStart + i * xStep;

                // 动态调整刻度线长度：有标签的刻度线更长
                double lineLength = (i % labelInterval == 0 || i == dataLimit - 1)
                        ? -0.45  // 长刻度
                        : -0.43; // 短刻度

                tess.startDrawing(GL11.GL_LINES);
                tess.setColorRGBA(255, 255, 255, 255);
                tess.addVertex(xPos, -0.4, zOffset);
                tess.addVertex(xPos, lineLength, zOffset);
                tess.draw();

                // 标签渲染（仅在长刻度位置显示）
                if (i % labelInterval == 0 || i == dataLimit - 1) {
                    String label = String.valueOf(i);
                    double textOffset = label.length() * 0.015;
                    renderText(
                            label,
                            xPos - textOffset,
                            -0.47,
                            zOffset,
                            0xFFFFFF,
                            0.5f,
                            false,
                            facing // 传递朝向参数
                    );
                }
            }

            // 边界刻度强化（保留原有逻辑）
            tess.startDrawing(GL11.GL_LINES);
            tess.setColorRGBA(255, 255, 255, 255);
            tess.addVertex(xEnd, -0.4, zOffset);
            tess.addVertex(xEnd, -0.45, zOffset);
            tess.draw();
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void renderText(String text, double x, double y, double z, int color, float scale, boolean vertical, int facing) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        //GL11.glDisable(GL11.GL_DEPTH_TEST); // 确保文本在前

        GL11.glTranslated(x, y, z);

        GL11.glRotatef(180, 0, 1, 0);
        GL11.glRotatef(180, 0, 0, 1);
        if (vertical) {
            GL11.glTranslatef((float) -0.05, 0, 0);
            GL11.glRotatef(-90, 0, 0, 1); // 调整垂直方向
        }



        GL11.glScalef(scale * 0.02f, scale * 0.02f, scale * 0.02f);

        // 中心对齐文本
        int textWidth = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawString(text, -textWidth/2, 0, color);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private boolean contains(int[] array, int value) {
        for (int i : array) {
            if (i == value) return true;
        }
        return false;
    }
    private int computeOptimalXInterval(int dataPoints) {
        if (dataPoints <= 10) return 1;
        if (dataPoints <= 20) return 2;
        if (dataPoints <= 50) return 5;
        return Math.max(1, dataPoints / 10);
    }
    private float getRotationFromFacing(int facing) {
        switch (facing) {
            case 0: return 180.0f;
            case 1: return 90.0f;
            case 2: return 0.0f;
            case 3: return -90.0f;
            default: return 0.0f;
        }
    }

    private double[] adjustOffsetByFacing(float xOffset, float zOffset, int facing) {
        switch (facing) {
            case 0: return new double[]{-xOffset, zOffset};
            case 1: return new double[]{zOffset, xOffset};
            case 2: return new double[]{xOffset, zOffset};
            case 3: return new double[]{-zOffset, -xOffset};
            default: return new double[]{xOffset, zOffset};
        }
    }

    private double computeOptimalInterval(double range) {
        if (range <= 0) return 1;
        double[] intervals = {1, 2, 5, 10, 20, 50};
        double log = Math.log10(range);
        double factor = Math.pow(10, Math.floor(log));
        double normalized = range / factor;

        for (double interval : intervals) {
            if (normalized <= interval) {
                return interval * factor / 2;
            }
        }
        return 10 * factor;
    }

    private void renderText(String text, double x, double y, double z, int color, float scale, boolean vertical) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT); // 保存当前状态
        GL11.glEnable(GL11.GL_TEXTURE_2D); // 启用纹理
        GL11.glEnable(GL11.GL_BLEND); // 确保混合启用
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glTranslated(x, y, z);
        // 调整旋转以正确朝向玩家
        GL11.glRotatef(-180, 0, 1, 0); // 仅绕Y轴旋转180度以适应方块方向
        if (vertical) {
            GL11.glRotatef(-90, 0, 0, 1); // 调整Y轴标签旋转方向
        }
        GL11.glScalef(scale * 0.02f, scale * 0.02f, scale * 0.02f);

        // 渲染文本，确保在正确位置
        mc.fontRenderer.drawString(text, -mc.fontRenderer.getStringWidth(text) / 2, 0, color);

        GL11.glPopAttrib(); // 恢复之前的状态
        GL11.glPopMatrix();
    }

    private int[] computeXLabelPositions(int dataLimit) {
        Set<Integer> positions = new LinkedHashSet<>();
        if (dataLimit <= 0) return new int[0];

        positions.add(0);
        positions.add(dataLimit - 1);

        if (dataLimit > 3) {
            positions.add(Math.round((dataLimit-1) * 1.0f / 3));
            positions.add(Math.round((dataLimit-1) * 2.0f / 3));
        }

        while (positions.size() < 4 && dataLimit > 1) {
            for (int i = 1; i < dataLimit-1; i++) {
                positions.add(i);
                if (positions.size() >= 4) break;
            }
        }

        return positions.stream().mapToInt(i->i).toArray();
    }
}