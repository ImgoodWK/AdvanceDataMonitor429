package com.imgood.advancedatamonitor.tileentity;

import static com.imgood.advancedatamonitor.AdvanceDataMonitor.LOG;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.advancedatamonitor.compat.ae.AeCompat;
import com.imgood.advancedatamonitor.compat.ae.AeStorageStatsAccumulator;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

/**
 * Display names / 显示名称:
 * - EN: Network Linker
 * - ZH: 网络链接器
 * Lang keys: tile.NetworkLinkBlock.name (parent block)
 */
public class TileEntityAdvanceNetworkLink extends AENetworkTile implements IOwnableTile {

    private String ownerName = "";

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
    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    @Override
    public void setOwnerName(String name) {
        this.ownerName = name == null ? "" : name;
        markDirty();
    }

    @Override
    public void setOwnerFromPlacer(EntityLivingBase placer) {
        setOwnerName(OwnableTileUtil.nameFromPlacer(placer));
    }

    @Override
    public void claimOwnerIfEmpty(EntityPlayer player) {
        // Network link: no claim-on-open; re-place to set owner.
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
        AeStorageStatsAccumulator stats = new AeStorageStatsAccumulator();

        List<TileEntity> tileEntities = getTiles();
        for (TileEntity tile : tileEntities) {
            if (tile instanceof TileDrive) {
                TileDrive drive = (TileDrive) tile;
                for (int i = 0; i < drive.getInternalInventory()
                    .getSizeInventory(); i++) {
                    ItemStack stack = drive.getInternalInventory()
                        .getStackInSlot(i);
                    if (stack != null) {
                        AeCompat.cells()
                            .accumulateStorageStack(stack, stats);
                    }
                }
            } else if (tile instanceof TileChest) {
                TileChest chest = (TileChest) tile;
                ItemStack stack = chest.getInternalInventory()
                    .getStackInSlot(0);
                if (stack != null) {
                    AeCompat.cells()
                        .accumulateStorageStack(stack, stats);
                }
            }
        }

        this.itemTotalBytes = stats.itemBytes[0];
        this.itemUsedBytes = stats.itemBytes[1];
        this.itemTotalTypes = stats.itemTypes[0];
        this.itemUsedTypes = stats.itemTypes[1];

        this.fluidTotalBytes = stats.fluidBytes[0];
        this.fluidUsedBytes = stats.fluidBytes[1];
        this.fluidTotalTypes = stats.fluidTypes[0];
        this.fluidUsedTypes = stats.fluidTypes[1];

        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
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
        OwnableTileUtil.writeOwner(data, ownerName);
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
        ownerName = OwnableTileUtil.readOwner(data);
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
