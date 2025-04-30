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
        // 遍历所有DataBound的NBT数据进行渲染
        for (Map.Entry<Integer, NBTTagCompound> entry : monitor.getDataBoundList().entrySet()) {
            NBTTagCompound nbt = entry.getValue();

            //System.out.println("key:" + entry.getKey()+"NBT: " + nbt);
            renderDataBound(nbt, x, y, z, facing);
        }
    }

    private void renderDataBound(NBTTagCompound nbt, double x, double y, double z, int facing) {
        // 检查数据类型
        String dataType = nbt.getString("dataType");
        //if (!dataType.equals("Line")) return;
        // 获取数据值列表
        NBTTagList dataValues = nbt.getTagList("dataValues", 10);
        //System.out.println("dataValues: " + dataValues);
        if (dataValues.tagCount() < 2) return;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // 移动到方块中心
        GL11.glTranslated(x, y, z);

        // 根据 facing 绕 Y 轴旋转（关键修改点）
        float baseRotation = getRotationFromFacing(facing);
        GL11.glRotatef(baseRotation, 0, 1, 0); // 应用基础朝向旋转

        // 应用原有的自定义旋转参数
        GL11.glRotatef(nbt.getFloat("rotationX"), 1, 0, 0);
        GL11.glRotatef(nbt.getFloat("rotationY"), 0, 1, 0); // 叠加原有 Y 轴旋转（如果需要）
        GL11.glRotatef(nbt.getFloat("rotationZ"), 0, 0, 1);

        // 调整平移方向（基于 facing）
        double[] adjustedOffset = adjustOffsetByFacing(
                nbt.getFloat("xOffset"),
                nbt.getFloat("zOffset"), // 假设新增 zOffset 参数
                facing
        );
        GL11.glTranslated(
                adjustedOffset[0],
                nbt.getFloat("yOffset"),
                adjustedOffset[1] - 0.5 - 0.001 // 调整 Z 偏移
        );

        // 缩放和最终旋转
        GL11.glScalef(nbt.getFloat("scale"), nbt.getFloat("scale"), 1.0f);
        GL11.glRotatef(180, 0, 1, 0); // 可选，根据实际效果调整


        // 设置线条属性
        Tessellator tess = Tessellator.instance;
        GL11.glLineWidth(nbt.getFloat("lineWidth"));
        tess.startDrawing(GL11.GL_LINE_STRIP);
        int color = Integer.parseInt(nbt.getString("lineColor"),16);
        tess.setColorRGBA(
                (color >> 16) & 0xFF,
                (color >> 8) & 0xFF,
                color & 0xFF,
                255
        );

        // 计算坐标范围和比例
        double xRange = 0.8;
        double yRange = 0.8;
        double yMin = nbt.getDouble("yMin");
        double yMax = nbt.getDouble("yMax");
        int dataLimit = nbt.getInteger("dataLimit");

        // 动态计算X轴步长
        double xStep = xRange / (dataLimit - 1);
        //System.out.println("dataValuesCount:"+dataValues.tagCount());
        // 绘制数据点
        for (int i = 0; i < dataValues.tagCount(); i++) {
            NBTTagCompound dataPoint = dataValues.getCompoundTagAt(i);
            double value = dataPoint.getDouble("data");
            //System.out.println("value: " + value);
            double xPos = -0.4 + (i * xStep);
            double yPos = ((value - yMin) / (yMax - yMin)) * yRange - 0.4;

            // 添加边界保护
            yPos = Math.max(-0.4, Math.min(0.4, yPos));
            tess.addVertex(xPos, yPos, 0);
        }

        tess.draw();

        renderAxis(nbt, facing);

        // 恢复OpenGL状态
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    // 根据 facing 返回基础旋转角度
    private float getRotationFromFacing(int facing) {
        switch (facing) {

            case 0: return 180.0f;  // South
            case 1: return 90.0f;   // West
            case 2: return 0.0f;    // North
            case 3: return -90.0f;  // East
            default: return 0.0f;
        }
    }

    // 根据 facing 调整 X/Z 偏移方向
    private double[] adjustOffsetByFacing(float xOffset, float zOffset, int facing) {
        switch (facing) {
            case 0: // South: 反转 X 偏移
                return new double[]{-xOffset, zOffset};
            case 1: // West: 交换 X/Z 偏移
                return new double[]{zOffset, xOffset};
            case 2: // North: 保持原样
                return new double[]{xOffset, zOffset};
            case 3: // East: 交换并反转 X/Z 偏移
                return new double[]{-zOffset, -xOffset};
            default:
                return new double[]{xOffset, zOffset};
        }
    }

    private void renderAxis(NBTTagCompound nbt, int facing) {
        // 启用纹理和混合（用于文字渲染）
        GL11.glRotatef(180, 0, 1, 0); // 可选，根据实际效果调整
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tess = Tessellator.instance;

        // 绘制坐标轴
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA(255, 255, 255, 255); // 白色轴线

        // X轴
        tess.addVertex(-0.4, -0.4, 0);
        tess.addVertex(0.4, -0.4, 0);

        // Y轴
        tess.addVertex(-0.4, -0.4, 0);
        tess.addVertex(-0.4, 0.4, 0);
        tess.draw();

        // 获取数据范围
        double yMin = nbt.getDouble("yMin");
        double yMax = nbt.getDouble("yMax");
        int dataLimit = nbt.getInteger("dataLimit");
        double yRange = yMax - yMin;

        // 绘制Y轴刻度
        double yInterval = computeOptimalInterval(yMax - yMin);
        for (double value = yMin; value <= yMax; value += yInterval) {
            double yPos = ((value - yMin) / yRange) * 0.8 - 0.4;
            yPos = Math.max(-0.4, Math.min(0.4, yPos));

            // 刻度线
            tess.startDrawing(GL11.GL_LINES);
            tess.addVertex(-0.41, yPos, 0);
            tess.addVertex(-0.44, yPos, 0);
            tess.draw();

            // 修改后的条件：跳过值为0的标签
            if (value != 0 && (value / yInterval) % 2 == 0) {
                renderText(
                        String.format("%.1f", value),
                        -0.46, yPos - 0.02, 0,
                        0xFFFFFF,
                        0.5f,
                        true
                );
            }
        }

        // 绘制X轴刻度（修改后部分）
        if (dataLimit > 1) {
            double xStep = 0.8 / (dataLimit - 1);
            int[] labelPositions = computeXLabelPositions(dataLimit);

            for (int pos : labelPositions) {
                if (pos >= dataLimit) continue;

                double xPos = -0.4 + pos * xStep;

                // 绘制刻度线
                tess.startDrawing(GL11.GL_LINES);
                tess.addVertex(xPos, -0.4, 0);
                tess.addVertex(xPos, -0.43, 0);
                tess.draw();

                // 绘制标签
                renderText(
                        String.valueOf(pos),
                        xPos - 0.03,
                        -0.45,
                        0,
                        0xFFFFFF,
                        0.5f,
                        false
                );
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
    }

    private double computeOptimalInterval(double range) {
        // 智能计算刻度间隔
        if (range <= 0) return 1;
        double[] intervals = {1, 2, 5, 10, 20, 50};
        double log = Math.log10(range);
        double factor = Math.pow(10, Math.floor(log));
        double normalized = range / factor;

        for (double interval : intervals) {
            if (normalized <= interval) {
                return interval * factor / 2; // 保证刻度数适中
            }
        }
        return 10 * factor;
    }

    private void renderText(String text, double x, double y, double z, int color, float scale, boolean vertical) {

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(180, 0, 0, 1); // 调整文字方向
        GL11.glRotatef(180, 0, 1, 0); // 调整文字方向
        if (vertical) GL11.glRotatef(90, 0, 0, 1); // Y轴标签垂直
        GL11.glScalef(scale * 0.02f, scale * 0.02f, scale * 0.02f);
        mc.fontRenderer.drawString(text, -(mc.fontRenderer.getStringWidth(text) / 2), 0, color);
        GL11.glPopMatrix();
    }

    // 新增智能X轴间隔计算方法
    private int computeOptimalXInterval(int dataPoints) {
        if (dataPoints <= 0) return 1;

        // 根据数据点数量自动选择最佳间隔
        int[] candidates = {1, 2, 5, 10, 20, 50};
        int targetCount = 10; // 期望最多显示10个标签

        for (int interval : candidates) {
            if (dataPoints / interval <= targetCount) {
                return Math.max(1, interval); // 确保至少间隔1
            }
        }
        return dataPoints / targetCount;
    }

    // 新增的四点定位算法
    private int[] computeXLabelPositions(int dataLimit) {
        Set<Integer> positions = new LinkedHashSet<>();
        if (dataLimit <= 0) return new int[0];

        // 始终包含首尾
        positions.add(0);
        positions.add(dataLimit - 1);

        // 计算中间两个点
        if (dataLimit > 3) {
            positions.add(Math.round((dataLimit-1) * 1.0f / 3));
            positions.add(Math.round((dataLimit-1) * 2.0f / 3));
        }

        // 处理小数据量的情况
        while (positions.size() < 4 && dataLimit > 1) {
            for (int i = 1; i < dataLimit-1; i++) {
                positions.add(i);
                if (positions.size() >= 4) break;
            }
        }

        return positions.stream().mapToInt(i->i).toArray();
    }
}
