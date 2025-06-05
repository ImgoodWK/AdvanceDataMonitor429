package com.imgood.advancedatamonitor.renders;

import java.nio.FloatBuffer;
import java.text.DecimalFormat;
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


    private void renderAxis(NBTTagCompound nbt, int facing, double dataMin, double dataMax, double yRange,
                            double yAxisBase) {
        // 参数初始化
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

        // 新增：刻度线长度因子 (默认1.0)
        double tickLengthFactor = nbt.hasKey("tickLengthFactor") ?
                nbt.getDouble("tickLengthFactor") : 1.0;
        // 确保最小值为0.1
        tickLengthFactor = Math.max(0.1, tickLengthFactor);

        // 计算标签偏移量 - 使用非线性函数确保刻度线变长时标签远离更多
        // 当tickLengthFactor=1.0时，偏移量为原始值
        // 当tickLengthFactor>1.0时，偏移量增加更快
        double xLabelOffset = 0.05 * (1 + 0.5 * (tickLengthFactor - 1.0) * tickLengthFactor);
        double yLabelOffset = 0.05 * (1 + 0.5 * (tickLengthFactor - 1.0) * tickLengthFactor);

        // 提升sortedMajorIndices作用域
        List<Integer> sortedMajorIndices = new ArrayList<>();

        // ========================== 计算部分 ========================== //
        // 计算坐标轴线顶点
        List<Float> axisLines = new ArrayList<>();
        axisLines.add((float)xStart); axisLines.add((float)yAxisBase); axisLines.add((float)zOffset);
        axisLines.add((float)xEnd); axisLines.add((float)yAxisBase); axisLines.add((float)zOffset);
        axisLines.add((float)xStart); axisLines.add((float)yAxisBase); axisLines.add((float)zOffset);
        axisLines.add((float)xStart); axisLines.add((float)yAxisTop); axisLines.add((float)zOffset);

        // 计算Y轴刻度 - 确保8-10个标签
        double yDataBorder = dataMax - dataMin;
        List<Float> yTicks = new ArrayList<>();
        List<Double> yLabelValues = new ArrayList<>();
        List<Double> yTickPositions = new ArrayList<>(); // 新增：存储刻度位置

        // 目标刻度数量（8-10个）
        int targetTicks = 8;
        if (yDataBorder > 0) {
            // 计算初始步长
            double tempStep = yDataBorder / targetTicks;
            // 将步长转换为10的幂次形式
            double exponent = Math.floor(Math.log10(tempStep));
            double factor = Math.pow(10, exponent);
            double normalizedStep = tempStep / factor;

            // 将步长规范化为1, 2或5的倍数
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
                // 确保不会超出数据范围
                if (value < dataMin - 1e-6 || value > dataMax + 1e-6) continue;

                double yPos = yAxisBase + ((value - dataMin) / yDataBorder) * yRange;
                yPos = Math.max(yAxisBase, Math.min(yAxisTop, yPos));
                yTicks.add((float)xStart);
                yTicks.add((float)yPos);
                yTicks.add((float)zOffset);
                // 应用刻度线长度因子
                yTicks.add((float)(xStart - 0.05 * tickLengthFactor));
                yTicks.add((float)yPos);
                yTicks.add((float)zOffset);
                yLabelValues.add(value);
                yTickPositions.add(yPos); // 存储刻度位置
            }
        }

        // 如果数据范围太小，至少显示最小值和最大值
        if (yLabelValues.isEmpty() && yDataBorder > 0) {
            // 最小值位置
            double minYPos = yAxisBase;
            yTicks.add((float)xStart);
            yTicks.add((float)minYPos);
            yTicks.add((float)zOffset);
            // 应用刻度线长度因子
            yTicks.add((float)(xStart - 0.05 * tickLengthFactor));
            yTicks.add((float)minYPos);
            yTicks.add((float)zOffset);
            yLabelValues.add(dataMin);
            yTickPositions.add(minYPos);

            // 最大值位置
            double maxYPos = yAxisTop;
            yTicks.add((float)xStart);
            yTicks.add((float)maxYPos);
            yTicks.add((float)zOffset);
            // 应用刻度线长度因子
            yTicks.add((float)(xStart - 0.05 * tickLengthFactor));
            yTicks.add((float)maxYPos);
            yTicks.add((float)zOffset);
            yLabelValues.add(dataMax);
            yTickPositions.add(maxYPos);
        }

        // 计算X轴刻度 - 修复刻度位置计算逻辑
        List<Float> majorTicks = new ArrayList<>();
        List<Float> minorTicks = new ArrayList<>();
        if (dataLimit > 1) {
            double xStep = xRange / (dataLimit - 1);
            int labelInterval = computeOptimalXInterval(dataLimit);

            // 修复：使用更精确的刻度位置计算方法
            int numIntervals = Math.max(1, (dataLimit - 1) / labelInterval);
            for (int i = 0; i <= numIntervals; i++) {
                int index = i * labelInterval;
                if (index < dataLimit) {
                    majorIndices.add(index);
                }
            }

            // 确保包含最后一个数据点
            if (dataLimit - 1 > (majorIndices.isEmpty() ? -1 : majorIndices.get(majorIndices.size() - 1))) {
                majorIndices.add(dataLimit - 1);
            }

            sortedMajorIndices = new ArrayList<>(majorIndices);
            Collections.sort(sortedMajorIndices);

            // 主刻度
            for (int i : sortedMajorIndices) {
                double xPos = xStart + i * xStep;
                majorTicks.add((float)xPos); majorTicks.add((float)yAxisBase); majorTicks.add((float)zOffset);
                // 应用刻度线长度因子
                majorTicks.add((float)xPos); majorTicks.add((float)(yAxisBase - 0.08 * tickLengthFactor)); majorTicks.add((float)zOffset);
            }

            // 次刻度 - 仅在两个主刻度之间有足够空间时添加
            for (int i = 0; i < sortedMajorIndices.size() - 1; i++) {
                int prev = sortedMajorIndices.get(i);
                int next = sortedMajorIndices.get(i + 1);
                int space = next - prev;

                if (space > 1) {
                    int minorSteps = Math.min(4, space - 1); // 最多4个次刻度
                    for (int k = 1; k <= minorSteps; k++) {
                        double xPos = xStart + (prev + (next - prev) * k / (minorSteps + 1.0)) * xStep;
                        minorTicks.add((float)xPos); minorTicks.add((float)yAxisBase); minorTicks.add((float)zOffset);
                        // 应用刻度线长度因子
                        minorTicks.add((float)xPos); minorTicks.add((float)(yAxisBase - 0.04 * tickLengthFactor)); minorTicks.add((float)zOffset);
                    }
                }
            }
        }

        // ========================== 渲染部分 ========================== //
        // ====================== 网格线渲染 ====================== //
        // 使用NBT参数控制网格线
        boolean enableGrid = nbt.getBoolean("enableGrid");
        if (enableGrid && !yTickPositions.isEmpty() && !sortedMajorIndices.isEmpty()) {
            // 准备网格线顶点数据
            List<Float> gridLines = new ArrayList<>();

            // 横向网格线 (Y轴刻度位置)
            for (Double yPos : yTickPositions) {
                gridLines.add((float)xStart);
                gridLines.add(yPos.floatValue());
                gridLines.add(0.0f); // 与折线在同一平面

                gridLines.add((float)xEnd);
                gridLines.add(yPos.floatValue());
                gridLines.add(0.0f);
            }

            // 纵向网格线 (X轴主刻度位置)
            for (int index : sortedMajorIndices) {
                double xPos = xStart + index * (xRange / (dataLimit - 1));
                gridLines.add((float)xPos);
                gridLines.add((float)yAxisBase);
                gridLines.add(0.0f);

                gridLines.add((float)xPos);
                gridLines.add((float)(yAxisBase + yRange));
                gridLines.add(0.0f);
            }

            if (!gridLines.isEmpty()) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                // 使用NBT参数设置网格线宽
                float gridLineWidth = (float) nbt.getDouble("gridLineWidth");
                GL11.glLineWidth(gridLineWidth);

                // 使用NBT参数设置网格线颜色
                int gridLineColor;
                if (nbt.hasKey("gridLineColor")) {
                    gridLineColor = Integer.parseInt(nbt.getString("gridLineColor"), 16);
                } else {
                    gridLineColor = 0xffffff; // 默认颜色
                }

                float gridAlpha;
                if (nbt.hasKey("gridLineAlpha")) {
                    gridAlpha = (float) nbt.getDouble("gridLineAlpha");
                } else {
                    gridAlpha = 0.3f; // 默认透明度
                }
                GL11.glColor4f(
                        ((gridLineColor >> 16) & 0xFF) / 255.0f,
                        ((gridLineColor >> 8) & 0xFF) / 255.0f,
                        (gridLineColor & 0xFF) / 255.0f,
                        gridAlpha
                );
                boolean useDashedLine;
                if (nbt.hasKey("gridLineStyle")) {
                    useDashedLine = nbt.getBoolean("gridLineStyle");
                } else {
                    useDashedLine = true; // 默认不使用虚线
                }


                if (useDashedLine) {
                    // 虚线模式
                    GL11.glEnable(GL11.GL_LINE_STIPPLE);
                    GL11.glLineStipple(1, (short) 0x00FF); // 虚线模式: 1像素实线 + 1像素空白
                }

                // 创建并渲染网格线VBO
                int gridVbo = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, gridVbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, asFloatBuffer(gridLines), GL15.GL_STREAM_DRAW);

                GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L);
                GL11.glDrawArrays(GL11.GL_LINES, 0, gridLines.size() / 3);
                GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

                if (useDashedLine) {
                    // 禁用虚线模式
                    GL11.glDisable(GL11.GL_LINE_STIPPLE);
                }

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GL15.glDeleteBuffers(gridVbo);

                GL11.glDisable(GL11.GL_BLEND);
            }
        }
        GL11.glTranslatef(0,0,-0.11F);
        if (nbt.getBoolean("enableAxis")) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);


            // 使用NBT参数设置坐标轴线宽
            float axisLineWidth = (float) nbt.getDouble("axisLineWidth");
            GL11.glLineWidth(axisLineWidth);

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

        // 修复点：使用排序后的索引列表渲染X轴标签
        if (nbt.getBoolean("enableAxisFont") && !sortedMajorIndices.isEmpty()) {
            double xStep = xRange / (dataLimit - 1);

            // 计算标准刻度间隔（仅当有多个主刻度时）
            int standardGap = -1;
            if (sortedMajorIndices.size() > 2) {
                standardGap = sortedMajorIndices.get(1) - sortedMajorIndices.get(0);
            }

            for (int i = 0; i < sortedMajorIndices.size(); i++) {
                int index = sortedMajorIndices.get(i);
                double xPos = xStart + (index * xStep);
                String label = String.valueOf(index);

                // 检查是否是最后两个刻度
                if (i == sortedMajorIndices.size() - 2) {
                    // 计算最后两个刻度的实际间隔
                    int lastGap = sortedMajorIndices.get(sortedMajorIndices.size()-1) - index;

                    // 如果最后两个刻度间隔与标准间隔不同，跳过倒数第二个标签
                    if (standardGap > 0 && lastGap+1 != standardGap) {
                        continue;
                    }
                }

                // 总是渲染最后一个标签
                if (i == sortedMajorIndices.size() - 1) {
                    label = String.valueOf(dataLimit - 1); // 显示实际最后一个索引
                }

                // 添加边界检查防止溢出
                double labelWidth = label.length() * 0.015;
                // 修改点1：减少X轴标签水平偏移（从整个宽度改为一半宽度）
                double adjustedX = Math.min(xEnd - labelWidth, xPos - labelWidth/2);
                if (i == sortedMajorIndices.size()-1) {
                    label = String.valueOf(dataLimit);
                }
                // 修改：根据刻度线长度因子调整Y轴偏移
                renderText(
                        label,
                        adjustedX,
                        yAxisBase - yLabelOffset, // 使用计算出的标签偏移量
                        zOffset,
                        axisFontColor,
                        axisFontScale,
                        false,
                        facing
                );
            }

            String dataName = nbt.getString("name");
            boolean isValue = nbt.getBoolean("isValue");
            String[] percentageKeys = {
                    "TotalBytes", "UsedBytes", "TotalItemTypes", "UsedItemTypes",
                    "TotalFluidBytes", "UsedFluidBytes", "TotalFluidTypes", "UsedFluidTypes"
            };

            // 检查是否为特殊键值
            boolean isSpecialKey = false;
            for (String key : percentageKeys) {
                if (key.equals(dataName)) {
                    isSpecialKey = true;
                    break;
                } else {
                    isSpecialKey = false;
                }
            }

            // 统一处理渲染逻辑
            for (int i = 0; i < yLabelValues.size(); i++) {
                double value = yLabelValues.get(i);
                String text;

                if (isSpecialKey) {
                    text = isValue ? String.format("%.3f", value)+"%" : String.format("%.1f", value);
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
                        axisFontColor,
                        axisFontScale,
                        true,
                        facing
                );
            }

        }

        // 标题渲染
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

        GL11.glTranslated(0, yPos + 0.5, 0.09);
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