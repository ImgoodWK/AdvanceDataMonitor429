package com.imgood.textech.tileentity;

import static com.imgood.textech.AdvanceDataMonitor.LOG;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.handler.NetworkLinkGridStatsCache;
import com.imgood.textech.handler.NetworkLinkGridStatsCache.StatsSnapshot;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.tile.grid.AENetworkTile;

/**
 * Display names / 显示名称:
 * - EN: Network Linker
 * - ZH: 网络链接器
 * Lang keys: tile.NetworkLinkBlock.name (parent block)
 */
public class TileEntityAdvanceNetworkLink extends AENetworkTile implements IOwnableTile {

    private String ownerName = "";

    // 物品存储统计（改用long 防止溢出：
    private long itemTotalBytes = 0L;
    private long itemUsedBytes = 0L;
    private int itemTotalTypes = 0;
    private int itemUsedTypes = 0;

    // 流体存储统计（改用long 防止溢出：
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
     * Request a debounced refresh via the shared per-grid stats cache.
     */
    public void updateNetworkCache() {
        NetworkLinkGridStatsCache.scheduleRefresh(this);
    }

    /**
     * Applies shared grid statistics to this link (called by {@link com.imgood.textech.handler.ConnectorTickService}).
     */
    public void refreshFromSharedCache(long worldTick) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        IGrid grid = NetworkLinkGridStatsCache.resolveGrid(this);
        if (grid == null) {
            return;
        }
        StatsSnapshot snapshot = NetworkLinkGridStatsCache.getOrCompute(grid, worldObj, worldTick);
        if (snapshot == null) {
            return;
        }
        applyStatsSnapshot(snapshot);
    }

    private void applyStatsSnapshot(StatsSnapshot snapshot) {
        boolean changed = !snapshot.equalsValues(
            itemTotalBytes,
            itemUsedBytes,
            itemTotalTypes,
            itemUsedTypes,
            fluidTotalBytes,
            fluidUsedBytes,
            fluidTotalTypes,
            fluidUsedTypes);

        this.itemTotalBytes = snapshot.itemTotalBytes;
        this.itemUsedBytes = snapshot.itemUsedBytes;
        this.itemTotalTypes = snapshot.itemTotalTypes;
        this.itemUsedTypes = snapshot.itemUsedTypes;
        this.fluidTotalBytes = snapshot.fluidTotalBytes;
        this.fluidUsedBytes = snapshot.fluidUsedBytes;
        this.fluidTotalTypes = snapshot.fluidTotalTypes;
        this.fluidUsedTypes = snapshot.fluidUsedTypes;

        if (changed) {
            markDirty();
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    // ========== 事件驱动（debounced；不订阅 MENetworkStorageEvent 以避免每次存取全扫） ==========
    @MENetworkEventSubscribe
    public void updateViaCellEvent(MENetworkCellArrayUpdate event) {
        IGrid grid = NetworkLinkGridStatsCache.resolveGrid(this);
        if (grid != null) {
            NetworkLinkGridStatsCache.invalidate(grid);
        }
        updateNetworkCache();
    }

    // ========== 区块加载时强制刷方==========
    /*
     * @Override
     * public void validate() {
     * super.validate();
     * if (!worldObj.isRemote) {
     * updateNetworkCache();
     * }
     * }
     */

    // ========== NBT 持久化（使用 getLong/setLong：==========
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

    // ========== 客户端同步包（使用getLong/setLong：==========
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

    // ========== 公共 Getter（返回long：==========
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

    // 格式化信息（%d 可处理long：
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
