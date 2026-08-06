package com.imgood.textech.renders;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.monitor.MonitorWidgetSpec;

public class MonitorRenderSupportTest {

    @Test
    public void wirelessSteamNeedsARealCapacityOrTarget() {
        NBTTagCompound binding = new NBTTagCompound();
        binding.setString("sourceKind", MonitorWidgetSpec.SOURCE_WIRELESS_STEAM);
        binding.setString("metricKey", "steamPercent");

        Assert.assertEquals(0.0D, MonitorRenderSupport.progressMax(binding, 500.0D), 0.0D);

        binding.setDouble("targetValue", 1000.0D);
        Assert.assertEquals(1000.0D, MonitorRenderSupport.progressMax(binding, 500.0D), 0.0D);
    }

    @Test
    public void realCapacityPrecedesTargetAndOrdinaryPercentUsesOneHundred() {
        NBTTagCompound capacityBinding = new NBTTagCompound();
        capacityBinding.setBoolean("capacityKnown", true);
        capacityBinding.setDouble("capacity", 2000.0D);
        capacityBinding.setDouble("targetValue", 1000.0D);
        Assert.assertEquals(2000.0D, MonitorRenderSupport.progressMax(capacityBinding, 500.0D), 0.0D);

        NBTTagCompound percentBinding = new NBTTagCompound();
        percentBinding.setString("metricKey", "cpuUsagePercent");
        Assert.assertEquals(100.0D, MonitorRenderSupport.progressMax(percentBinding, 42.0D), 0.0D);
    }
}
