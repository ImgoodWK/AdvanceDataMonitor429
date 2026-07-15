package com.imgood.textech.webae.quest;

import java.lang.reflect.Method;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.textech.compat.ae.legacy.LegacyAeFluidMarkerAdapter;

/**
 * Resolves a display-only FluidRegistry name for quest / node icons.
 * <p>
 * Covers GTNH representations that {@link LegacyAeFluidMarkerAdapter} alone misses:
 * IC2 filled cells (via FCR), and {@code gregtech:gt.GregTech_FluidDisplay} whose damage is a fluid ID.
 * Callers must set {@code iconItemId = fluid:<name>} only — never write {@code fluidName} on item-submit tasks.
 */
public final class QuestFluidIconResolver {

    private static volatile Method gtUtilityGetFluid;
    private static volatile boolean gtUtilityResolved;

    private QuestFluidIconResolver() {}

    /**
     * @return FluidRegistry name, or {@code null} if the stack is not a known fluid representation
     */
    public static String resolveFluidName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        try {
            String name = fluidNameOf(LegacyAeFluidMarkerAdapter.INSTANCE.resolveMarkerFluid(stack));
            if (name != null) {
                return name;
            }

            name = fluidNameOf(FluidContainerRegistry.getFluidForFilledItem(stack));
            if (name != null) {
                return name;
            }

            name = resolveGtFluidDisplay(stack);
            if (name != null) {
                return name;
            }

            return resolveGtUtility(stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveGtFluidDisplay(ItemStack stack) {
        Object nameObj = Item.itemRegistry.getNameForObject(stack.getItem());
        if (nameObj == null) {
            return null;
        }
        String registry = nameObj.toString();
        if (registry.indexOf("GregTech_FluidDisplay") < 0) {
            return null;
        }
        Fluid fluid = FluidRegistry.getFluid(stack.getItemDamage());
        if (fluid == null) {
            return null;
        }
        return fluidNameOf(new FluidStack(fluid, 1000));
    }

    private static String resolveGtUtility(ItemStack stack) {
        Method method = gtUtilityMethod();
        if (method == null) {
            return null;
        }
        try {
            Object result = method.invoke(null, stack, Boolean.TRUE);
            if (result instanceof FluidStack) {
                return fluidNameOf((FluidStack) result);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Method gtUtilityMethod() {
        if (gtUtilityResolved) {
            return gtUtilityGetFluid;
        }
        synchronized (QuestFluidIconResolver.class) {
            if (gtUtilityResolved) {
                return gtUtilityGetFluid;
            }
            try {
                Class<?> util = Class.forName("gregtech.api.util.GTUtility");
                gtUtilityGetFluid = util.getMethod("getFluidForFilledItem", ItemStack.class, boolean.class);
            } catch (Throwable ignored) {
                gtUtilityGetFluid = null;
            }
            gtUtilityResolved = true;
            return gtUtilityGetFluid;
        }
    }

    private static String fluidNameOf(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) {
            return null;
        }
        String name = FluidRegistry.getFluidName(fluid.getFluid());
        if (name == null || name.isEmpty()) {
            name = fluid.getFluid()
                .getName();
        }
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name;
    }
}
