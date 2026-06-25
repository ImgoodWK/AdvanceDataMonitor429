package com.imgood.advancedatamonitor.compat.ae.native_;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.advancedatamonitor.compat.ae.AeFluidMarkerAdapter;
import com.imgood.advancedatamonitor.compat.ae.legacy.LegacyAeFluidMarkerAdapter;

/**
 * GTNH 2.9.0 beta-1+ fluid marker resolution. NBT ghosts are shared; AE2-native util is tried before AE2FC fallback.
 */
public final class NativeAeFluidMarkerAdapter implements AeFluidMarkerAdapter {

    public static final NativeAeFluidMarkerAdapter INSTANCE = new NativeAeFluidMarkerAdapter();

    private NativeAeFluidMarkerAdapter() {}

    @Override
    public FluidStack resolveMarkerFluid(ItemStack markerItem) {
        if (markerItem == null || markerItem.getItem() == null) {
            return null;
        }

        FluidStack fromNbt = LegacyAeFluidMarkerAdapter.resolveMarkerFluidFromNbt(markerItem);
        if (fromNbt != null) {
            return fromNbt;
        }

        FluidStack fromAe2 = resolveViaAe2StorageUtil(markerItem);
        if (fromAe2 != null) {
            return fromAe2;
        }

        return LegacyAeFluidMarkerAdapter.INSTANCE.resolveMarkerFluid(markerItem);
    }

    private static FluidStack resolveViaAe2StorageUtil(ItemStack markerItem) {
        if (!markerItem.hasTagCompound()) {
            return null;
        }
        try {
            Class<?> utilClass = Class.forName("appeng.util.Platform");
            Object aeFluid = utilClass.getMethod("loadFluidStackFromNBT", net.minecraft.nbt.NBTTagCompound.class)
                .invoke(null, markerItem.getTagCompound());
            if (aeFluid instanceof appeng.api.storage.data.IAEFluidStack) {
                appeng.api.storage.data.IAEFluidStack stack = (appeng.api.storage.data.IAEFluidStack) aeFluid;
                if (stack.getFluid() != null) {
                    return LegacyAeFluidMarkerAdapter.normalizeMarkerFluid(stack.getFluidStack());
                }
            }
        } catch (Throwable ignored) {
            // AE2 util not present on this build
        }
        return null;
    }
}
