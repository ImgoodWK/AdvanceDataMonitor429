package com.imgood.advancedatamonitor.tileentity;

import static com.imgood.advancedatamonitor.AdvanceDataMonitor.LOG;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.glodblock.github.common.storage.FluidCellInventoryHandler;
import com.glodblock.github.common.storage.IFluidCellInventory;

import appeng.api.AEApi;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

public class TileEntityAdvanceNetworkLink extends AENetworkTile {

    // 物品存储统计（改用 long 防止溢出）
    private long itemTotalBytes = 0L;
    private long itemUsedBytes = 0L;
    private int itemTotalTypes = 0;
    private int itemUsedTypes = 0;

    // 流体存储统计（改用 long 防止溢出）
    private long fluidTotalBytes = 0L;
    private long fluidUsedBytes = 0L;
    private int fluidTotalTypes = 0;
    private int fluidUsedTypes = 0;

    public int facing = 0;

    public TileEntityAdvanceNetworkLink() {
        this.getProxy()
            .setFlags(new GridFlags[] { GridFlags.REQUIRE_CHANNEL });
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection forgeDirection) {
        return AECableType.SMART;
    }

    /**
     * 核心数据更新方法 —— 遍历网络存储单元统计，所有字节值均使用 long 累加
     */
    public void updateNetworkCache() {
        // 改用 long 数组存储累加值
        long[] itemBytes = new long[2]; // [0]总字节, [1]已用字节
        int[] itemTypes = new int[2]; // [0]总类型, [1]已用类型
        long[] fluidBytes = new long[2];
        int[] fluidTypes = new int[2];

        List<TileEntity> tileEntities = getTiles();
        for (TileEntity tile : tileEntities) {
            if (tile instanceof TileDrive) {
                TileDrive drive = (TileDrive) tile;
                for (int i = 0; i < drive.getInternalInventory()
                    .getSizeInventory(); i++) {
                    ItemStack stack = drive.getInternalInventory()
                        .getStackInSlot(i);
                    if (stack != null) {
                        processStorageStack(stack, itemBytes, itemTypes, fluidBytes, fluidTypes);
                    }
                }
            } else if (tile instanceof TileChest) {
                TileChest chest = (TileChest) tile;
                ItemStack stack = chest.getInternalInventory()
                    .getStackInSlot(0);
                if (stack != null) {
                    processStorageStack(stack, itemBytes, itemTypes, fluidBytes, fluidTypes);
                }
            }
        }

        this.itemTotalBytes = itemBytes[0];
        this.itemUsedBytes = itemBytes[1];
        this.itemTotalTypes = itemTypes[0];
        this.itemUsedTypes = itemTypes[1];

        this.fluidTotalBytes = fluidBytes[0];
        this.fluidUsedBytes = fluidBytes[1];
        this.fluidTotalTypes = fluidTypes[0];
        this.fluidUsedTypes = fluidTypes[1];

        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    private void processStorageStack(ItemStack stack, long[] itemBytes, int[] itemTypes, long[] fluidBytes,
        int[] fluidTypes) {
        // 物品存储单元
        IMEInventoryHandler itemInventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.ITEMS);
        if (itemInventory instanceof ICellInventoryHandler) {
            ICellInventoryHandler handler = (ICellInventoryHandler) itemInventory;
            ICellInventory cell = handler.getCellInv();
            if (cell != null) {
                itemBytes[0] += cell.getTotalBytes();
                itemBytes[1] += cell.getUsedBytes();
                itemTypes[0] += cell.getTotalItemTypes();
                itemTypes[1] += cell.getStoredItemTypes();
            }
        }

        // 流体存储单元
        IMEInventoryHandler fluidInventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.FLUIDS);
        if (fluidInventory instanceof ICellInventoryHandler) {
            ICellInventoryHandler handler = (ICellInventoryHandler) fluidInventory;
            ICellInventory cell = handler.getCellInv();
            if (cell != null) {
                fluidBytes[0] += cell.getTotalBytes();
                fluidBytes[1] += cell.getUsedBytes();
                fluidTypes[0] += cell.getTotalItemTypes();
                fluidTypes[1] += cell.getStoredItemTypes();
            }
        }

