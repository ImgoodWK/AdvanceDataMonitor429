package com.imgood.advancedatamonitor.compat.ae.legacy;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import net.minecraftforge.fluids.FluidStack;

import com.imgood.advancedatamonitor.compat.ae.AePatternFluidAdapter;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;

/** Reflection-based fluid pattern I/O for AE2FC-extended {@link ICraftingPatternDetails}. */
public final class LegacyAePatternFluidAdapter implements AePatternFluidAdapter {

    public static final LegacyAePatternFluidAdapter INSTANCE = new LegacyAePatternFluidAdapter();

    private LegacyAePatternFluidAdapter() {}

    @Override
    public boolean hasFluidInputs(ICraftingPatternDetails pattern) {
        return probeFluid(pattern, "getCondensedFluidInputs", "getFluidInputs");
    }

    @Override
    public boolean hasFluidOutputs(ICraftingPatternDetails pattern) {
        return probeFluid(pattern, "getCondensedFluidOutputs", "getFluidOutputs");
    }

    @Override
    public void appendFluidInputs(StringBuilder builder, ICraftingPatternDetails pattern) {
        appendFluidLike(builder, pattern, "getCondensedFluidInputs", "getFluidInputs");
    }

    @Override
    public void appendFluidOutputs(StringBuilder builder, ICraftingPatternDetails pattern) {
        appendFluidLike(builder, pattern, "getCondensedFluidOutputs", "getFluidOutputs");
    }

    private static boolean probeFluid(ICraftingPatternDetails pattern, String... methodNames) {
        if (pattern == null) {
            return false;
        }
        StringBuilder section = new StringBuilder();
        for (String methodName : methodNames) {
            Object value = invokeNoArg(pattern, methodName);
            if (value == null) {
                continue;
            }
            int before = section.length();
            appendFluidValue(section, value);
            if (section.length() > before) {
                return true;
            }
        }
        return false;
    }

    private static void appendFluidLike(StringBuilder builder, ICraftingPatternDetails pattern, String... methodNames) {
        if (pattern == null) {
            return;
        }
        for (String methodName : methodNames) {
            Object value = invokeNoArg(pattern, methodName);
            if (value == null) {
                continue;
            }
            int before = builder.length();
            appendFluidValue(builder, value);
            if (builder.length() > before) {
                return;
            }
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass()
                .getMethod(methodName);
            if (method.getParameterTypes().length == 0) {
                return method.invoke(target);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void appendFluidValue(StringBuilder section, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof IAEFluidStack) {
            appendAeFluid(section, (IAEFluidStack) value);
            return;
        }
        if (value instanceof FluidStack) {
            appendFluid(section, (FluidStack) value, ((FluidStack) value).amount);
            return;
        }
        if (value.getClass()
            .isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                appendFluidValue(section, Array.get(value, i));
            }
            return;
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                appendFluidValue(section, item);
            }
        }
    }

    private static void appendAeFluid(StringBuilder section, IAEFluidStack stack) {
        if (stack == null || stack.getStackSize() <= 0) {
            return;
        }
        appendFluid(section, stack.getFluidStack(), stack.getStackSize());
    }

    private static void appendFluid(StringBuilder section, FluidStack stack, long amount) {
        if (stack == null || stack.getFluid() == null || amount <= 0) {
            return;
        }
        section.append("\n- ")
            .append(stack.getLocalizedName())
            .append(" ")
            .append(amount)
            .append(" mB");
    }
}
