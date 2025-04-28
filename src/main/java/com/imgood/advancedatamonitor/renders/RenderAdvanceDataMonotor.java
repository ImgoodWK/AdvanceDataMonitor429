// RenderAdvanceDataMonotor.java
package com.imgood.advancedatamonitor.renders;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;
import java.util.List;

public class RenderAdvanceDataMonotor extends TileEntitySpecialRenderer {
    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityAdvanceDataMonotor)) return;
        TileEntityAdvanceDataMonotor monitor = (TileEntityAdvanceDataMonotor) te;
        List<Double> data = monitor.getDataValues();
        if (data.size() < 2) return;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // 应用变换参数
        GL11.glTranslated(x + 0.5, y + 0.5 + monitor.getHeightOffset(), z + 0.5);
        GL11.glScalef(monitor.getScale(), monitor.getScale(), monitor.getScale());
        GL11.glRotatef(monitor.getRotationY(), 0, 1, 0);
        GL11.glRotatef(monitor.getRotationX(), 1, 0, 0);
        GL11.glRotatef(monitor.getRotationZ(), 0, 0, 1);
        GL11.glTranslated(0, 0, -0.5 - 0.001);
        GL11.glRotatef(180, 0, 1, 0);

        // 绘制设置
        Tessellator tess = Tessellator.instance;
        GL11.glLineWidth(monitor.getLineWidth());
        tess.startDrawing(GL11.GL_LINE_STRIP);
        tess.setColorRGBA(
                (monitor.getLineColor() >> 16) & 0xFF,
                (monitor.getLineColor() >> 8) & 0xFF,
                monitor.getLineColor() & 0xFF,
                255
        );

        // 坐标计算
        double xAxisMin = -0.4;
        double xAxisMax = 0.4;
        double yMin = monitor.getYMin();
        double yMax = monitor.getYMax();
        int dataLimit = monitor.getDataLimit();

        for (int i = 0; i < data.size(); i++) {
            double value = data.get(i);
            double xPos = xAxisMin + (i / (double) (dataLimit - 1)) * (xAxisMax - xAxisMin);
            double yPos = ((value - yMin) / (yMax - yMin)) * 0.8 - 0.4;
            tess.addVertex(xPos, yPos, 0);
        }

        tess.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }
}