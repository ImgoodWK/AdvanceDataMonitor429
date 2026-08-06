package com.imgood.textech.monitor;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;

public class MonitorWidgetSpecThresholdTest {

    @Test
    public void legacyBindingReceivesDisabledThresholdWithoutUsingTargetValue() {
        NBTTagCompound binding = new NBTTagCompound();
        binding.setDouble("targetValue", 1234.0D);
        MonitorWidgetSpec.normalizeBinding(binding, 1, 2, 3);

        NBTTagCompound threshold = binding.getCompoundTag(MonitorWidgetSpec.THRESHOLD_KEY);
        Assert.assertFalse(threshold.getBoolean("enabled"));
        Assert.assertEquals(MonitorThresholdEvaluator.OPERATOR_GTE, threshold.getString("operator"));
        Assert.assertEquals(0.0D, threshold.getDouble("value"), 0.0D);
        Assert.assertEquals(1234.0D, MonitorWidgetSpec.getTargetValue(binding), 0.0D);
    }

    @Test
    public void normalizesNegativeHysteresisAndUnknownOperator() {
        NBTTagCompound binding = new NBTTagCompound();
        NBTTagCompound threshold = new NBTTagCompound();
        threshold.setBoolean("enabled", true);
        threshold.setString("operator", "unknown");
        threshold.setDouble("hysteresis", -4.0D);
        binding.setTag(MonitorWidgetSpec.THRESHOLD_KEY, threshold);

        MonitorWidgetSpec.normalizeBinding(binding, 0, 0, 0);
        threshold = binding.getCompoundTag(MonitorWidgetSpec.THRESHOLD_KEY);
        Assert.assertTrue(threshold.getBoolean("enabled"));
        Assert.assertEquals(MonitorThresholdEvaluator.OPERATOR_GTE, threshold.getString("operator"));
        Assert.assertEquals(0.0D, threshold.getDouble("hysteresis"), 0.0D);
    }
}
