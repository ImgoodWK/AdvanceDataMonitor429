package com.imgood.textech.renders;

import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.monitor.MonitorWidgetSpec;

/** Native progress renderer with target/capacity-aware semantics. */
public class ProgressBarRenderer implements IADMRender {

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
        double left = -width / 2.0D;
        double bottom = MonitorRenderSupport.BASE_Y + height * 0.42D;
        double top = bottom + Math.max(0.18D, height * 0.22D);
        MonitorRenderSupport.begin(nbt);
        try {
            MonitorRenderSupport.drawTextCentered(
                MonitorWidgetSpec.getTitle(nbt),
                0.0D,
                MonitorRenderSupport.BASE_Y + height * 0.15D,
                0.03D,
                0.016D,
                MonitorRenderSupport.color(nbt, "displayNameColor", 0xFFFFFF));
            MonitorRenderSupport.drawQuad(left, bottom, width / 2.0D, top, 0.0D, 0x22313B, 0.85F);
            MonitorRenderSupport.drawQuad(
                left,
                bottom,
                left + width * ratio,
                top,
                0.01D,
                MonitorRenderSupport.color(nbt, "lineColor", 0x00FFFF),
                0.95F);
            MonitorRenderSupport.drawOutline(left, bottom, width / 2.0D, top, 0.02D, 0xFFFFFF, 0.65F);
            MonitorRenderSupport.drawTextCentered(
                MonitorRenderSupport.format(value) + " / " + MonitorRenderSupport.format(max),
                0.0D,
                top + height * 0.12D,
                0.03D,
                0.015D,
                0xFFFFFF);
        } finally {
            MonitorRenderSupport.end();
        }
    }

    @Override
    public void cleanup() {}
}