        // 流体特殊处理（ExtraCells / GlodBlock 的 FluidCellInventoryHandler）
        if (fluidInventory instanceof FluidCellInventoryHandler) {
            FluidCellInventoryHandler handler = (FluidCellInventoryHandler) fluidInventory;
            IFluidCellInventory cell = handler.getCellInv();
            if (cell != null) {
                fluidBytes[0] += cell.getTotalBytes();
                fluidBytes[1] += cell.getUsedBytes();
                fluidTypes[0] += cell.getTotalFluidTypes();
                fluidTypes[1] += cell.getStoredFluidTypes();
            }
        }
    }

    private List<TileEntity> getTiles() {
        List<TileEntity> list = new ArrayList<>();
        try {
            IGrid grid = this.getProxy()
                .getGrid();
            if (grid == null) return list;

            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                if (IChestOrDrive.class.isAssignableFrom(clazz)) {
                    for (IGridNode node : grid.getMachines(clazz)) {
                        TileEntity te = getBaseTileEntity(
                            node.getGridBlock()
                                .getLocation());
                        if (te != null) list.add(te);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error retrieving network tiles: " + e.getMessage());
        }
        return list;
    }

    private static TileEntity getBaseTileEntity(DimensionalCoord coord) {
        if (coord == null) {
            LOG.fatal("Coord is null");
            return null;
        }
        World world = coord.getWorld();
        if (world == null) {
            LOG.fatal("World is null");
            return null;
        }
        return world.getTileEntity(coord.x, coord.y, coord.z);
    }

    // ========== 事件驱动 ==========
    @MENetworkEventSubscribe
    public void updateViaCellEvent(MENetworkCellArrayUpdate event) {
        updateNetworkCache();
    }

    @MENetworkEventSubscribe
    public void updateViaStorageEvent(MENetworkStorageEvent event) {
        updateNetworkCache();
    }

    // ========== 区块加载时强制刷新 ==========
    /*
     * @Override
     * public void validate() {
     * super.validate();
     * if (!worldObj.isRemote) {
     * updateNetworkCache();
     * }
     * }
     */

    // ========== NBT 持久化（使用 getLong/setLong） ==========
    @Override
    public void writeToNBT_AENetwork(NBTTagCompound data) {
        data.setLong("ItemTotalBytes", this.itemTotalBytes);
        data.setLong("ItemUsedBytes", this.itemUsedBytes);
        data.setInteger("ItemTotalTypes", this.itemTotalTypes);
        data.setInteger("ItemUsedTypes", this.itemUsedTypes);

        data.setLong("FluidTotalBytes", this.fluidTotalBytes);
        data.setLong("FluidUsedBytes", this.fluidUsedBytes);
        data.setInteger("FluidTotalTypes", this.fluidTotalTypes);
        data.setInteger("FluidUsedTypes", this.fluidUsedTypes);
    }

    @Override
    public void readFromNBT_AENetwork(NBTTagCompound data) {
        this.itemTotalBytes = data.getLong("ItemTotalBytes");
        this.itemUsedBytes = data.getLong("ItemUsedBytes");
        this.itemTotalTypes = data.getInteger("ItemTotalTypes");
        this.itemUsedTypes = data.getInteger("ItemUsedTypes");

        this.fluidTotalBytes = data.getLong("FluidTotalBytes");
        this.fluidUsedBytes = data.getLong("FluidUsedBytes");
        this.fluidTotalTypes = data.getInteger("FluidTotalTypes");
        this.fluidUsedTypes = data.getInteger("FluidUsedTypes");
    }

    // ========== 客户端同步包（使用 getLong/setLong） ==========
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound syncData = new NBTTagCompound();
        syncData.setLong("ItemTotalBytes", this.itemTotalBytes);
        syncData.setLong("ItemUsedBytes", this.itemUsedBytes);
        syncData.setInteger("ItemTotalTypes", this.itemTotalTypes);
        syncData.setInteger("ItemUsedTypes", this.itemUsedTypes);

        syncData.setLong("FluidTotalBytes", this.fluidTotalBytes);
        syncData.setLong("FluidUsedBytes", this.fluidUsedBytes);
        syncData.setInteger("FluidTotalTypes", this.fluidTotalTypes);
        syncData.setInteger("FluidUsedTypes", this.fluidUsedTypes);

        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, syncData);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound data = pkt.func_148857_g();
        this.itemTotalBytes = data.getLong("ItemTotalBytes");
        this.itemUsedBytes = data.getLong("ItemUsedBytes");
        this.itemTotalTypes = data.getInteger("ItemTotalTypes");
        this.itemUsedTypes = data.getInteger("ItemUsedTypes");

        this.fluidTotalBytes = data.getLong("FluidTotalBytes");
        this.fluidUsedBytes = data.getLong("FluidUsedBytes");
        this.fluidTotalTypes = data.getInteger("FluidTotalTypes");
        this.fluidUsedTypes = data.getInteger("FluidUsedTypes");
    }

    // ========== 公共 Getter（返回 long） ==========
    public long getItemTotalBytes() {
        return this.itemTotalBytes;
    }

    public long getItemUsedBytes() {
        return this.itemUsedBytes;
    }

    public int getItemTotalTypes() {
        return this.itemTotalTypes;
    }

    public int getItemUsedTypes() {
        return this.itemUsedTypes;
    }

    public long getFluidTotalBytes() {
        return this.fluidTotalBytes;
    }

    public long getFluidUsedBytes() {
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

    // 格式化信息（%d 可处理 long）
    public String getStatsInfo() {
        return String.format(
            "§eAE2 Network Status§r\n" + "§aItems:§r %d / %d bytes (%d/%d types)\n"
                + "§bFluids:§r %d / %d bytes (%d/%d types)",
            itemUsedBytes,
            itemTotalBytes,
            itemUsedTypes,
            itemTotalTypes,
            fluidUsedBytes,
            fluidTotalBytes,
            fluidUsedTypes,
            fluidTotalTypes);
    }
}
