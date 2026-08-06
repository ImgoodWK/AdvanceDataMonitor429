package com.imgood.textech.monitor;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;

public class MonitorThresholdRuntimeTest {

    @Test
    public void aggregatesStrongWithOrAndWeakWithMaximum() {
        MonitorThresholdRuntime runtime = new MonitorThresholdRuntime();
        Map<Integer, NBTTagCompound> bindings = new HashMap<Integer, NBTTagCompound>();
        bindings.put(Integer.valueOf(0), binding("gte", 90.0D, 0.0D, 100.0D, 20));
        bindings.put(Integer.valueOf(1), binding("lte", 10.0D, 0.0D, 200.0D, 20));

        runtime.recordSample(0, 50.0D);
        runtime.recordSample(1, 150.0D);
        MonitorThresholdRuntime.Output first = runtime.evaluate(bindings);
        Assert.assertFalse(first.getStrongPower() > 0);
        Assert.assertEquals(11, first.getWeakPower());
        Assert.assertTrue(first.isChanged());

        runtime.recordSample(0, 100.0D);
        MonitorThresholdRuntime.Output second = runtime.evaluate(bindings);
        Assert.assertEquals(15, second.getStrongPower());
        Assert.assertEquals(15, second.getWeakPower());
        Assert.assertTrue(second.isChanged());
        Assert.assertFalse(
            runtime.evaluate(bindings)
                .isChanged());
    }

    @Test
    public void expiresAtMaxOfTwiceIntervalAndTenSeconds() {
        MonitorThresholdRuntime runtime = new MonitorThresholdRuntime();
        Map<Integer, NBTTagCompound> bindings = new HashMap<Integer, NBTTagCompound>();
        bindings.put(Integer.valueOf(0), binding("gte", 1.0D, 0.0D, 0.0D, 20));
        runtime.recordSample(0, 2.0D);
        Assert.assertEquals(
            15,
            runtime.evaluate(bindings)
                .getStrongPower());

        for (int i = 0; i < 200; i++) runtime.advanceTick();
        Assert.assertEquals(
            15,
            runtime.evaluate(bindings)
                .getStrongPower());
        runtime.advanceTick();
        MonitorThresholdRuntime.Output stale = runtime.evaluate(bindings);
        Assert.assertEquals(0, stale.getStrongPower());
        Assert.assertEquals(0, stale.getWeakPower());
        Assert.assertTrue(stale.isChanged());
    }

    @Test
    public void longerIntervalsExtendFreshnessAndInvalidSamplesClearOutput() {
        MonitorThresholdRuntime runtime = new MonitorThresholdRuntime();
        Map<Integer, NBTTagCompound> bindings = new HashMap<Integer, NBTTagCompound>();
        bindings.put(Integer.valueOf(0), binding("gte", 1.0D, 0.0D, 0.0D, 150));
        runtime.recordSample(0, 2.0D);
        runtime.evaluate(bindings);
        for (int i = 0; i < 300; i++) runtime.advanceTick();
        Assert.assertEquals(
            15,
            runtime.evaluate(bindings)
                .getStrongPower());
        runtime.advanceTick();
        Assert.assertEquals(
            0,
            runtime.evaluate(bindings)
                .getStrongPower());

        runtime.recordSample(0, 2.0D);
        Assert.assertEquals(
            15,
            runtime.evaluate(bindings)
                .getStrongPower());
        runtime.recordSample(0, Double.NaN);
        Assert.assertEquals(
            0,
            runtime.evaluate(bindings)
                .getStrongPower());
    }

    @Test
    public void resetModelsTileReloadBeforeFirstSample() {
        MonitorThresholdRuntime runtime = new MonitorThresholdRuntime();
        Map<Integer, NBTTagCompound> bindings = new HashMap<Integer, NBTTagCompound>();
        bindings.put(Integer.valueOf(0), binding("gte", 1.0D, 0.0D, 0.0D, 20));
        runtime.recordSample(0, 2.0D);
        runtime.evaluate(bindings);
        Assert.assertEquals(15, runtime.getStrongPower());
        runtime.reset();
        Assert.assertEquals(
            0,
            runtime.evaluate(bindings)
                .getStrongPower());
        Assert.assertEquals(0, runtime.getWeakPower());
    }

    @Test
    public void disabledBindingAndChangedThresholdDoNotReusePriorActiveState() {
        MonitorThresholdRuntime runtime = new MonitorThresholdRuntime();
        Map<Integer, NBTTagCompound> bindings = new HashMap<Integer, NBTTagCompound>();
        NBTTagCompound binding = binding("gte", 10.0D, 0.0D, 0.0D, 20);
        binding.getCompoundTag(MonitorWidgetSpec.THRESHOLD_KEY)
            .setDouble("hysteresis", 2.0D);
        bindings.put(Integer.valueOf(0), binding);

        runtime.recordSample(0, 10.0D);
        Assert.assertEquals(
            15,
            runtime.evaluate(bindings)
                .getStrongPower());

        binding.getCompoundTag(MonitorWidgetSpec.THRESHOLD_KEY)
            .setDouble("value", 11.0D);
        runtime.resetBindingState(0);
        Assert.assertEquals(
            0,
            runtime.evaluate(bindings)
                .getStrongPower());

        binding.setBoolean("enable", false);
        MonitorThresholdRuntime.Output disabled = runtime.evaluate(bindings);
        Assert.assertEquals(0, disabled.getStrongPower());
        Assert.assertEquals(0, disabled.getWeakPower());
    }

    private static NBTTagCompound binding(String operator, double thresholdValue, double outputMin, double outputMax,
        int interval) {
        NBTTagCompound binding = new NBTTagCompound();
        binding.setBoolean("enable", true);
        binding.setInteger("interval", interval);
        NBTTagCompound threshold = new NBTTagCompound();
        threshold.setBoolean("enabled", true);
        threshold.setString("operator", operator);
        threshold.setDouble("value", thresholdValue);
        threshold.setDouble("hysteresis", 0.0D);
        threshold.setDouble("outputMin", outputMin);
        threshold.setDouble("outputMax", outputMax);
        binding.setTag(MonitorWidgetSpec.THRESHOLD_KEY, threshold);
        return binding;
    }
}
