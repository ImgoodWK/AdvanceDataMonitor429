package com.imgood.textech.handler;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;

/**
 * Keeps {@link StorageLinkWatchRegistry} aligned with data monitor storage bindings.
 */
public final class StorageLinkWatchSync {

    private StorageLinkWatchSync() {}

    public static void onBindingChanged(World world, NBTTagCompound oldBinding, NBTTagCompound newBinding) {
        releaseIfStorageLink(world, oldBinding);
        acquireIfStorageLink(world, newBinding);
    }

    public static void releaseIfStorageLink(World world, NBTTagCompound binding) {
        int[] pos = resolveStorageLinkPos(world, binding);
        if (pos == null) {
            return;
        }
        StorageLinkWatchRegistry.release(pos[0], pos[1], pos[2], pos[3]);
    }

    public static void acquireIfStorageLink(World world, NBTTagCompound binding) {
        int[] pos = resolveStorageLinkPos(world, binding);
        if (pos == null) {
            return;
        }
        StorageLinkWatchRegistry.acquire(pos[0], pos[1], pos[2], pos[3]);
    }

    private static int[] resolveStorageLinkPos(World world, NBTTagCompound binding) {
        if (world == null || binding == null || !binding.hasKey("XYZ")) {
            return null;
        }
        String dataType = binding.hasKey("dataType") ? binding.getString("dataType") : "";
        if (!"storage".equals(dataType)) {
            return null;
        }
        String xyz = binding.getString("XYZ");
        if (xyz == null || xyz.isEmpty()) {
            return null;
        }
        String[] parts = xyz.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            if (!world.blockExists(x, y, z)) {
                return null;
            }
            TileEntity target = world.getTileEntity(x, y, z);
            if (!(target instanceof TileEntityAdvanceNetworkLink)) {
                return null;
            }
            return new int[] { world.provider.dimensionId, x, y, z };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
