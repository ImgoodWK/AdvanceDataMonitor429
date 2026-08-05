package com.imgood.textech.renders;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.monitor.MonitorWidgetSpec;

/** Native categorical pie chart backed by explicit categories or recent samples. */
public class PieChartRenderer implements IADMRender {

    private final StatCardRenderer fallback = new StatCardRenderer();

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing, int bindingIndex) {
        if (!nbt.getBoolean("enable")) return;
        List<MonitorRenderSupport.Category> categories = MonitorRenderSupport.categories(nbt, 10);
        double total = 0.0D;
        for (MonitorRenderSupport.Category category : categories) total += Math.abs(category.value);
        if (total <= 0.0D) {
            fallback.render(nbt, x, y, z, facing, bindingIndex);
            return;
        }

        double width = MonitorRenderSupport.width(nbt);
        double height = MonitorRenderSupport.height(nbt);
        double radius = Math.min(width, height) * 0.34D;
        double cy = MonitorRenderSupport.BASE_Y + height * 0.58D;
        double angle = -Math.PI / 2.0D;
        MonitorRenderSupport.begin(nbt);
        try {
            MonitorRenderSupport.drawTextCentered(
                MonitorWidgetSpec.getTitle(nbt),
                0.0D,
                MonitorRenderSupport.BASE_Y + height * 0.08D,
                0.03D,
                0.016D,
                MonitorRenderSupport.color(nbt, "displayNameColor", 0xFFFFFF));
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            for (int i = 0; i < categories.size(); i++) {
                double sweep = Math.PI * 2.0D * Math.abs(categories.get(i).value) / total;
                int segments = Math.max(2, (int) Math.ceil(sweep / (Math.PI / 24.0D)));
                MonitorRenderSupport.setColor(
                    BarChartRenderer.palette(i, MonitorRenderSupport.color(nbt, "lineColor", 0x00FFFF)),
                    0.92F);
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                GL11.glVertex3d(0.0D, cy, 0.01D);
                for (int segment = 0; segment <= segments; segment++) {
                    double current = angle + sweep * segment / segments;
                    GL11.glVertex3d(Math.cos(current) * radius, cy + Math.sin(current) * radius, 0.01D);
                }
                GL11.glEnd();
                angle += sweep;
            }
        } finally {
            MonitorRenderSupport.end();
        }
    }

    @Override
    public void cleanup() {}
}
