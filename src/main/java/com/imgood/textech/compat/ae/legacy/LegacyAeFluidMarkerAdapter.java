package com.imgood.textech.compat.ae.legacy;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.imgood.textech.compat.ae.AeFluidMarkerAdapter;

/**
 * Fluid marker resolution without compile-time ae2fc types (GTNH 2.9.0-beta-2+).
 * Uses NBT + Forge fluid containers; optional reflective hooks for remaining ae2fc helpers.
 */
public final class LegacyAeFluidMarkerAdapter implements AeFluidMarkerAdapter {

    public static final LegacyAeFluidMarkerAdapter INSTANCE = new LegacyAeFluidMarkerAdapter();

    private LegacyAeFluidMarkerAdapter() {}

    @Override
    public FluidStack resolveMarkerFluid(ItemStack markerItem) {
        if (markerItem == null || markerItem.getItem() == null) {
            return null;
        }

        FluidStack fromNbt = resolveMarkerFluidFromNbt(markerItem);
        if (fromNbt != null) {
            return fromNbt;
        }

        FluidStack fromPacket = resolveViaAe2fcPacketReflect(markerItem);
        if (fromPacket != null) {
            return fromPacket;
        }

        FluidStack fromVirtual = resolveViaAe2fcUtilReflect(markerItem, "getFluidFromVirtual");
        if (fromVirtual != null) {
            return fromVirtual;
        }

        FluidStack fromItem = resolveViaAe2fcUtilReflect(markerItem, "getFluidFromItem");
        if (fromItem != null) {
            return fromItem;
        }

        if (markerItem.getItem() instanceof IFluidContainerItem) {
            FluidStack drained = ((IFluidContainerItem) markerItem.getItem()).getFluid(markerItem);
            if (drained != null && drained.getFluid() != null) {
                return normalizeMarkerFluid(drained);
            }
        }

        FluidStack container = FluidContainerRegistry.getFluidForFilledItem(markerItem);
        if (container != null && container.getFluid() != null) {
            return normalizeMarkerFluid(container);
        }

        return null;
    }

    public static FluidStack resolveMarkerFluidFromNbt(ItemStack markerItem) {
        if (!markerItem.hasTagCompound()) {
            return null;
        }
        NBTTagCompound tag = markerItem.getTagCompound();
        try {
            if (tag.hasKey("FluidStack", 10)) {
                FluidStack nested = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("FluidStack"));
                if (nested != null && nested.getFluid() != null) {
                    return normalizeMarkerFluid(nested);
                }
            }
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag);
            if (fluid != null && fluid.getFluid() != null) {
                return normalizeMarkerFluid(fluid);
            }
        } catch (Throwable ignored) {
            // ignore malformed ghost stacks
        }
        return null;
    }

    public static FluidStack normalizeMarkerFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) {
            return null;
        }
        FluidStack copy = fluid.copy();
        if (copy.amount <= 0) {
            copy.amount = 1000;
        }
        return copy;
    }

    private static FluidStack resolveViaAe2fcPacketReflect(ItemStack markerItem) {
        try {
            Class<?> packetClass = Class.forName("com.glodblock.github.common.item.ItemFluidPacket");
            if (!packetClass.isInstance(markerItem.getItem())) {
                return null;
            }
            Object fluid = packetClass.getMethod("getFluidStack", ItemStack.class)
                .invoke(null, markerItem);
            if (fluid instanceof FluidStack) {
                return normalizeMarkerFluid((FluidStack) fluid);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static FluidStack resolveViaAe2fcUtilReflect(ItemStack markerItem, String methodName) {
        try {
            Class<?> utilClass = Class.forName("com.glodblock.github.util.Util");
            Object fluid = utilClass.getMethod(methodName, ItemStack.class)
                .invoke(null, markerItem);
            if (fluid instanceof FluidStack) {
                return normalizeMarkerFluid((FluidStack) fluid);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
