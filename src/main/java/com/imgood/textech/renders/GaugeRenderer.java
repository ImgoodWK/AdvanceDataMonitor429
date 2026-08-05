package com.imgood.textech.renders;

import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.monitor.MonitorWidgetSpec;

/** Native circular gauge; absolute sources without a real max fall back to a scalar card. */
public class GaugeRenderer implements IADMRender {

    private final StatCardRenderer fallback = new StatCardRenderer();

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing, int bindingIndex) {
        if (!nbt.getBoolean("enable")) return;
        double value = MonitorRenderSupport.latest(nbt);
        double max = MonitorRenderSupport.progressMax(nbt, value);
        if (max <= 0.0D) {
            fallback.render(nbt, x, y, z, facing, bindingIndex);
            return;
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, value / max));
        double width = MonitorRenderSupport.width(nbt);
        double height = MonitorRenderSupport.height(nbt);
        double radius = Math.min(width, height) * 0.34D;
        double cy = MonitorRenderSupport.BASE_Y + height * 0.56D;
        MonitorRenderSupport.begin(nbt);
        try {
            MonitorRenderSupport.drawTextCentered(
                MonitorWidgetSpec.getTitle(nbt),
                0.0D,
                MonitorRenderSupport.BASE_Y + height * 0.12D,
                0.03D,
                0.016D,
                MonitorRenderSupport.color(nbt, "displayNameColor", 0xFFFFFF));
            drawArc(radius, cy, 1.0D, 0x30424E, 0.9F);
            drawArc(radius, cy, ratio, MonitorRenderSupport.color(nbt, "lineColor", 0x00FFFF), 1.0F);
            MonitorRenderSupport.drawTextCentered(
                MonitorRenderSupport.format(value),
                0.0D,
                cy - 0.04D,
                0.03D,
                0.022D,
                0xFFFFFF);
            MonitorRenderSupport.drawTextCentered(
                String.format(java.util.Locale.ROOT, "%.0f%%", ratio * 100.0D),
                0.0D,
                cy + radius * 0.36D,
                0.03D,
                0.013D,
                MonitorRenderSupport.color(nbt, "axisFontColor", 0xB7C9D6));
        } finally {
            MonitorRenderSupport.end();
        }
    }

    private static void drawArc(double radius, double centerY, double ratio, int color, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(5.0F);
        MonitorRenderSupport.setColor(color, alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        int segments = Math.max(1, (int) Math.round(48.0D * ratio));
        for (int i = 0; i <= segments; i++) {
            double angle = -Math.PI / 2.0D + Math.PI * 2.0D * i / 48.0D;
            GL11.glVertex3d(Math.cos(angle) * radius, centerY + Math.sin(angle) * radius, 0.02D);
        }
        GL11.glEnd();
    }

    @Override
    public void cleanup() {}
}
