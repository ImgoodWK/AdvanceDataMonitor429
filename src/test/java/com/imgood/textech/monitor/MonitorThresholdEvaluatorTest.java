package com.imgood.textech.monitor;

import org.junit.Assert;
import org.junit.Test;

public class MonitorThresholdEvaluatorTest {

    @Test
    public void gteHysteresisUsesStrictDeactivationBoundary() {
        assertState(false, 0, evaluate("gte", false, 9.99D, 10.0D, 2.0D));
        assertState(true, 15, evaluate("gte", false, 10.0D, 10.0D, 2.0D));
        assertState(true, 15, evaluate("gte", true, 8.0D, 10.0D, 2.0D));
        assertState(false, 0, evaluate("gte", true, 7.99D, 10.0D, 2.0D));
    }

    @Test
    public void lteHysteresisUsesStrictDeactivationBoundary() {
        assertState(false, 0, evaluate("lte", false, 10.01D, 10.0D, 2.0D));
        assertState(true, 15, evaluate("lte", false, 10.0D, 10.0D, 2.0D));
        assertState(true, 15, evaluate("lte", true, 12.0D, 10.0D, 2.0D));
        assertState(false, 0, evaluate("lte", true, 12.01D, 10.0D, 2.0D));
    }

    @Test
    public void validRangeMapsAndClampsIndependentlyOfBooleanState() {
        MonitorThresholdEvaluator.Result below = MonitorThresholdEvaluator
            .evaluate(true, "gte", 100.0D, 0.0D, 0.0D, 100.0D, false, 50.0D, true);
        Assert.assertFalse(below.isActive());
        Assert.assertEquals(8, below.getWeakPower());
        Assert.assertEquals(0, below.getStrongPower());

        Assert.assertEquals(
            0,
            MonitorThresholdEvaluator.evaluate(true, "gte", 0.0D, 0.0D, 0.0D, 100.0D, false, -1.0D, true)
                .getWeakPower());
        Assert.assertEquals(
            15,
            MonitorThresholdEvaluator.evaluate(true, "gte", 0.0D, 0.0D, 0.0D, 100.0D, false, 101.0D, true)
                .getWeakPower());
    }

    @Test
    public void invalidRangeFallsBackToBinaryOutput() {
        MonitorThresholdEvaluator.Result inactive = MonitorThresholdEvaluator
            .evaluate(true, "gte", 10.0D, 0.0D, 4.0D, 4.0D, false, 9.0D, true);
        assertState(false, 0, inactive);
        MonitorThresholdEvaluator.Result active = MonitorThresholdEvaluator
            .evaluate(true, "gte", 10.0D, 0.0D, 4.0D, 3.0D, false, 10.0D, true);
        assertState(true, 15, active);
    }

    @Test
    public void disabledStaleAndNonFiniteSamplesAreAlwaysOff() {
        assertOff(MonitorThresholdEvaluator.evaluate(false, "gte", 1, 0, 0, 10, true, 5, true));
        assertOff(MonitorThresholdEvaluator.evaluate(true, "gte", 1, 0, 0, 10, true, 5, false));
        assertOff(MonitorThresholdEvaluator.evaluate(true, "gte", 1, 0, 0, 10, true, Double.NaN, true));
        assertOff(
            MonitorThresholdEvaluator.evaluate(true, "gte", 1, 0, 0, 10, true, Double.POSITIVE_INFINITY, true));
    }

    private static MonitorThresholdEvaluator.Result evaluate(String operator, boolean active, double sample,
        double threshold, double hysteresis) {
        return MonitorThresholdEvaluator
            .evaluate(true, operator, threshold, hysteresis, 0.0D, 0.0D, active, sample, true);
    }

    private static void assertOff(MonitorThresholdEvaluator.Result result) {
        assertState(false, 0, result);
        Assert.assertEquals(0, result.getWeakPower());
    }

    private static void assertState(boolean active, int strongPower, MonitorThresholdEvaluator.Result result) {
        Assert.assertEquals(active, result.isActive());
        Assert.assertEquals(strongPower, result.getStrongPower());
    }
}
