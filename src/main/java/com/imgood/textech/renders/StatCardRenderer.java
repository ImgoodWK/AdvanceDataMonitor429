package com.imgood.textech.renders;

import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.monitor.MonitorWidgetSpec;

/** Native scalar card renderer. */
public class StatCardRenderer implements IADMRender {

    @Override
    public void render(NBTTagCompound nbt, double x, double y, double z, int facing, int bindingIndex) {
        if (!nbt.getBoolean("enable")) return;
        double width = MonitorRenderSupport.width(nbt);
        double height = MonitorRenderSupport.height(nbt);
        MonitorRenderSupport.begin(nbt);
        try {
            MonitorRenderSupport.drawQuad(
                -width / 2.0D,
                MonitorRenderSupport.BASE_Y,
                width / 2.0D,
                MonitorRenderSupport.BASE_Y + height,
                0.0D,
                MonitorRenderSupport.color(nbt, "backgroundColor", 0x07151F),
                0.78F);
            MonitorRenderSupport.drawOutline(
                -width / 2.0D,
                MonitorRenderSupport.BASE_Y,
                width / 2.0D,
                MonitorRenderSupport.BASE_Y + height,
                0.01D,
                MonitorRenderSupport.color(nbt, "lineColor", 0x00FFFF),
                0.9F);
            MonitorRenderSupport.drawTextCentered(
                MonitorWidgetSpec.getTitle(nbt),
                0.0D,
                MonitorRenderSupport.BASE_Y + height * 0.25D,
                0.03D,
                0.016D,
                MonitorRenderSupport.color(nbt, "displayNameColor", 0xFFFFFF));
            MonitorRenderSupport.drawTextCentered(
                MonitorRenderSupport.format(MonitorRenderSupport.latest(nbt)),
                0.0D,
                MonitorRenderSupport.BASE_Y + height * 0.58D,
                0.03D,
                0.028D,
                MonitorRenderSupport.color(nbt, "lineColor", 0x00FFFF));
        } finally {
            MonitorRenderSupport.end();
        }
    }

    @Override
    public void cleanup() {}
}
