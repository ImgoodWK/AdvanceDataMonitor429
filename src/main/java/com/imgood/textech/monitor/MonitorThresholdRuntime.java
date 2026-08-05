package com.imgood.textech.monitor;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Ephemeral threshold state for one data monitor.
 *
 * <p>
 * Samples, hysteresis state, and aggregate outputs intentionally never enter tile NBT. A reload therefore starts
 * with zero output until the first valid server-side sample.
 * </p>
 */
public final class MonitorThresholdRuntime {

    public static final long MIN_FRESH_TICKS = 200L;

    private static final class Sample {

        private final double value;
        private final long sampledAtTick;

        private Sample(double value, long sampledAtTick) {
            this.value = value;
            this.sampledAtTick = sampledAtTick;
        }
    }

    private final Map<Integer, Sample> samples = new HashMap<Integer, Sample>();
    private final Map<Integer, Boolean> activeBindings = new HashMap<Integer, Boolean>();
    private long currentTick;
    private int weakPower;
    private int strongPower;

    public void advanceTick() {
        currentTick++;
    }

    public void recordSample(int bindingIndex, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            samples.remove(Integer.valueOf(bindingIndex));
            activeBindings.remove(Integer.valueOf(bindingIndex));
            return;
        }
        samples.put(Integer.valueOf(bindingIndex), new Sample(value, currentTick));
    }

    public void resetBindingState(int bindingIndex) {
        activeBindings.remove(Integer.valueOf(bindingIndex));
    }

    public void removeBinding(int bindingIndex) {
        samples.remove(Integer.valueOf(bindingIndex));
        activeBindings.remove(Integer.valueOf(bindingIndex));
    }

    public void reset() {
        samples.clear();
        activeBindings.clear();
        currentTick = 0L;
        weakPower = 0;
        strongPower = 0;
    }

    public Output evaluate(Map<Integer, NBTTagCompound> bindings) {
        int nextWeakPower = 0;
        int nextStrongPower = 0;
        if (bindings != null) {
            for (Map.Entry<Integer, NBTTagCompound> entry : bindings.entrySet()) {
                Integer index = entry.getKey();
                NBTTagCompound binding = entry.getValue();
                if (index == null || binding == null) {
                    continue;
                }
                MonitorWidgetSpec.normalizeThreshold(binding);
                NBTTagCompound threshold = binding.getCompoundTag(MonitorWidgetSpec.THRESHOLD_KEY);
                Sample sample = samples.get(index);
                int intervalTicks = binding.hasKey("interval") ? Math.max(1, binding.getInteger("interval")) : 20;
                long freshnessTicks = Math.max(MIN_FRESH_TICKS, 2L * intervalTicks);
                boolean fresh = sample != null && currentTick >= sample.sampledAtTick
                    && currentTick - sample.sampledAtTick <= freshnessTicks;
                boolean bindingEnabled = !binding.hasKey("enable") || binding.getBoolean("enable");
                boolean wasActive = Boolean.TRUE.equals(activeBindings.get(index));
                MonitorThresholdEvaluator.Result result = MonitorThresholdEvaluator.evaluate(
                    bindingEnabled && threshold.getBoolean("enabled"),
                    threshold.getString("operator"),
                    threshold.getDouble("value"),
                    threshold.getDouble("hysteresis"),
                    threshold.getDouble("outputMin"),
                    threshold.getDouble("outputMax"),
                    wasActive,
                    sample == null ? Double.NaN : sample.value,
                    fresh);
                if (result.isActive()) {
                    activeBindings.put(index, Boolean.TRUE);
                } else {
                    activeBindings.remove(index);
                }
                nextWeakPower = Math.max(nextWeakPower, result.getWeakPower());
                nextStrongPower = Math.max(nextStrongPower, result.getStrongPower());
            }
        }

        boolean changed = nextWeakPower != weakPower || nextStrongPower != strongPower;
        weakPower = nextWeakPower;
        strongPower = nextStrongPower;
        return new Output(weakPower, strongPower, changed);
    }

    public int getWeakPower() {
        return weakPower;
    }

    public int getStrongPower() {
        return strongPower;
    }

    public static final class Output {

        private final int weakPower;
        private final int strongPower;
        private final boolean changed;

        private Output(int weakPower, int strongPower, boolean changed) {
            this.weakPower = weakPower;
            this.strongPower = strongPower;
            this.changed = changed;
        }

        public int getWeakPower() {
            return weakPower;
        }

        public int getStrongPower() {
            return strongPower;
        }

        public boolean isChanged() {
            return changed;
        }
    }
}
