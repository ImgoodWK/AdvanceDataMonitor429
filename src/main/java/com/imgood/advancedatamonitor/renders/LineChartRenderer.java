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
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
        GL11.glDrawArrays(GL11.GL_LINE_STRIP, 0, vertices.size() / 3);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

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
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        double zOffset = 0.1;
        double xRange = nbt.getDouble("xRange");
        double xStart = -xRange / 2;
        double xEnd = xRange / 2;
        double yAxisTop = yAxisBase + yRange;
        int dataLimit = nbt.getInteger("dataLimit");
        int axisLineColor = Integer.parseInt(nbt.getString("axisLineColor"), 16);
        int axisFontColor = Integer.parseInt(nbt.getString("axisFontColor"), 16);
        int displayNameColor = Integer.parseInt(nbt.getString("displayNameColor"), 16);
        double axisFontScale = nbt.getDouble("axisFontScale");
        double displayNameScale = nbt.getDouble("displayNameScale");

        // ============== 坐标轴线VBO版本（修改部分开始）============== //
        List<Float> axisLines = new ArrayList<>();
        // X轴
        axisLines.add((float)xStart);
        axisLines.add((float)yAxisBase);
        axisLines.add((float)zOffset);
        axisLines.add((float)xEnd);
        axisLines.add((float)yAxisBase);
        axisLines.add((float)zOffset);
        // Y轴
        axisLines.add((float)xStart);
        axisLines.add((float)yAxisBase);
        axisLines.add((float)zOffset);
        axisLines.add((float)xStart);
        axisLines.add((float)yAxisTop);
        axisLines.add((float)zOffset);

        int axisVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, axisVbo);
        FloatBuffer axisBuffer = BufferUtils.createFloatBuffer(axisLines.size());
        for (float v : axisLines) {
            axisBuffer.put(v);
        }
        axisBuffer.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, axisBuffer, GL15.GL_STREAM_DRAW);

        GL11.glColor4ub(
                (byte)((axisLineColor >> 16) & 0xFF),
                (byte)((axisLineColor >> 8) & 0xFF),
                (byte)(axisLineColor & 0xFF),
                (byte)255
        );
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
        GL11.glDrawArrays(GL11.GL_LINES, 0, axisLines.size() / 3);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(axisVbo);
        // ============== 坐标轴线VBO版本（修改部分结束）============== //

        // Y轴刻度系统（VBO版本）
        double yDataBorder = dataMax - dataMin;
        double yInterval = computeOptimalInterval(yDataBorder);
        List<Float> yTicks = new ArrayList<>();
        for (double value = dataMin; value <= dataMax; value += yInterval) {
            double yPos = yAxisBase + ((value - dataMin) / yDataBorder) * yRange;
            yPos = Math.max(yAxisBase, Math.min(yAxisTop, yPos));

            yTicks.add((float)xStart);
            yTicks.add((float)yPos);
            yTicks.add((float)zOffset);
            yTicks.add((float)(xStart - 0.05));
            yTicks.add((float)yPos);
            yTicks.add((float)zOffset);
            renderText(
                    String.format("%.1f", value),
                    xStart - 0.08,
                    yPos - 0.02,
                    zOffset,
                    axisFontColor,
                    axisFontScale,
                    true,
                    facing);
        }

        if (!yTicks.isEmpty()) {
            int yTickVbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, yTickVbo);
            FloatBuffer yTickBuffer = BufferUtils.createFloatBuffer(yTicks.size());
            for (float v : yTicks) {
                yTickBuffer.put(v);
            }
            yTickBuffer.flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, yTickBuffer, GL15.GL_STREAM_DRAW);

            GL11.glColor4ub(
                    (byte)((axisLineColor >> 16) & 0xFF),
                    (byte)((axisLineColor >> 8) & 0xFF),
                    (byte)(axisLineColor & 0xFF),
                    (byte)255
            );
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
            GL11.glDrawArrays(GL11.GL_LINES, 0, yTicks.size() / 3);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glDeleteBuffers(yTickVbo);

        }

        // X轴刻度系统（VBO版本）
        if (dataLimit > 1) {
            double xStep = xRange / (dataLimit - 1);
            int labelInterval = computeOptimalXInterval(dataLimit);

            Set<Integer> majorIndices = new LinkedHashSet<>();
            majorIndices.add(0);
            majorIndices.add(dataLimit - 1);
            for (int i = labelInterval; i < dataLimit; i += labelInterval) {
                majorIndices.add(i);
            }
            List<Integer> sortedMajorIndices = new ArrayList<>(majorIndices);
            Collections.sort(sortedMajorIndices);

            // 主刻度线
            List<Float> majorTicks = new ArrayList<>();
            for (int i : sortedMajorIndices) {
                double xPos = xStart + i * xStep;
                majorTicks.add((float)xPos);
                majorTicks.add((float)yAxisBase);
                majorTicks.add((float)zOffset);
                majorTicks.add((float)xPos);
                majorTicks.add((float)(yAxisBase - 0.08));
                majorTicks.add((float)zOffset);
                String label = String.valueOf(i);
                double textOffset = label.length() * 0.015;
                renderText(
                        label,
                        xPos - textOffset,
                        yAxisBase - 0.07,
                        zOffset,
                        axisFontColor,
                        axisFontScale,
                        false,
                        facing);
            }

            if (!majorTicks.isEmpty()) {
                int majorTickVbo = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, majorTickVbo);
                FloatBuffer majorTickBuffer = BufferUtils.createFloatBuffer(majorTicks.size());
                for (float v : majorTicks) {
                    majorTickBuffer.put(v);
                }
                majorTickBuffer.flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, majorTickBuffer, GL15.GL_STREAM_DRAW);

                GL11.glColor4ub(
                        (byte)((axisLineColor >> 16) & 0xFF),
                        (byte)((axisLineColor >> 8) & 0xFF),
                        (byte)(axisLineColor & 0xFF),
                        (byte)255
                );
                GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
                GL11.glDrawArrays(GL11.GL_LINES, 0, majorTicks.size() / 3);
                GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GL15.glDeleteBuffers(majorTickVbo);
            }

            // 次刻度线（示例实现）
            List<Float> minorTicks = new ArrayList<>();
            for (int i = 0; i < sortedMajorIndices.size() - 1; i++) {
                int prevIdx = sortedMajorIndices.get(i);
                int nextIdx = sortedMajorIndices.get(i + 1);
                double xPrev = xStart + prevIdx * xStep;
                double xNext = xStart + nextIdx * xStep;

                int segments = 10;
                for (int k = 1; k < segments; k++) {
                    double xPos = xPrev + (xNext - xPrev) * k / segments;
                    minorTicks.add((float)xPos);
                    minorTicks.add((float)yAxisBase);
                    minorTicks.add((float)zOffset);
                    minorTicks.add((float)xPos);
                    minorTicks.add((float)(yAxisBase - 0.04));
                    minorTicks.add((float)zOffset);
                }
            }

            if (!minorTicks.isEmpty()) {
                int minorTickVbo = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, minorTickVbo);
                FloatBuffer minorTickBuffer = BufferUtils.createFloatBuffer(minorTicks.size());
                for (float v : minorTicks) {
                    minorTickBuffer.put(v);
                }
                minorTickBuffer.flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, minorTickBuffer, GL15.GL_STREAM_DRAW);

                GL11.glColor4ub(
                        (byte)((axisLineColor >> 16) & 0xFF),
                        (byte)((axisLineColor >> 8) & 0xFF),
                        (byte)(axisLineColor & 0xFF),
                        (byte)255
                );
                GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
                GL11.glDrawArrays(GL11.GL_LINES, 0, minorTicks.size() / 3);
                GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GL15.glDeleteBuffers(minorTickVbo);
            }
        }

        // 文字渲染（保持不变）
        String displayName = nbt.getString("displayName");
        if (!displayName.isEmpty()) {
            renderTitle(displayName, displayNameColor, displayNameScale, facing, yAxisTop);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
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
