package com.imgood.textech.renders;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.monitor.MonitorWidgetSpec;

/** Native categorical bar chart backed by explicit categories or recent samples. */
public class BarChartRenderer implements IADMRender {

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing, int bindingIndex) {
        if (!nbt.getBoolean("enable")) return;
        List<MonitorRenderSupport.Category> categories = MonitorRenderSupport.categories(nbt, 12);
        double width = MonitorRenderSupport.width(nbt);
        double height = MonitorRenderSupport.height(nbt);
        double chartHeight = height * 0.72D;
        double max = 1.0D;
        for (MonitorRenderSupport.Category category : categories) max = Math.max(max, Math.abs(category.value));

        MonitorRenderSupport.begin(nbt);
        try {
            MonitorRenderSupport.drawTextCentered(
                MonitorWidgetSpec.getTitle(nbt),
                0.0D,
                MonitorRenderSupport.BASE_Y + height * 0.05D,
                0.03D,
                0.016D,
                MonitorRenderSupport.color(nbt, "displayNameColor", 0xFFFFFF));
            if (categories.isEmpty()) return;
            double slot = width / categories.size();
            for (int i = 0; i < categories.size(); i++) {
                MonitorRenderSupport.Category category = categories.get(i);
                double left = -width / 2.0D + i * slot + slot * 0.12D;
                double right = left + slot * 0.76D;
                double bottom = MonitorRenderSupport.BASE_Y + height * 0.22D;
                double top = bottom + chartHeight * Math.abs(category.value) / max;
                int color = palette(i, MonitorRenderSupport.color(nbt, "lineColor", 0x00FFFF));
                MonitorRenderSupport.drawQuad(left, bottom, right, top, 0.01D, color, 0.9F);
                if (categories.size() <= 8) {
                    MonitorRenderSupport.drawTextCentered(category.label, (left + right) / 2.0D, bottom - 0.08D, 0.03D,
                        0.010D, 0xFFFFFF);
                }
            }
        } finally {
            MonitorRenderSupport.end();
        }
    }

    static int palette(int index, int fallback) {
        int[] colors = { fallback, 0x52C41A, 0xFAAD14, 0xA66CFF, 0x3EA6FF, 0xFF6B81 };
        return colors[index % colors.length];
    }

    @Override
    public void cleanup() {}
}
