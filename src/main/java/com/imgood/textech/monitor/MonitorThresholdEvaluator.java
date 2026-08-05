package com.imgood.textech.monitor;

/** Pure threshold, hysteresis, and analog-output evaluation for monitor bindings. */
public final class MonitorThresholdEvaluator {

    public static final String OPERATOR_GTE = "gte";
    public static final String OPERATOR_LTE = "lte";

    private MonitorThresholdEvaluator() {}

    public static Result evaluate(boolean enabled, String operator, double thresholdValue, double hysteresis,
        double outputMin, double outputMax, boolean previouslyActive, double sampleValue, boolean fresh) {
        if (!enabled || !fresh || !isFinite(sampleValue) || !isFinite(thresholdValue)) {
            return Result.OFF;
        }

        double normalizedHysteresis = isFinite(hysteresis) ? Math.max(0.0D, hysteresis) : 0.0D;
        boolean active;
        if (OPERATOR_LTE.equals(operator)) {
            active = previouslyActive ? sampleValue <= thresholdValue + normalizedHysteresis
                : sampleValue <= thresholdValue;
        } else {
            active = previouslyActive ? sampleValue >= thresholdValue - normalizedHysteresis
                : sampleValue >= thresholdValue;
        }

        int weakPower;
        if (isFinite(outputMin) && isFinite(outputMax) && outputMax > outputMin) {
            double scaled = (sampleValue - outputMin) * 15.0D / (outputMax - outputMin);
            weakPower = clampPower((int) Math.round(scaled));
        } else {
            weakPower = active ? 15 : 0;
        }
        return new Result(active, weakPower, active ? 15 : 0);
    }

    private static int clampPower(int power) {
        return Math.max(0, Math.min(15, power));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class Result {

        private static final Result OFF = new Result(false, 0, 0);

        private final boolean active;
        private final int weakPower;
        private final int strongPower;

        private Result(boolean active, int weakPower, int strongPower) {
            this.active = active;
            this.weakPower = weakPower;
            this.strongPower = strongPower;
        }

        public boolean isActive() {
            return active;
        }

        public int getWeakPower() {
            return weakPower;
        }

        public int getStrongPower() {
            return strongPower;
        }
    }
}
