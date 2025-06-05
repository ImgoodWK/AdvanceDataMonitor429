package com.imgood.advancedatamonitor.tileentity;

import appeng.api.AEApi;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.storage.*;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import com.glodblock.github.common.storage.FluidCellInventoryHandler;
import com.glodblock.github.common.storage.IFluidCellInventory;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

public class TileEntityAdvanceNetworkLink extends AENetworkTile {
    // 物品存储统计（实例变量）
    private int itemTotalBytes = 0;
    private int itemUsedBytes = 0;
    private int itemTotalTypes = 0;
    private int itemUsedTypes = 0;

    // 流体存储统计（实例变量）
    private int fluidTotalBytes = 0;
    private int fluidUsedBytes = 0;
    private int fluidTotalTypes = 0;
    private int fluidUsedTypes = 0;

    public int facing = 0;

    public TileEntityAdvanceNetworkLink() {
        this.getProxy().setFlags(new GridFlags[]{GridFlags.REQUIRE_CHANNEL});
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    public AECableType getCableConnectionType(ForgeDirection forgeDirection) {
        return AECableType.SMART;
    }

    public void updateNetworkCache() {
        // 使用数组变量来存储累加值
        int[] itemStats = new int[4]; // [0]总字节, [1]已用字节, [2]总类型, [3]已用类型
        int[] fluidStats = new int[4]; // 同上

        List<TileEntity> tileEntities = getTiles();
        for (TileEntity tile : tileEntities) {
            if (tile instanceof TileDrive) {
                TileDrive drive = (TileDrive) tile;
                for (int i = 0; i < drive.getInternalInventory().getSizeInventory(); i++) {
                    ItemStack stack = drive.getInternalInventory().getStackInSlot(i);
                    if (stack != null) {
                        processStorageStack(stack, itemStats, fluidStats);
                    }
                }
            } else if (tile instanceof TileChest) {
                TileChest chest = (TileChest) tile;
                ItemStack stack = chest.getInternalInventory().getStackInSlot(0);
                if (stack != null) {
                    processStorageStack(stack, itemStats, fluidStats);
                }
            }
        }

        // 直接使用数组值更新实例变量
        this.itemTotalBytes = itemStats[0];
        this.itemUsedBytes = itemStats[1];
        this.itemTotalTypes = itemStats[2];
        this.itemUsedTypes = itemStats[3];

        this.fluidTotalBytes = fluidStats[0];
        this.fluidUsedBytes = fluidStats[1];
        this.fluidTotalTypes = fluidStats[2];
        this.fluidUsedTypes = fluidStats[3];
    }

    private void processStorageStack(ItemStack stack, int[] itemStats, int[] fluidStats) {
        // 物品存储单元处理 - 直接累加到数组
        IMEInventoryHandler itemInventory = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, StorageChannel.ITEMS);
        if (itemInventory instanceof ICellInventoryHandler) {
            ICellInventoryHandler handler = (ICellInventoryHandler) itemInventory;
            ICellInventory cell = handler.getCellInv();
            if (cell != null) {
                itemStats[0] += cell.getTotalBytes();
                itemStats[1] += cell.getUsedBytes();
                itemStats[2] += cell.getTotalItemTypes();
                itemStats[3] += cell.getStoredItemTypes();
            }
        }

        // 流体存储单元处理 - 直接累加到数组
        IMEInventoryHandler fluidInventory = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, StorageChannel.FLUIDS);
        if (fluidInventory instanceof ICellInventoryHandler) {
            ICellInventoryHandler handler = (ICellInventoryHandler) fluidInventory;
            ICellInventory cell = handler.getCellInv();
            if (cell != null) {
                fluidStats[0] += cell.getTotalBytes();
                fluidStats[1] += cell.getUsedBytes();
                fluidStats[2] += cell.getTotalItemTypes();
                fluidStats[3] += cell.getStoredItemTypes();
            }
        }

