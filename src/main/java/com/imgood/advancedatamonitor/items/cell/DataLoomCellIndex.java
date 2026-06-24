package com.imgood.advancedatamonitor.items.cell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.storage.ISaveProvider;
import appeng.helpers.IPriorityHost;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

/**
 * Server-side registry of ME hosts that currently hold data loom cells.
 * Maintained on chunk load/unload and by periodic rescan — independent of AE inventory polling.
 */
public final class DataLoomCellIndex {

    public static final DataLoomCellIndex INSTANCE = new DataLoomCellIndex();

    private final Set<DataLoomCellSlot> slots = new HashSet<DataLoomCellSlot>();

    private DataLoomCellIndex() {}

    public void registerDrive(TileDrive drive) {
        if (drive == null || drive.getWorldObj() == null) {
            return;
        }
        int dim = drive.getWorldObj().provider.dimensionId;
        IInventory inv = drive.getInternalInventory();
        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (isLoomCell(stack)) {
                DataLoomCellUtil.ensureInstanceId(stack);
                DataLoomCellSlot ref = new DataLoomCellSlot(
                    dim,
                    drive.xCoord,
                    drive.yCoord,
                    drive.zCoord,
                    slot,
                    DataLoomCellSlot.HostKind.DRIVE);
                if (slots.add(ref) && stack.getItem() instanceof AbstractDataLoomFluidCell) {
                    DataLoomDebugLog.info(
                        "Indexed fluid loom cell {} driveSlot={} markers={}",
                        "dim" + dim + "@" + drive.xCoord + "," + drive.yCoord + "," + drive.zCoord,
                        slot,
                        DataLoomCellUtil.resolveMarkedFluids(stack)
                            .size());
                }
            }
        }
    }

    public void registerChest(TileChest chest) {
        if (chest == null || chest.getWorldObj() == null) {
            return;
        }
        int dim = chest.getWorldObj().provider.dimensionId;
        ItemStack stack = chest.getInternalInventory()
            .getStackInSlot(0);
        if (isLoomCell(stack)) {
            DataLoomCellUtil.ensureInstanceId(stack);
            slots.add(
                new DataLoomCellSlot(
                    dim,
                    chest.xCoord,
                    chest.yCoord,
                    chest.zCoord,
                    0,
                    DataLoomCellSlot.HostKind.CHEST));
        }
    }

    public void removeChunk(int dimensionId, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        Iterator<DataLoomCellSlot> it = slots.iterator();
        while (it.hasNext()) {
            DataLoomCellSlot slot = it.next();
            if (slot.dimensionId == dimensionId && slot.x >= minX
                && slot.x <= maxX
                && slot.z >= minZ
                && slot.z <= maxZ) {
                it.remove();
            }
        }
    }

    public List<DataLoomCellSlot> snapshot() {
        return new ArrayList<DataLoomCellSlot>(slots);
    }

    public void rescanLoadedHosts() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (WorldServer world : server.worldServers) {
            if (world == null) {
                continue;
            }
            for (Object loaded : world.loadedTileEntityList) {
                if (!(loaded instanceof TileEntity)) {
                    continue;
                }
                TileEntity tile = (TileEntity) loaded;
                if (tile instanceof TileDrive) {
                    registerDrive((TileDrive) tile);
                } else if (tile instanceof TileChest) {
                    registerChest((TileChest) tile);
                }
            }
        }
    }

    public ItemStack resolveStack(World world, DataLoomCellSlot slotRef) {
        if (world == null || slotRef == null) {
            return null;
        }
        TileEntity tile = world.getTileEntity(slotRef.x, slotRef.y, slotRef.z);
        if (slotRef.hostKind == DataLoomCellSlot.HostKind.DRIVE) {
            if (!(tile instanceof TileDrive)) {
                return null;
            }
            IInventory inv = ((TileDrive) tile).getInternalInventory();
            if (slotRef.slot < 0 || slotRef.slot >= inv.getSizeInventory()) {
                return null;
            }
            return inv.getStackInSlot(slotRef.slot);
        }
        if (!(tile instanceof TileChest)) {
            return null;
        }
        return ((TileChest) tile).getInternalInventory()
            .getStackInSlot(0);
    }

    public TileEntity resolveHost(World world, DataLoomCellSlot slotRef) {
        if (world == null || slotRef == null) {
            return null;
        }
        return world.getTileEntity(slotRef.x, slotRef.y, slotRef.z);
    }

    /** Writes the mutated cell stack back into the ME host inventory. */
    public void commitStack(World world, DataLoomCellSlot slotRef, ItemStack stack) {
        if (world == null || slotRef == null || stack == null) {
            return;
        }
        TileEntity tile = resolveHost(world, slotRef);
        if (tile == null) {
            return;
        }

        IInventory inv = resolveHostInventory(tile, slotRef);
        if (inv == null) {
            return;
        }

        int slot = slotRef.hostKind == DataLoomCellSlot.HostKind.DRIVE ? slotRef.slot : 0;
        if (slot < 0 || slot >= inv.getSizeInventory()) {
            return;
        }

        ItemStack inSlot = inv.getStackInSlot(slot);
        if (inSlot != null && inSlot == stack) {
            // Weave engine mutates the live drive/chest stack — avoid copy() so AE cell handlers keep the same ref.
        } else if (inSlot != null && stack.hasTagCompound()) {
            inSlot.setTagCompound(
                (NBTTagCompound) stack.getTagCompound()
                    .copy());
            DataLoomCellUtil.ensureInstanceId(inSlot);
        } else {
            ItemStack committed = stack.copy();
            committed.stackSize = stack.stackSize;
            inv.setInventorySlotContents(slot, committed);
        }

        if (tile instanceof ISaveProvider) {
            ((ISaveProvider) tile).saveChanges(null);
        }
        tile.markDirty();
        refreshMeStorageHost(tile);
        if (DataLoomDebugLog.isEnabled() && stack.getItem() instanceof AbstractDataLoomFluidCell) {
            long[] stats = DataLoomCellUtil.readFluidStorageStats(inSlot != null ? inSlot : stack);
            DataLoomDebugLog.info(
                "commitStack fluid cell dim{}@{} {} {} slot={} storedMb={} types={}",
                slotRef.dimensionId,
                slotRef.x,
                slotRef.y,
                slotRef.z,
                slot,
                stats[1],
                stats[0]);
        }
    }

    private static IInventory resolveHostInventory(TileEntity tile, DataLoomCellSlot slotRef) {
        if (slotRef.hostKind == DataLoomCellSlot.HostKind.DRIVE) {
            if (!(tile instanceof TileDrive)) {
                return null;
            }
            return ((TileDrive) tile).getInternalInventory();
        }
        if (!(tile instanceof TileChest)) {
            return null;
        }
        return ((TileChest) tile).getInternalInventory();
    }

    /**
     * NBT-only cell updates do not always rebuild ME cell handlers; force cache drop + grid refresh so fluid
     * terminals see woven contents.
     */
    private static void refreshMeStorageHost(TileEntity tile) {
        if (tile instanceof IPriorityHost) {
            IPriorityHost priorityHost = (IPriorityHost) tile;
            priorityHost.setPriority(priorityHost.getPriority());
        }
        if (!(tile instanceof appeng.api.networking.IGridHost)) {
            return;
        }
        try {
            appeng.api.networking.IGridNode node = ((appeng.api.networking.IGridHost) tile)
                .getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
            if (node != null && node.getGrid() != null) {
                node.getGrid()
                    .postEvent(new MENetworkCellArrayUpdate());
            }
        } catch (Throwable ignored) {
            // offline drive — NBT is still persisted on the tile
        }
    }

    private static boolean isLoomCell(ItemStack stack) {
        return stack != null && stack.getItem() != null && DataLoomCellUtil.isDataLoomCell(stack.getItem());
    }
}
