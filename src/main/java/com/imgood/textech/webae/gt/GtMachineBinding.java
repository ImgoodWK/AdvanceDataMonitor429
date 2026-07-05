package com.imgood.textech.webae.gt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.BlockPos;

public final class GtMachineBinding {

    private static final String TAG_GT_BOUND_MACHINES = "gtBoundMachines";

    private GtMachineBinding() {}

    public static List<BoundMachine> getBoundMachines(TileEntityAdvanceDataMonitor tile) {
        List<BoundMachine> list = new ArrayList<BoundMachine>();
        NBTTagCompound nbt = tile.getGtBoundMachinesNbt();
        if (nbt == null) return list;

        NBTTagList tagList = nbt.getTagList(TAG_GT_BOUND_MACHINES, 10);
        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound entry = tagList.getCompoundTagAt(i);
            int dim = entry.getInteger("dim");
            int x = entry.getInteger("x");
            int y = entry.getInteger("y");
            int z = entry.getInteger("z");
            list.add(new BoundMachine(dim, x, y, z));
        }
        return list;
    }

    public static void addMachine(TileEntityAdvanceDataMonitor tile, int dim, BlockPos pos) {
        List<BoundMachine> existing = getBoundMachines(tile);
        for (BoundMachine m : existing) {
            if (m.dim == dim && m.x == pos.getX() && m.y == pos.getY() && m.z == pos.getZ()) {
                return; // already bound
            }
        }
        existing.add(new BoundMachine(dim, pos.getX(), pos.getY(), pos.getZ()));
        saveMachines(tile, existing);
    }

    public static void addMachinesBatch(TileEntityAdvanceDataMonitor tile, int dim, List<BlockPos> positions) {
        List<BoundMachine> existing = getBoundMachines(tile);
        for (BlockPos pos : positions) {
            boolean duplicate = false;
            for (BoundMachine m : existing) {
                if (m.dim == dim && m.x == pos.getX() && m.y == pos.getY() && m.z == pos.getZ()) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                existing.add(new BoundMachine(dim, pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        saveMachines(tile, existing);
    }

    public static void removeMachine(TileEntityAdvanceDataMonitor tile, int dim, BlockPos pos) {
        List<BoundMachine> existing = getBoundMachines(tile);
        BoundMachine toRemove = null;
        for (BoundMachine m : existing) {
            if (m.dim == dim && m.x == pos.getX() && m.y == pos.getY() && m.z == pos.getZ()) {
                toRemove = m;
                break;
            }
        }
        if (toRemove != null) {
            existing.remove(toRemove);
            saveMachines(tile, existing);
        }
    }

    public static void clearMachines(TileEntityAdvanceDataMonitor tile) {
        NBTTagCompound nbt = tile.getGtBoundMachinesNbt();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            tile.setGtBoundMachinesNbt(nbt);
        }
        nbt.setTag(TAG_GT_BOUND_MACHINES, new NBTTagList());
        tile.markDirty();
        tile.syncData();
    }

    private static void saveMachines(TileEntityAdvanceDataMonitor tile, List<BoundMachine> machines) {
        NBTTagCompound nbt = tile.getGtBoundMachinesNbt();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            tile.setGtBoundMachinesNbt(nbt);
        }
        NBTTagList tagList = new NBTTagList();
        for (BoundMachine m : machines) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("dim", m.dim);
            entry.setInteger("x", m.x);
            entry.setInteger("y", m.y);
            entry.setInteger("z", m.z);
            tagList.appendTag(entry);
        }
        nbt.setTag(TAG_GT_BOUND_MACHINES, tagList);
        tile.markDirty();
        tile.syncData();
    }

    public static class BoundMachine {

        public final int dim, x, y, z;

        public BoundMachine(int dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
