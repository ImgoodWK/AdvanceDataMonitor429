package com.imgood.textech.renders;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/**
 * @program: AdvanceDataMonitor429
 * @description: 修复OpenGL状态管理问�?
 * @author: Imgood
 * @create: 2025-05-15 09:22
 **/
public class LineChartRenderer implements IADMRender {

    private int vboId = -1;
    private Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing) {
        if (!nbt.getBoolean("enable")) return;
        NBTTagList dataValues = nbt.getTagList("dataValues", 10);

        // 保存当前OpenGL状�?
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        try {
            // 确保所有状态重�?
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);

            // ============== 关键修复：启用混�?============== //
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // 空间变换体系
            GL11.glRotatef(180, 0, 1, 0);
            GL11.glRotatef(nbt.getFloat("rotationX"), 1, 0, 0);
            GL11.glRotatef(nbt.getFloat("rotationY"), 0, 1, 0);
            GL11.glRotatef(nbt.getFloat("rotationZ"), 0, 0, 1);

            // 动态缩�?
            float scaleFactor = nbt.getFloat("scale");
            GL11.glScalef(scaleFactor, scaleFactor, 1.0f);

            // 计算动态数据范�?
            double dataMin = Double.MAX_VALUE;
            double dataMax = -Double.MAX_VALUE;
            for (int i = 0; i < dataValues.tagCount(); i++) {
                double val = dataValues.getCompoundTagAt(i)
                    .getDouble("data");
                if (val < dataMin) dataMin = val;
                if (val > dataMax) dataMax = val;
            }
            if (dataMin == dataMax) {
                dataMax += 1.0;
            }

            // ============== 折线绘制VBO版本 ============== //
            GL11.glLineWidth(nbt.getFloat("lineWidth"));
            int color = Integer.parseInt(nbt.getString("lineColor"), 16);
            double lineAlpha = nbt.hasKey("lineAlpha") ? nbt.getDouble("lineAlpha") : 1.0;

            // ============== 关键修复：设置混合颜�?============== //
            GL11.glColor4f(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                (float) lineAlpha // 这里使用透明�?
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
                vertices.add((float) xPos);
                vertices.add((float) yPos);
                vertices.add(0.0f);
            }

            if (nbt.getBoolean("enableData") && vertices.size() >= 6) {
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

                // 清理VBO - 确保状态恢�?
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GL15.glDeleteBuffers(vboId);
            }

            renderAxis(nbt, facing, dataMin, dataMax, yRange, Y_AXIS_BASE);

        } finally {
            // 确保所有状态恢�?
            GL11.glPopMatrix();
            GL11.glPopAttrib();

            // 强制重置可能影响字体渲染器的状�?
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        }
    }

    @Override
    public void cleanup() {
        if (vboId != -1) {
            GL15.glDeleteBuffers(vboId);
            vboId = -1;
        }
    }

    private void renderAxis(NBTTagCompound nbt, int facing, double dataMin, double dataMax, double yRange,
        double yAxisBase) {
        // 保存状�?
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        try {
            // 参数初始�?
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

            // 获取透明度�?
            double axisLineAlpha = nbt.hasKey("axisLineAlpha") ? nbt.getDouble("axisLineAlpha") : 1.0;
            double axisFontAlpha = nbt.hasKey("axisFontAlpha") ? nbt.getDouble("axisFontAlpha") : 1.0;
            double nameAlpha = nbt.hasKey("nameAlpha") ? nbt.getDouble("nameAlpha") : 1.0;

            // 新增：刻度线长度因子 (默认1.0)
            double tickLengthFactor = nbt.hasKey("tickLengthFactor") ? nbt.getDouble("tickLengthFactor") : 1.0;
            // 确保最小值为0.1
            tickLengthFactor = Math.max(0.1, tickLengthFactor);

            // 计算标签偏移�?
            double xLabelOffset = 0.05 * (1 + 0.5 * (tickLengthFactor - 1.0) * tickLengthFactor);
            double yLabelOffset = 0.05 * (1 + 0.5 * (tickLengthFactor - 1.0) * tickLengthFactor);

            // 提升sortedMajorIndices作用�?
            List<Integer> sortedMajorIndices = new ArrayList<>();

            // ========================== 计算部分 ========================== //
            // 计算坐标轴线顶点
            List<Float> axisLines = new ArrayList<>();
            axisLines.add((float) xStart);
            axisLines.add((float) yAxisBase);
            axisLines.add((float) zOffset);
            axisLines.add((float) xEnd);
            axisLines.add((float) yAxisBase);
            axisLines.add((float) zOffset);
            axisLines.add((float) xStart);
            axisLines.add((float) yAxisBase);
            axisLines.add((float) zOffset);
            axisLines.add((float) xStart);
            axisLines.add((float) yAxisTop);
            axisLines.add((float) zOffset);

            // 计算Y轴刻�?- 确保8-10个标�?
            double yDataBorder = dataMax - dataMin;
            List<Float> yTicks = new ArrayList<>();
            List<Double> yLabelValues = new ArrayList<>();
            List<Double> yTickPositions = new ArrayList<>();

            // 目标刻度数量�?-10个）
            int targetTicks = 8;
            if (yDataBorder > 0) {
                // 计算初始步长
                double tempStep = yDataBorder / targetTicks;
                double exponent = Math.floor(Math.log10(tempStep));
                double factor = Math.pow(10, exponent);
                double normalizedStep = tempStep / factor;

                // 将步长规范化�?, 2�?的倍数
                double chosenStep;
                if (normalizedStep <= 1.0) {
                    chosenStep = 1.0;
                } else if (normalizedStep <= 2.0) {
                    chosenStep = 2.0;
                } else if (normalizedStep <= 5.0) {
                    chosenStep = 5.0;
                } else {
                    chosenStep = 10.0;
                }

                double yInterval = chosenStep * factor;

                // 计算刻度位置
                double firstTick = Math.floor(dataMin / yInterval) * yInterval;
                double lastTick = Math.ceil(dataMax / yInterval) * yInterval;

                // 生成刻度
                for (double value = firstTick; value <= lastTick; value += yInterval) {
                    if (value < dataMin - 1e-6 || value > dataMax + 1e-6) continue;

                    double yPos = yAxisBase + ((value - dataMin) / yDataBorder) * yRange;
                    yPos = Math.max(yAxisBase, Math.min(yAxisTop, yPos));
                    yTicks.add((float) xStart);
                    yTicks.add((float) yPos);
                    yTicks.add((float) zOffset);
                    // 应用刻度线长度因�?
                    yTicks.add((float) (xStart - 0.05 * tickLengthFactor));
                    yTicks.add((float) yPos);
                    yTicks.add((float) zOffset);
                    yLabelValues.add(value);
                    yTickPositions.add(yPos);
                }
            }

            // 如果数据范围太小，至少显示最小值和最大�?
            if (yLabelValues.isEmpty() && yDataBorder > 0) {
                // 最小值位�?
                double minYPos = yAxisBase;
                yTicks.add((float) xStart);
                yTicks.add((float) minYPos);
                yTicks.add((float) zOffset);
                yTicks.add((float) (xStart - 0.05 * tickLengthFactor));
                yTicks.add((float) minYPos);
                yTicks.add((float) zOffset);
                yLabelValues.add(dataMin);
                yTickPositions.add(minYPos);

                // 最大值位�?
                double maxYPos = yAxisTop;
                yTicks.add((float) xStart);
                yTicks.add((float) maxYPos);
                yTicks.add((float) zOffset);
                yTicks.add((float) (xStart - 0.05 * tickLengthFactor));
                yTicks.add((float) maxYPos);
                yTicks.add((float) zOffset);
                yLabelValues.add(dataMax);
                yTickPositions.add(maxYPos);
            }

            // 计算X轴刻�?
            List<Float> majorTicks = new ArrayList<>();
            List<Float> minorTicks = new ArrayList<>();
            if (dataLimit > 1) {
                double xStep = xRange / (dataLimit - 1);
                int labelInterval = computeOptimalXInterval(dataLimit);

                int numIntervals = Math.max(1, (dataLimit - 1) / labelInterval);
                for (int i = 0; i <= numIntervals; i++) {
                    int index = i * labelInterval;
                    if (index < dataLimit) {
                        majorIndices.add(index);
                    }
                }

                if (dataLimit - 1 > (majorIndices.isEmpty() ? -1 : majorIndices.get(majorIndices.size() - 1))) {
                    majorIndices.add(dataLimit - 1);
                }

                sortedMajorIndices = new ArrayList<>(majorIndices);
                Collections.sort(sortedMajorIndices);

                // 主刻�?
                for (int i : sortedMajorIndices) {
                    double xPos = xStart + i * xStep;
                    majorTicks.add((float) xPos);
                    majorTicks.add((float) yAxisBase);
                    majorTicks.add((float) zOffset);
                    majorTicks.add((float) xPos);
                    majorTicks.add((float) (yAxisBase - 0.08 * tickLengthFactor));
                    majorTicks.add((float) zOffset);
                }

                // 次刻�?
                for (int i = 0; i < sortedMajorIndices.size() - 1; i++) {
                    int prev = sortedMajorIndices.get(i);
                    int next = sortedMajorIndices.get(i + 1);
                    int space = next - prev;

                    if (space > 1) {
                        int minorSteps = Math.min(4, space - 1);
                        for (int k = 1; k <= minorSteps; k++) {
                            double xPos = xStart + (prev + (next - prev) * k / (minorSteps + 1.0)) * xStep;
                            minorTicks.add((float) xPos);
                            minorTicks.add((float) yAxisBase);
                            minorTicks.add((float) zOffset);
                            minorTicks.add((float) xPos);
                            minorTicks.add((float) (yAxisBase - 0.04 * tickLengthFactor));
                            minorTicks.add((float) zOffset);
                        }
                    }
                }
            }

            // ========================== 渲染部分 ========================== //
            // ====================== 网格线渲�?====================== //
            boolean enableGrid = nbt.getBoolean("enableGrid");
            if (enableGrid && !yTickPositions.isEmpty() && !sortedMajorIndices.isEmpty()) {
                // 保存网格线渲染前的状�?
                GL11.glPushAttrib(GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);

                try {
                    // 准备网格线顶点数�?
                    List<Float> gridLines = new ArrayList<>();

                    // 横向网格�?
                    for (Double yPos : yTickPositions) {
                        gridLines.add((float) xStart);
                        gridLines.add(yPos.floatValue());
                        gridLines.add(0.0f);

                        gridLines.add((float) xEnd);
                        gridLines.add(yPos.floatValue());
                        gridLines.add(0.0f);
                    }

                    // 纵向网格�?
                    for (int index : sortedMajorIndices) {
                        double xPos = xStart + index * (xRange / (dataLimit - 1));
                        gridLines.add((float) xPos);
                        gridLines.add((float) yAxisBase);
                        gridLines.add(0.0f);

                        gridLines.add((float) xPos);
                        gridLines.add((float) (yAxisBase + yRange));
                        gridLines.add(0.0f);
                    }

                    if (!gridLines.isEmpty()) {
                        GL11.glEnable(GL11.GL_BLEND);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                        float gridLineWidth = (float) nbt.getDouble("gridLineWidth");
                        GL11.glLineWidth(gridLineWidth);

                        int gridLineColor;
                        if (nbt.hasKey("gridLineColor")) {
                            gridLineColor = Integer.parseInt(nbt.getString("gridLineColor"), 16);
                        } else {
                            gridLineColor = 0xffffff;
                        }

                        float gridAlpha;
                        if (nbt.hasKey("gridLineAlpha")) {
                            gridAlpha = (float) nbt.getDouble("gridLineAlpha");
                        } else {
                            gridAlpha = 0.3f;
                        }

                        GL11.glColor4f(
                            ((gridLineColor >> 16) & 0xFF) / 255.0f,
                            ((gridLineColor >> 8) & 0xFF) / 255.0f,
                            (gridLineColor & 0xFF) / 255.0f,
                            gridAlpha);

                        boolean useDashedLine;
                        if (nbt.hasKey("gridLineStyle")) {
                            useDashedLine = nbt.getBoolean("gridLineStyle");
                        } else {
                            useDashedLine = false;
                        }

                        if (useDashedLine) {
                            GL11.glEnable(GL11.GL_LINE_STIPPLE);
                            GL11.glLineStipple(1, (short) 0x00FF);
                        }

                        // 创建并渲染网格线VBO
                        int gridVbo = GL15.glGenBuffers();
                        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, gridVbo);
                        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, asFloatBuffer(gridLines), GL15.GL_STREAM_DRAW);

                        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
                        GL11.glDrawArrays(GL11.GL_LINES, 0, gridLines.size() / 3);
                        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

                        // 清理网格线VBO
                        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                        GL15.glDeleteBuffers(gridVbo);

                        if (useDashedLine) {
                            GL11.glDisable(GL11.GL_LINE_STIPPLE);
                        }

                        GL11.glDisable(GL11.GL_BLEND);
                    }
                } finally {
                    // 恢复网格线渲染前的状�?
                    GL11.glPopAttrib();
                }
            }

            GL11.glTranslatef(0, 0, -0.11F);

            if (nbt.getBoolean("enableAxis")) {
                // 保存坐标轴渲染前的状�?
                GL11.glPushAttrib(GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);

                try {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                    float axisLineWidth = (float) nbt.getDouble("axisLineWidth");
                    GL11.glLineWidth(axisLineWidth);

                    // 渲染坐标轴线，使用axisLineAlpha透明�?
                    GL11.glColor4f(
                        ((axisLineColor >> 16) & 0xFF) / 255.0f,
                        ((axisLineColor >> 8) & 0xFF) / 255.0f,
                        (axisLineColor & 0xFF) / 255.0f,
                        (float) axisLineAlpha);
                    renderLinesWithVBO(axisLines);

                    // 渲染Y轴刻�?
                    if (!yTicks.isEmpty()) {
                        GL11.glColor4f(
                            ((axisLineColor >> 16) & 0xFF) / 255.0f,
                            ((axisLineColor >> 8) & 0xFF) / 255.0f,
                            (axisLineColor & 0xFF) / 255.0f,
                            (float) axisLineAlpha);
                        renderLinesWithVBO(yTicks);
                    }

                    // 渲染X轴主刻度
                    if (!majorTicks.isEmpty()) {
                        GL11.glColor4f(
                            ((axisLineColor >> 16) & 0xFF) / 255.0f,
                            ((axisLineColor >> 8) & 0xFF) / 255.0f,
                            (axisLineColor & 0xFF) / 255.0f,
                            (float) axisLineAlpha);
                        renderLinesWithVBO(majorTicks);
                    }

                    // 渲染X轴次刻度
                    if (!minorTicks.isEmpty()) {
                        GL11.glColor4f(
                            ((axisLineColor >> 16) & 0xFF) / 255.0f,
                            ((axisLineColor >> 8) & 0xFF) / 255.0f,
                            (axisLineColor & 0xFF) / 255.0f,
                            (float) axisLineAlpha);
                        renderLinesWithVBO(minorTicks);
                    }

                    GL11.glDisable(GL11.GL_BLEND);
                } finally {
                    GL11.glPopAttrib();
                }
            }

            // 渲染文本 - 需要在调用字体渲染器前确保OpenGL状态正�?
            if (nbt.getBoolean("enableAxisFont") && !sortedMajorIndices.isEmpty()) {
                double xStep = xRange / (dataLimit - 1);

                // 保存字体渲染前的状�?- 关键修复
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);

                try {
                    // 确保VBO未绑�?
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

                    // 确保所有客户端状态已禁用
                    GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                    GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
                    GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

                    // 启用字体渲染所需状�?
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                    int standardGap = -1;
                    if (sortedMajorIndices.size() > 2) {
                        standardGap = sortedMajorIndices.get(1) - sortedMajorIndices.get(0);
                    }

                    // 应用轴字体透明�?
                    int axisFontColorWithAlpha = applyAlphaToColor(axisFontColor, axisFontAlpha);

                    for (int i = 0; i < sortedMajorIndices.size(); i++) {
                        int index = sortedMajorIndices.get(i);
                        double xPos = xStart + (index * xStep);
                        String label = String.valueOf(index);

                        if (i == sortedMajorIndices.size() - 2) {
                            int lastGap = sortedMajorIndices.get(sortedMajorIndices.size() - 1) - index;
                            if (standardGap > 0 && lastGap + 1 != standardGap) {
                                continue;
                            }
                        }

                        if (i == sortedMajorIndices.size() - 1) {
                            label = String.valueOf(dataLimit - 1);
                        }

                        double labelWidth = label.length() * 0.015;
                        double adjustedX = Math.min(xEnd - labelWidth, xPos - labelWidth / 2);

                        if (i == sortedMajorIndices.size() - 1) {
                            label = String.valueOf(dataLimit);
                        }

                        renderText(
                            label,
                            adjustedX,
                            yAxisBase - yLabelOffset,
                            zOffset,
                            axisFontColorWithAlpha,
                            axisFontScale,
                            false,
                            facing);
                    }

                    String dataName = nbt.getString("name");
                    boolean isValue = nbt.getBoolean("isValue");
                    String[] percentageKeys = { "TotalBytes", "UsedBytes", "TotalItemTypes", "UsedItemTypes",
                        "TotalFluidBytes", "UsedFluidBytes", "TotalFluidTypes", "UsedFluidTypes" };

                    boolean isSpecialKey = false;
                    for (String key : percentageKeys) {
                        if (key.equals(dataName)) {
                            isSpecialKey = true;
                            break;
                        } else {
                            isSpecialKey = false;
                        }
                    }

                    for (int i = 0; i < yLabelValues.size(); i++) {
                        double value = yLabelValues.get(i);
                        String text;

                        if (isSpecialKey) {
                            text = isValue ? String.format("%.3f", value) + "%" : String.format("%.1f", value);
                        } else {
                            text = String.format("%.1f", value);
                        }

                        double yPos = yAxisBase + ((value - dataMin) / (dataMax - dataMin)) * yRange;
                        yPos = Math.max(yAxisBase, Math.min(yAxisBase + yRange, yPos));

                        renderText(
                            text,
                            xStart - xLabelOffset,
                            yPos - 0.02,
                            zOffset,
                            axisFontColorWithAlpha,
                            axisFontScale,
                            true,
                            facing);
                    }
                } finally {
                    // 恢复字体渲染前的状�?
                    GL11.glPopClientAttrib();
                    GL11.glPopAttrib();
                }
            }

            // 标题渲染
            String displayName = nbt.getString("displayName");
            if (!displayName.isEmpty()) {
                // 保存标题渲染前的状�?
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);

                try {
                    // 确保VBO未绑�?
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

                    // 确保所有客户端状态已禁用
                    GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                    GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
                    GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

                    // 应用名称透明�?
                    int displayNameColorWithAlpha = applyAlphaToColor(displayNameColor, nameAlpha);
                    renderTitle(displayName, displayNameColorWithAlpha, displayNameScale, facing, yAxisTop);
                } finally {
                    GL11.glPopClientAttrib();
                    GL11.glPopAttrib();
                }
            }

            GL11.glEnable(GL11.GL_DEPTH_TEST);

        } finally {
            GL11.glPopAttrib();
        }
    }

    // 辅助方法：将颜色和透明度合并为ARGB格式
    private int applyAlphaToColor(int rgbColor, double alpha) {
        // 确保alpha�?-1范围�?
        alpha = Math.max(0.0, Math.min(1.0, alpha));

        // 将透明度从0-1转换�?-255
        int alphaInt = (int) (alpha * 255) & 0xFF;

        // 合并为ARGB格式�?xAARRGGBB
        return (alphaInt << 24) | (rgbColor & 0x00FFFFFF);
    }

    // 新增：使用VBO渲染线段的辅助方�?
    private void renderLinesWithVBO(List<Float> lines) {
        if (lines.isEmpty()) return;

        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, asFloatBuffer(lines), GL15.GL_STREAM_DRAW);

        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
        GL11.glDrawArrays(GL11.GL_LINES, 0, lines.size() / 3);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

        // 清理VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(vbo);
    }

    // 辅助方法：将List<Float>转换为FloatBuffer
    private FloatBuffer asFloatBuffer(List<Float> list) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(list.size());
        for (float v : list) buffer.put(v);
        buffer.flip();
        return buffer;
    }

    private void renderTitle(String title, int color, double scale, int facing, double yPos) {
        GL11.glPushMatrix();

        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_LIGHTING);

            GL11.glTranslated(0, yPos + 0.5, 0.09);
            GL11.glRotatef(180, 0, 1, 0);
            GL11.glRotatef(180, 0, 0, 1);

            int textWidth = mc.fontRenderer.getStringWidth(title);
            double scaledWidth = textWidth * scale * 0.02f;
            GL11.glTranslated(-scaledWidth / 2, 0, 0);

            GL11.glScaled(scale * 0.02f, scale * 0.02f, 1);
            mc.fontRenderer.drawString(title, 0, 0, color);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void renderText(String text, double x, double y, double z, int color, double scale, boolean vertical,
        int facing) {
        GL11.glPushMatrix();

        try {
            // 确保纹理启用
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
        } finally {
            GL11.glPopMatrix();
        }
    }

    private int computeOptimalXInterval(int dataPoints) {
        if (dataPoints <= 10) return 1;
        if (dataPoints <= 20) return 2;
        if (dataPoints <= 50) return 5;
        return Math.max(1, dataPoints / 10);
    }
}
