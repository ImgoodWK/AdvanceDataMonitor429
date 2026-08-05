package com.imgood.textech.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class MonitorDownsampleUtilTest {

    @Test
    public void boundsPointsByHistoryWidthAndHardBudget() {
        Assert.assertEquals(0, MonitorDownsampleUtil.pointBudget(10, 0));
        Assert.assertEquals(10, MonitorDownsampleUtil.pointBudget(10, 100));
        Assert.assertEquals(40, MonitorDownsampleUtil.pointBudget(100, 40));
        Assert.assertEquals(240, MonitorDownsampleUtil.pointBudget(1000, 1000));
    }

    @Test
    public void preservesFirstAndLastPoint() {
        List<Double> values = new ArrayList<Double>();
        for (int i = 0; i < 1000; i++) values.add(Double.valueOf(i));
        List<Double> sampled = MonitorDownsampleUtil.downsample(values, 32);
        Assert.assertEquals(32, sampled.size());
        Assert.assertEquals(Double.valueOf(0.0D), sampled.get(0));
        Assert.assertEquals(Double.valueOf(999.0D), sampled.get(sampled.size() - 1));
    }

    @Test
    public void computesDifferenceTransform() {
        Assert.assertEquals(
            Arrays.asList(Double.valueOf(3.0D), Double.valueOf(-2.0D), Double.valueOf(5.0D)),
            MonitorDownsampleUtil.difference(Arrays.asList(1.0D, 4.0D, 2.0D, 7.0D)));
    }
}
