package com.imgood.textech.renders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.monitor.MonitorDownsampleUtil;
import com.imgood.textech.monitor.MonitorWidgetSpec;

/** Common model and immediate-mode chrome used by native monitor widget renderers. */
final class MonitorRenderSupport {

    static final double DEFAULT_WIDTH = 3.0D;
    static final double DEFAULT_HEIGHT = 2.0D;
    static final double BASE_Y = -0.4D;

    private MonitorRenderSupport() {}

    static void begin(NBTTagCompound nbt) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // Widgets are an overlay on the monitor face. Keeping depth writes from the
        // base model would otherwise hide foreground fills/text behind their backdrop.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(nbt.getFloat("rotationX"), 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(nbt.getFloat("rotationY"), 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(nbt.getFloat("rotationZ"), 0.0F, 0.0F, 1.0F);
        float scale = nbt.hasKey("scale") ? nbt.getFloat("scale") : 1.0F;
        if (scale <= 0.0F) scale = 1.0F;
        GL11.glScalef(scale, scale, 1.0F);
    }

    static void end() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    static double width(NBTTagCompound nbt) {
        double value = nbt.hasKey("xRange") ? nbt.getDouble("xRange") : DEFAULT_WIDTH;
        return value > 0.0D ? value : DEFAULT_WIDTH;
    }

    static double height(NBTTagCompound nbt) {
        double value = nbt.hasKey("yRange") ? nbt.getDouble("yRange") : DEFAULT_HEIGHT;
        return value > 0.0D ? value : DEFAULT_HEIGHT;
    }

    static List<Double> values(NBTTagCompound nbt) {
        NBTTagList data = nbt.getTagList("dataValues", 10);
        if (data.tagCount() == 0) return Collections.emptyList();
        List<Double> values = new ArrayList<Double>(data.tagCount());
        for (int i = 0; i < data.tagCount(); i++) {
            values.add(Double.valueOf(data.getCompoundTagAt(i).getDouble("data")));
        }
        if (MonitorWidgetSpec.SERIES_TRANSFORM_DIFFERENCE.equals(MonitorWidgetSpec.getSeriesTransform(nbt))) {
            values = MonitorDownsampleUtil.difference(values);
        }
        int visibleWidth = Math.max(2, (int) Math.round(width(nbt) * 120.0D));
        return MonitorDownsampleUtil.downsample(values, visibleWidth);
    }

    static double latest(NBTTagCompound nbt) {
        List<Double> values = values(nbt);
        return values.isEmpty() ? 0.0D : values.get(values.size() - 1).doubleValue();
    }

    static double progressMax(NBTTagCompound nbt, double value) {
        double target = MonitorWidgetSpec.getTargetValue(nbt);
        boolean wirelessSteam = MonitorWidgetSpec.SOURCE_WIRELESS_STEAM.equals(MonitorWidgetSpec.getSourceKind(nbt));

        if (nbt.getBoolean("capacityKnown")) {
            double capacity = nbt.hasKey("capacity") ? nbt.getDouble("capacity") : nbt.getDouble("maxValue");
            if (capacity > 0.0D) return capacity;
        }

        String metric = MonitorWidgetSpec.getMetricKey(nbt).toLowerCase(Locale.ROOT);
        if (!wirelessSteam && (metric.contains("percent") || metric.contains("ratio") || metric.contains("usage"))) {
            return 100.0D;
        }

        if (target > 0.0D) return target;
        if (wirelessSteam) return 0.0D;
        if (nbt.hasKey("maxValue") && nbt.getDouble("maxValue") > 0.0D) return nbt.getDouble("maxValue");
        double configuredMax = nbt.hasKey("yMax") ? nbt.getDouble("yMax") : 0.0D;
        return configuredMax > 0.0D && configuredMax >= value ? configuredMax : 0.0D;
    }

    static int color(NBTTagCompound nbt, String key, int fallback) {
        if (!nbt.hasKey(key)) return fallback;
        String raw = nbt.getString(key).trim();
        if (raw.startsWith("#")) raw = raw.substring(1);
        try {
            return Integer.parseInt(raw, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static void setColor(int rgb, float alpha) {
        GL11.glColor4f(
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >> 8) & 0xFF) / 255.0F,
            (rgb & 0xFF) / 255.0F,
            Math.max(0.0F, Math.min(1.0F, alpha)));
    }

    static void drawQuad(double left, double bottom, double right, double top, double z, int rgb, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        setColor(rgb, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(left, bottom, z);
        GL11.glVertex3d(right, bottom, z);
        GL11.glVertex3d(right, top, z);
        GL11.glVertex3d(left, top, z);
        GL11.glEnd();
    }

    static void drawOutline(double left, double bottom, double right, double top, double z, int rgb, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        setColor(rgb, alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(left, bottom, z);
        GL11.glVertex3d(right, bottom, z);
        GL11.glVertex3d(right, top, z);
        GL11.glVertex3d(left, top, z);
        GL11.glEnd();
    }

    static void drawTextCentered(String text, double centerX, double y, double z, double scale, int rgb) {
        if (text == null || text.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glTranslated(centerX, y, z);
        GL11.glScaled(scale, -scale, scale);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.fontRenderer.drawString(text, -mc.fontRenderer.getStringWidth(text) / 2, 0, rgb);
        GL11.glPopMatrix();
    }

    static String format(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000.0D) return String.format(Locale.ROOT, "%.2e", value);
        if (absolute >= 1000.0D) return String.format(Locale.ROOT, "%,.0f", value);
        if (absolute >= 10.0D) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static List<Category> categories(NBTTagCompound nbt, int limit) {
        NBTTagList categories = nbt.getTagList("categories", 10);
        List<Category> result = new ArrayList<Category>();
        if (categories.tagCount() > 0) {
            for (int i = 0; i < categories.tagCount() && result.size() < limit; i++) {
                NBTTagCompound category = categories.getCompoundTagAt(i);
                result.add(new Category(category.getString("label"), category.getDouble("value")));
            }
            return result;
        }
        List<Double> values = values(nbt);
        int start = Math.max(0, values.size() - limit);
        for (int i = start; i < values.size(); i++) {
            result.add(new Category(String.valueOf(i + 1), values.get(i).doubleValue()));
        }
        return result;
    }

    static final class Category {

        final String label;
        final double value;

        Category(String label, double value) {
            this.label = label == null || label.isEmpty() ? "-" : label;
            this.value = value;
        }
    }
}
