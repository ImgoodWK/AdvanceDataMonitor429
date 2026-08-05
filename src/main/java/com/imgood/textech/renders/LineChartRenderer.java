package com.imgood.textech.renders;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.monitor.MonitorWidgetSpec;

/** Cached native time-series renderer shared by lineChart and legacy line/difference bindings. */
public class LineChartRenderer implements IADMRender {

    @Override
    public void render(final NBTTagCompound nbt, double x, double y, double z, int facing, int bindingIndex) {
        if (!nbt.getBoolean("enable")) return;

        final List<Double> values = MonitorRenderSupport.values(nbt);
        final double[] range = range(values);
        final double width = MonitorRenderSupport.width(nbt);
        final double height = MonitorRenderSupport.height(nbt);
        MonitorRenderCache.Entry cache = MonitorRenderCache.getOrBuild(
            (int) x,
            (int) y,
            (int) z,
            bindingIndex,
            MonitorWidgetSpec.getRevision(nbt),
            new MonitorRenderCache.GeometryFactory() {

                @Override
                public MonitorRenderCache.Geometry build() {
                    return buildGeometry(values, range[0], range[1], width, height);
                }
            });

        MonitorRenderSupport.begin(nbt);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            if (nbt.getBoolean("enableAxis")) {
                GL11.glLineWidth(nbt.hasKey("gridLineWidth") ? Math.max(0.25F, nbt.getFloat("gridLineWidth")) : 0.8F);
                MonitorRenderSupport.setColor(
                    MonitorRenderSupport.color(nbt, "axisLineColor", 0xB7C9D6),
                    nbt.hasKey("gridLineAlpha") ? nbt.getFloat("gridLineAlpha") : 0.35F);
                cache.draw(2);

                GL11.glLineWidth(nbt.hasKey("axisLineWidth") ? Math.max(0.5F, nbt.getFloat("axisLineWidth")) : 1.0F);
                MonitorRenderSupport.setColor(
                    MonitorRenderSupport.color(nbt, "axisLineColor", 0xFFFFFF),
                    nbt.hasKey("axisLineAlpha") ? nbt.getFloat("axisLineAlpha") : 1.0F);
                cache.draw(1);
            }

            if (nbt.getBoolean("enableData")) {
                GL11.glLineWidth(nbt.hasKey("lineWidth") ? Math.max(0.5F, nbt.getFloat("lineWidth")) : 2.0F);
                MonitorRenderSupport.setColor(
                    MonitorRenderSupport.color(nbt, "lineColor", 0x00FFFF),
                    nbt.hasKey("lineAlpha") ? nbt.getFloat("lineAlpha") : 1.0F);
                cache.draw(0);
            }

            int fontColor = MonitorRenderSupport.color(nbt, "axisFontColor", 0xFFFFFF);
            String title = MonitorWidgetSpec.getTitle(nbt);
            MonitorRenderSupport.drawTextCentered(
                title,
                0.0D,
                MonitorRenderSupport.BASE_Y + height + 0.18D,
                0.03D,
                0.016D,
                MonitorRenderSupport.color(nbt, "displayNameColor", fontColor));
            if (nbt.getBoolean("enableAxisFont")) {
                MonitorRenderSupport.drawTextCentered(
                    MonitorRenderSupport.format(range[0]),
                    -width / 2.0D,
                    MonitorRenderSupport.BASE_Y - 0.12D,
                    0.03D,
                    0.012D,
                    fontColor);
                MonitorRenderSupport.drawTextCentered(
                    MonitorRenderSupport.format(range[1]),
                    -width / 2.0D,
                    MonitorRenderSupport.BASE_Y + height + 0.02D,
                    0.03D,
                    0.012D,
                    fontColor);
            }
        } finally {
            MonitorRenderSupport.end();
        }
    }

    @Override
    public void cleanup() {
        MonitorRenderCache.clear();
    }

    private static MonitorRenderCache.Geometry buildGeometry(List<Double> values, double min, double max, double width,
        double height) {
        List<Float> line = new ArrayList<Float>();
        if (values.size() == 1) {
            add(
                line,
                0.0D,
                ordinate(
                    values.get(0)
                        .doubleValue(),
                    min,
                    max,
                    height),
                0.02D);
        } else if (values.size() > 1) {
            double step = width / (values.size() - 1.0D);
            for (int i = 0; i < values.size(); i++) {
                add(
                    line,
                    -width / 2.0D + i * step,
                    ordinate(
                        values.get(i)
                            .doubleValue(),
                        min,
                        max,
                        height),
                    0.02D);
            }
        }

        List<Float> axes = new ArrayList<Float>();
        addLine(axes, -width / 2.0D, MonitorRenderSupport.BASE_Y, width / 2.0D, MonitorRenderSupport.BASE_Y, 0.01D);
        addLine(
            axes,
            -width / 2.0D,
            MonitorRenderSupport.BASE_Y,
            -width / 2.0D,
            MonitorRenderSupport.BASE_Y + height,
            0.01D);

        List<Float> grid = new ArrayList<Float>();
        for (int i = 1; i < 5; i++) {
            double gy = MonitorRenderSupport.BASE_Y + height * i / 5.0D;
            addLine(grid, -width / 2.0D, gy, width / 2.0D, gy, 0.0D);
        }
        int verticals = Math.min(12, Math.max(2, values.size()));
        for (int i = 1; i < verticals; i++) {
            double gx = -width / 2.0D + width * i / verticals;
            addLine(grid, gx, MonitorRenderSupport.BASE_Y, gx, MonitorRenderSupport.BASE_Y + height, 0.0D);
        }

        return new MonitorRenderCache.Geometry().add(GL11.GL_LINE_STRIP, array(line))
            .add(GL11.GL_LINES, array(axes))
            .add(GL11.GL_LINES, array(grid));
    }

    private static double ordinate(double value, double min, double max, double height) {
        double ratio = max <= min ? 0.5D : (value - min) / (max - min);
        return MonitorRenderSupport.BASE_Y + Math.max(0.0D, Math.min(1.0D, ratio)) * height;
    }

    private static double[] range(List<Double> values) {
        if (values.isEmpty()) return new double[] { 0.0D, 1.0D };
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Double value : values) {
            min = Math.min(min, value.doubleValue());
            max = Math.max(max, value.doubleValue());
        }
        if (min == max) {
            double pad = Math.max(1.0D, Math.abs(min) * 0.05D);
            min -= pad;
            max += pad;
        }
        return new double[] { min, max };
    }

    private static void addLine(List<Float> target, double x1, double y1, double x2, double y2, double z) {
        add(target, x1, y1, z);
        add(target, x2, y2, z);
    }

    private static void add(List<Float> target, double x, double y, double z) {
        target.add(Float.valueOf((float) x));
        target.add(Float.valueOf((float) y));
        target.add(Float.valueOf((float) z));
    }

    private static float[] array(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i)
            .floatValue();
        return result;
    }
}
