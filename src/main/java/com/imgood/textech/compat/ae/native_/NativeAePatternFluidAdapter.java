package com.imgood.textech.compat.ae.native_;

import com.imgood.textech.compat.ae.AePatternFluidAdapter;
import com.imgood.textech.compat.ae.legacy.LegacyAePatternFluidAdapter;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;

/**
 * GTNH 2.9.0 beta-1+ pattern fluid I/O. Tries {@link ICraftingPatternDetails} interface methods first,
 * then falls back to legacy reflection for mixed networks.
 */
public final class NativeAePatternFluidAdapter implements AePatternFluidAdapter {

    public static final NativeAePatternFluidAdapter INSTANCE = new NativeAePatternFluidAdapter();

    private NativeAePatternFluidAdapter() {}

    @Override
    public boolean hasFluidInputs(ICraftingPatternDetails pattern) {
        return appendOrProbe(pattern, null, true, true);
    }

    @Override
    public boolean hasFluidOutputs(ICraftingPatternDetails pattern) {
        return appendOrProbe(pattern, null, false, true);
    }

    @Override
    public void appendFluidInputs(StringBuilder builder, ICraftingPatternDetails pattern) {
        appendOrProbe(pattern, builder, true, false);
    }

    @Override
    public void appendFluidOutputs(StringBuilder builder, ICraftingPatternDetails pattern) {
        appendOrProbe(pattern, builder, false, false);
    }

    private static boolean appendOrProbe(ICraftingPatternDetails pattern, StringBuilder builder, boolean inputs,
        boolean probeOnly) {
        if (pattern == null) {
            return false;
        }
        IAEFluidStack[] stacks = inputs ? tryGetFluidArray(pattern, true) : tryGetFluidArray(pattern, false);
        if (stacks != null) {
            if (probeOnly) {
                for (IAEFluidStack stack : stacks) {
                    if (stack != null && stack.getStackSize() > 0) {
                        return true;
                    }
                }
                return false;
            }
            for (IAEFluidStack stack : stacks) {
                if (stack != null && stack.getStackSize() > 0) {
                    LegacyAePatternFluidAdapter.appendFluidValue(builder, stack);
                }
            }
            return stacks.length > 0;
        }
        if (probeOnly) {
            return inputs ? LegacyAePatternFluidAdapter.INSTANCE.hasFluidInputs(pattern)
                : LegacyAePatternFluidAdapter.INSTANCE.hasFluidOutputs(pattern);
        }
        if (inputs) {
            LegacyAePatternFluidAdapter.INSTANCE.appendFluidInputs(builder, pattern);
        } else {
            LegacyAePatternFluidAdapter.INSTANCE.appendFluidOutputs(builder, pattern);
        }
        return inputs ? LegacyAePatternFluidAdapter.INSTANCE.hasFluidInputs(pattern)
            : LegacyAePatternFluidAdapter.INSTANCE.hasFluidOutputs(pattern);
    }

    private static IAEFluidStack[] tryGetFluidArray(ICraftingPatternDetails pattern, boolean inputs) {
        String[] methodNames = inputs ? new String[] { "getCondensedFluidInputs", "getFluidInputs" }
            : new String[] { "getCondensedFluidOutputs", "getFluidOutputs" };
        for (String methodName : methodNames) {
            Object value = invokeNoArg(pattern, methodName);
            if (value instanceof IAEFluidStack[]) {
                return (IAEFluidStack[]) value;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            java.lang.reflect.Method method = target.getClass()
                .getMethod(methodName);
            if (method.getParameterTypes().length == 0) {
                return method.invoke(target);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
