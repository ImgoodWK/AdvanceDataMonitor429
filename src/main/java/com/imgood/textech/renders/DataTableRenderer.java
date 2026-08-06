package com.imgood.textech.renders;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.imgood.textech.monitor.MonitorWidgetSpec;

/** Compact native table renderer. Undefined columns use defaults; an explicit empty list hides all columns. */
public class DataTableRenderer implements IADMRender {

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing, int bindingIndex) {
        if (!nbt.getBoolean("enable")) return;
        int maxRows = Math.max(1, Math.min(20, nbt.hasKey("maxRows") ? nbt.getInteger("maxRows") : 10));
        List<MonitorRenderSupport.Category> rows = MonitorRenderSupport.categories(nbt, maxRows);
        double width = MonitorRenderSupport.width(nbt);
        double height = MonitorRenderSupport.height(nbt);
        boolean explicitHidden = nbt.hasKey("columns") && nbt.getTagList("columns", 8)
            .tagCount() == 0;
        boolean showLabel = !explicitHidden && columnVisible(nbt, "label", true);
        boolean showValue = !explicitHidden && columnVisible(nbt, "value", true);

        MonitorRenderSupport.begin(nbt);
        try {
            MonitorRenderSupport.drawQuad(
                -width / 2.0D,
                MonitorRenderSupport.BASE_Y,
                width / 2.0D,
                MonitorRenderSupport.BASE_Y + height,
                0.0D,
                0x07151F,
                0.72F);
            MonitorRenderSupport.drawTextCentered(
                MonitorWidgetSpec.getTitle(nbt),
                0.0D,
                MonitorRenderSupport.BASE_Y + 0.08D,
                0.03D,
                0.016D,
                MonitorRenderSupport.color(nbt, "displayNameColor", 0xFFFFFF));
            if (explicitHidden) return;
            double rowHeight = Math.max(0.11D, (height - 0.32D) / Math.max(1, rows.size()));
            for (int i = 0; i < rows.size(); i++) {
                MonitorRenderSupport.Category row = rows.get(rows.size() - 1 - i);
                double rowY = MonitorRenderSupport.BASE_Y + 0.30D + i * rowHeight;
                if ((i & 1) == 1) {
                    MonitorRenderSupport.drawQuad(
                        -width / 2.0D + 0.04D,
                        rowY - 0.02D,
                        width / 2.0D - 0.04D,
                        rowY + rowHeight - 0.02D,
                        0.01D,
                        0x18303D,
                        0.5F);
                }
                if (showLabel) {
                    MonitorRenderSupport.drawTextCentered(row.label, -width * 0.23D, rowY, 0.03D, 0.011D, 0xD8E6EF);
                }
                if (showValue) {
                    MonitorRenderSupport.drawTextCentered(
                        MonitorRenderSupport.format(row.value),
                        width * 0.23D,
                        rowY,
                        0.03D,
                        0.011D,
                        MonitorRenderSupport.color(nbt, "lineColor", 0x00FFFF));
                }
            }
        } finally {
            MonitorRenderSupport.end();
        }
    }

    private static boolean columnVisible(NBTTagCompound nbt, String column, boolean defaultValue) {
        if (!nbt.hasKey("columns")) return defaultValue;
        NBTTagList columns = nbt.getTagList("columns", 8);
        for (int i = 0; i < columns.tagCount(); i++) {
            if (column.equals(columns.getStringTagAt(i))) return true;
        }
        return false;
    }

    @Override
    public void cleanup() {}
}
