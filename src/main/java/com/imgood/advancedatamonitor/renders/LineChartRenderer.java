package com.imgood.advancedatamonitor.renders;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/**
 * @program: AdvanceDataMonitor429
 * @description:
 * @author: Imgood
 * @create: 2025-05-15 09:22
 **/
public class LineChartRenderer implements IADMRender{
    private int vboId = -1;
    private Minecraft mc = Minecraft.getMinecraft();
    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing) {
        if (!nbt.getBoolean("enable")) return;
        NBTTagList dataValues = nbt.getTagList("dataValues", 10);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // 空间变换体系（保持不变）
        //GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(180, 0, 1, 0);
        GL11.glRotatef(nbt.getFloat("rotationX"), 1, 0, 0);
        GL11.glRotatef(nbt.getFloat("rotationY"), 0, 1, 0);
        GL11.glRotatef(nbt.getFloat("rotationZ"), 0, 0, 1);

        // 动态偏移计算（保持不变）
        double[] adjustedOffset = adjustOffsetByFacing(
                nbt.getFloat("xOffset"),
                nbt.getFloat("zOffset"),
                facing
        );
        GL11.glTranslated(adjustedOffset[0], nbt.getFloat("yOffset"), adjustedOffset[1]);

        // 动态缩放（保持不变）
        float scaleFactor = nbt.getFloat("scale");
        GL11.glScalef(scaleFactor, scaleFactor, 1.0f);

        // 计算动态数据范围（保持不变）
        double dataMin = Double.MAX_VALUE;
        double dataMax = -Double.MAX_VALUE;
        for (int i = 0; i < dataValues.tagCount(); i++) {
            double val = dataValues.getCompoundTagAt(i).getDouble("data");
            if (val < dataMin) dataMin = val;
            if (val > dataMax) dataMax = val;
        }
        if (dataMin == dataMax) {
            dataMax += 1.0;
        }

        // ============== 折线绘制VBO版本（修改部分开始）============== //
        GL11.glLineWidth(nbt.getFloat("lineWidth"));
        int color = Integer.parseInt(nbt.getString("lineColor"), 16);
        GL11.glColor4ub(
                (byte)((color >> 16) & 0xFF),
                (byte)((color >> 8) & 0xFF),
                (byte)(color & 0xFF),
                (byte)255
        );

        // 生成顶点数据
        List<Float> vertices = new ArrayList<>();
        double xRange = nbt.getDouble("xRange");
        int dataLimit = nbt.getInteger("dataLimit");
        double xStart = -xRange / 2;
        double xStep = xRange / (dataLimit - 1);
        final double Y_AXIS_BASE = -0.4;
        double yRange = nbt.hasKey("yRange") ? nbt.getDouble("yRange") : 2;

        for (int i = 0; i < dataValues.tagCount(); i++) {
            NBTTagCompound dataPoint = dataValues.getCompoundTagAt(i);
            double value = dataPoint.getDouble("data");
            double xPos = xStart + (i * xStep);
            double yPos = Y_AXIS_BASE + ((value - dataMin) / (dataMax - dataMin)) * yRange;
            yPos = Math.max(Y_AXIS_BASE, Math.min(Y_AXIS_BASE + yRange, yPos));
            vertices.add((float)xPos);
            vertices.add((float)yPos);
            vertices.add(0.0f);
        }

        // 创建并上传VBO数据
        int vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.size());
        for (float v : vertices) {
            buffer.put(v);
        }
        buffer.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);

        // 渲染VBO
        if (nbt.getBoolean("enableData")) {
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
            GL11.glDrawArrays(GL11.GL_LINE_STRIP, 0, vertices.size() / 3);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        }


        // 清理VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(vboId);
        // ============== 折线绘制VBO版本（修改部分结束）============== //

        renderAxis(nbt, facing, dataMin, dataMax, yRange, Y_AXIS_BASE);


        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @Override
    public void cleanup() {
        if (vboId != -1) {
            GL15.glDeleteBuffers(vboId);
            vboId = -1;
        }
    }


    private double[] adjustOffsetByFacing(float xOffset, float zOffset, int facing) {
        switch (facing) {
            case 0: // North

                return new double[] {  xOffset, zOffset+0.5 };  // X轴正方向向右，Z轴负方向向前
            case 1: // East

                return new double[] { xOffset, zOffset+0.5 };  // Z轴正方向向右，X轴正方向向前
            case 2: // South

                return new double[] {  xOffset, zOffset+0.5 }; // X轴负方向向右，Z轴正方向向前
            case 3: // West

                return new double[] { xOffset, zOffset+0.5 };// Z轴负方向向右，X轴负方向向前
            default:
                return new double[] { xOffset, zOffset+0.5 };
        }
    }

    private void renderAxis(NBTTagCompound nbt, int facing, double dataMin, double dataMax, double yRange,
                            double yAxisBase) {
        // 参数初始化（无论是否渲染都计算）
        int axisLineColor = Integer.parseInt(nbt.getString("axisLineColor"), 16);
        int axisFontColor = Integer.parseInt(nbt.getString("axisFontColor"), 16);
        int displayNameColor = Integer.parseInt(nbt.getString("displayNameColor"), 16);
        double axisFontScale = nbt.getDouble("axisFontScale");
        double displayNameScale = nbt.getDouble("displayNameScale");
        double zOffset = 0.1;
        double xRange = nbt.getDouble("xRange");
        double xStart = -xRange / 2;
        double xEnd = xRange / 2;
        double yAxisTop = yAxisBase + yRange;
        int dataLimit = nbt.getInteger("dataLimit");
        List<Integer> majorIndices = new ArrayList<>();
        // ========================== 计算部分（始终执行） ========================== //
        // 计算坐标轴线顶点
        List<Float> axisLines = new ArrayList<>();
        axisLines.add((float)xStart); axisLines.add((float)yAxisBase); axisLines.add((float)zOffset);
        axisLines.add((float)xEnd); axisLines.add((float)yAxisBase); axisLines.add((float)zOffset);
        axisLines.add((float)xStart); axisLines.add((float)yAxisBase); axisLines.add((float)zOffset);
        axisLines.add((float)xStart); axisLines.add((float)yAxisTop); axisLines.add((float)zOffset);

        // 计算Y轴刻度
        double yDataBorder = dataMax - dataMin;
        double yInterval = computeOptimalInterval(yDataBorder);
        List<Float> yTicks = new ArrayList<>();
        List<Double> yLabelValues = new ArrayList<>(); // 存储需要渲染的Y轴标签值
        for (double value = dataMin; value <= dataMax; value += yInterval) {
            double yPos = yAxisBase + ((value - dataMin) / yDataBorder) * yRange;
            yPos = Math.max(yAxisBase, Math.min(yAxisTop, yPos));
            yTicks.add((float)xStart); yTicks.add((float)yPos); yTicks.add((float)zOffset);
            yTicks.add((float)(xStart - 0.05)); yTicks.add((float)yPos); yTicks.add((float)zOffset);
            yLabelValues.add(yPos); // 记录标签位置
        }

        // 计算X轴刻度
        List<Float> majorTicks = new ArrayList<>();
        List<Float> minorTicks = new ArrayList<>();
        List<String> xLabels = new ArrayList<>(); // 存储需要渲染的X轴标签
        if (dataLimit > 1) {
            double xStep = xRange / (dataLimit - 1);
            int labelInterval = computeOptimalXInterval(dataLimit);

            majorIndices.add(0);
            majorIndices.add(dataLimit - 1);
            for (int i = labelInterval; i < dataLimit; i += labelInterval) {
                majorIndices.add(i);
            }
            List<Integer> sortedMajorIndices = new ArrayList<>(majorIndices);
            Collections.sort(sortedMajorIndices);

            // 主刻度
            for (int i : sortedMajorIndices) {
                double xPos = xStart + i * xStep;
                majorTicks.add((float)xPos); majorTicks.add((float)yAxisBase); majorTicks.add((float)zOffset);
                majorTicks.add((float)xPos); majorTicks.add((float)(yAxisBase - 0.08)); majorTicks.add((float)zOffset);
                xLabels.add(String.valueOf(i)); // 记录标签文本
            }

            // 次刻度（示例）
            for (int i = 0; i < sortedMajorIndices.size() - 1; i++) {
                int prev = sortedMajorIndices.get(i);
                int next = sortedMajorIndices.get(i + 1);
                for (int k = 1; k < 5; k++) { // 每个主刻度间4个次刻度
                    double xPos = xStart + (prev + (next - prev) * k / 5.0) * xStep;
                    minorTicks.add((float)xPos); minorTicks.add((float)yAxisBase); minorTicks.add((float)zOffset);
                    minorTicks.add((float)xPos); minorTicks.add((float)(yAxisBase - 0.04)); minorTicks.add((float)zOffset);
                }
            }
        }

        // ========================== 渲染部分（仅在enableAxis时执行） ========================== //
        if (nbt.getBoolean("enableAxis")) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // 渲染坐标轴线
            int axisVbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, axisVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, asFloatBuffer(axisLines), GL15.GL_STREAM_DRAW);
            GL11.glColor4ub((byte)(axisLineColor >> 16), (byte)(axisLineColor >> 8), (byte)axisLineColor, (byte)255);
            drawVboLines(axisLines.size() / 3);
            GL15.glDeleteBuffers(axisVbo);

            // 渲染Y轴刻度
            if (!yTicks.isEmpty()) {
                int yTickVbo = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, yTickVbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, asFloatBuffer(yTicks), GL15.GL_STREAM_DRAW);
                drawVboLines(yTicks.size() / 3);
                GL15.glDeleteBuffers(yTickVbo);


            }

            // 渲染X轴主刻度
            if (!majorTicks.isEmpty()) {
                int majorVbo = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, majorVbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, asFloatBuffer(majorTicks), GL15.GL_STREAM_DRAW);
                drawVboLines(majorTicks.size() / 3);
                GL15.glDeleteBuffers(majorVbo);

                // 渲染X轴标签（独立判断enableAxisFont）

            }

            // 渲染X轴次刻度
            if (!minorTicks.isEmpty()) {
                int minorVbo = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, minorVbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, asFloatBuffer(minorTicks), GL15.GL_STREAM_DRAW);
                drawVboLines(minorTicks.size() / 3);
                GL15.glDeleteBuffers(minorVbo);
            }



            GL11.glDisable(GL11.GL_BLEND);
        }
        if (nbt.getBoolean("enableAxisFont")) {
            double xStep = xRange / (dataLimit - 1);
            for (int i = 0; i < xLabels.size(); i++) {
                double xPos = xStart + (majorIndices.get(i) * xStep);
                String label = xLabels.get(i);
                renderText(
                        label,
                        xPos - label.length() * 0.015,
                        yAxisBase - 0.07,
                        zOffset,
                        axisFontColor,
                        axisFontScale,
                        false,
                        facing
                );
            }

            // 渲染Y轴标签（独立判断enableAxisFont）
            if (nbt.getBoolean("enableAxisFont")) {
                for (int i = 0; i < yLabelValues.size(); i++) {
                    double yPos = yLabelValues.get(i);
                    renderText(
                            String.format("%.1f", dataMin + i * yInterval),
                            xStart - 0.08,
                            yPos - 0.02,
                            zOffset,
                            axisFontColor,
                            axisFontScale,
                            true,
                            facing
                    );
                }
            }
        }
        // 标题渲染（保持独立）
        String displayName = nbt.getString("displayName");
        if (!displayName.isEmpty()) {
            renderTitle(displayName, displayNameColor, displayNameScale, facing, yAxisTop);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    // 辅助方法：将List<Float>转换为FloatBuffer
    private FloatBuffer asFloatBuffer(List<Float> list) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(list.size());
        for (float v : list) buffer.put(v);
        buffer.flip();
        return buffer;
    }

    // 辅助方法：绘制VBO中的线段
    private void drawVboLines(int vertexCount) {
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
        GL11.glDrawArrays(GL11.GL_LINES, 0, vertexCount);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private void renderTitle(String title, int color, double scale, int facing, double yPos) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);

        GL11.glTranslated(0, yPos + 0.5, 0.001);
        GL11.glRotatef(180, 0, 1, 0);
        GL11.glRotatef(180, 0, 0, 1);

        int textWidth = mc.fontRenderer.getStringWidth(title);
        double scaledWidth = textWidth * scale * 0.02f;
        GL11.glTranslated(-scaledWidth / 2, 0, 0);

        GL11.glScaled(scale * 0.02f, scale * 0.02f, 1);
        mc.fontRenderer.drawString(title, 0, 0, color);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
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

    private double computeOptimalInterval(double range) {
        if (range <= 0) return 1.0;
        double roughStep = range / 5;
        double exponent = Math.floor(Math.log10(roughStep));
        double factor = Math.pow(10, exponent);
        double normalized = roughStep / factor;

        double[] steps = {1.0, 2.0, 5.0, 10.0};
        for (double step : steps) {
            if (normalized <= step) {
                return step * factor;
            }
        }
        return 10 * factor;
    }

    private void renderText(String text, double x, double y, double z, int color, double scale, boolean vertical,
                            int facing) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glTranslated(x, y, z);
        GL11.glRotatef(180, 0, 1, 0);
        GL11.glRotatef(180, 0, 0, 1);

        if (vertical) {
            int textWidth = mc.fontRenderer.getStringWidth(text);
            int textHeight = mc.fontRenderer.FONT_HEIGHT;
            double actualWidth = textWidth * scale * 0.02;
            double actualHeight = textHeight * scale * 0.02;
            GL11.glTranslated(-actualWidth, -actualHeight / 2, 0);
        }

        GL11.glScaled(scale * 0.02, scale * 0.02, scale * 0.02);
        mc.fontRenderer.drawString(text, 0, 0, color);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private int computeOptimalXInterval(int dataPoints) {
        if (dataPoints <= 10) return 1;
        if (dataPoints <= 20) return 2;
        if (dataPoints <= 50) return 5;
        return Math.max(1, dataPoints / 10);
    }
}