        if (fluidInventory instanceof FluidCellInventoryHandler) {
            FluidCellInventoryHandler handler = (FluidCellInventoryHandler) fluidInventory;
            IFluidCellInventory cell = handler.getCellInv();
            if (cell != null) {
                fluidStats[0] += cell.getTotalBytes();
                fluidStats[1] += cell.getUsedBytes();
                fluidStats[2] += cell.getTotalFluidTypes();
                fluidStats[3] += cell.getStoredFluidTypes();
            }
        }
    }


    private List<TileEntity> getTiles() {
        List<TileEntity> list = new ArrayList<>();
        try {
            IGrid grid = this.getProxy().getGrid();
            if (grid == null) return list;

            // 更可靠的接口检查方式
            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                if (IChestOrDrive.class.isAssignableFrom(clazz)) {
                    for (IGridNode node : grid.getMachines(clazz)) {
                        TileEntity te = getBaseTileEntity(node.getGridBlock().getLocation());
                        if (te != null) list.add(te);
                    }
                }
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Error retrieving network tiles: " + e.getMessage());
        }
        return list;
    }

    private static TileEntity getBaseTileEntity(DimensionalCoord coord) {
        if (coord == null) {
            AdvanceDataMonitor.LOG.fatal("Coord is null");
            return null;
        }
        World world = coord.getWorld();
        if (world == null) {
            AdvanceDataMonitor.LOG.fatal("World is null");
            return null;
        }
        return world.getTileEntity(coord.x, coord.y, coord.z);
    }

    @MENetworkEventSubscribe
    public void updateViaCellEvent(MENetworkCellArrayUpdate event) {
        updateNetworkCache();
    }

    @MENetworkEventSubscribe
    public void updateViaStorageEvent(MENetworkStorageEvent event) {
        updateNetworkCache();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound syncData = new NBTTagCompound();
        // 物品数据
        syncData.setInteger("ItemTotalBytes", this.itemTotalBytes);
        syncData.setInteger("ItemUsedBytes", this.itemUsedBytes);
        syncData.setInteger("ItemTotalTypes", this.itemTotalTypes);
        syncData.setInteger("ItemUsedTypes", this.itemUsedTypes);

        // 流体数据
        syncData.setInteger("FluidTotalBytes", this.fluidTotalBytes);
        syncData.setInteger("FluidUsedBytes", this.fluidUsedBytes);
        syncData.setInteger("FluidTotalTypes", this.fluidTotalTypes);
        syncData.setInteger("FluidUsedTypes", this.fluidUsedTypes);

        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, syncData);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound data = pkt.func_148857_g();
        // 读取物品数据
        this.itemTotalBytes = data.getInteger("ItemTotalBytes");
        this.itemUsedBytes = data.getInteger("ItemUsedBytes");
        this.itemTotalTypes = data.getInteger("ItemTotalTypes");
        this.itemUsedTypes = data.getInteger("ItemUsedTypes");

        // 读取流体数据
        this.fluidTotalBytes = data.getInteger("FluidTotalBytes");
        this.fluidUsedBytes = data.getInteger("FluidUsedBytes");
        this.fluidTotalTypes = data.getInteger("FluidTotalTypes");
        this.fluidUsedTypes = data.getInteger("FluidUsedTypes");
    }

    // 物品统计获取方法
    public int getItemTotalBytes() {
        return this.itemTotalBytes;
    }

    public int getItemUsedBytes() {
        return this.itemUsedBytes;
    }

    public int getItemTotalTypes() {
        return this.itemTotalTypes;
    }

    public int getItemUsedTypes() {
        return this.itemUsedTypes;
    }

    // 流体统计获取方法
    public int getFluidTotalBytes() {
        return this.fluidTotalBytes;
    }

    public int getFluidUsedBytes() {
        return this.fluidUsedBytes;
    }

    public int getFluidTotalTypes() {
        return this.fluidTotalTypes;
    }

    public int getFluidUsedTypes() {
        return this.fluidUsedTypes;
    }

    public int getFacing() {
        return facing;
    }

}