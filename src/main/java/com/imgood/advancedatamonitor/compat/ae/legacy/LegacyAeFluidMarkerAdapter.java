package com.imgood.advancedatamonitor.compat.ae.legacy;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.item.ItemFluidPacket;
import com.glodblock.github.util.Util;
import com.imgood.advancedatamonitor.compat.ae.AeFluidMarkerAdapter;

/** AE2FC / GlodBlock Cell Workbench fluid marker resolution. */
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

        if (markerItem.getItem() instanceof ItemFluidPacket) {
            FluidStack packetFluid = ItemFluidPacket.getFluidStack(markerItem);
            if (packetFluid != null && packetFluid.getFluid() != null) {
                return normalizeMarkerFluid(packetFluid);
            }
        }

        FluidStack fluid = Util.getFluidFromVirtual(markerItem);
        if (fluid != null && fluid.getFluid() != null) {
            return normalizeMarkerFluid(fluid);
        }

        fluid = Util.getFluidFromItem(markerItem);
        if (fluid != null && fluid.getFluid() != null) {
            return normalizeMarkerFluid(fluid);
        }

        try {
            appeng.api.storage.data.IAEFluidStack aeFluid = Util.loadFluidStackFromNBT(markerItem.getTagCompound());
            if (aeFluid != null && aeFluid.getFluid() != null) {
                return normalizeMarkerFluid(aeFluid.getFluidStack());
            }
        } catch (Throwable ignored) {
            // AE2FC util optional path
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
}
