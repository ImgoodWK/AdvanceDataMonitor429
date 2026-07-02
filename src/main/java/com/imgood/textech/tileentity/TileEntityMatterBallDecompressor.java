package com.imgood.textech.tileentity;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.utils.MatterBallClusterUtil;
import com.imgood.textech.utils.MatterBallDecompressorCapacityUtil;
import com.imgood.textech.utils.MatterBallDecompressorSpeedUtil;

import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.me.GridAccessException;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.item.AEItemStack;
import fox.spiteful.avaritia.items.ItemMatterCluster;
import io.netty.buffer.ByteBuf;

/**
 * AE matter-ball decompressor: extracts items from Avaritia matter clusters into the network or a local buffer.
 */
public class TileEntityMatterBallDecompressor extends AENetworkTile
    implements IActionHost, IAEAppEngInventory, IConfigManagerHost, IConfigurableObject {

    public static final int INPUT_SLOTS = 9;
    public static final int BUFFER_SLOTS = MatterBallDecompressorCapacityUtil.MAX_BUFFER_SLOTS;
    public static final int UPGRADE_SLOTS = MatterBallDecompressorUpgrades.TOTAL_UPGRADE_SLOTS;

    private final AppEngInternalInventory inputInv = new AppEngInternalInventory(this, INPUT_SLOTS);
    private final AppEngInternalInventory bufferInv = new AppEngInternalInventory(this, BUFFER_SLOTS);
    private final UpgradeInventory upgrades;
    private final MachineSource machineSource = new MachineSource(this);
    private final ConfigManager configManager = new ConfigManager(this);

    private boolean outputToNetwork = true;
    private double processAccumulator = 0.0D;

    public TileEntityMatterBallDecompressor() {
        this.getProxy()
            .setFlags(new GridFlags[] { GridFlags.REQUIRE_CHANNEL });
        this.upgrades = new MatterBallDecompressorUpgrades(this, UPGRADE_SLOTS);
        this.configManager.registerSetting(Settings.BLOCK, YesNo.NO);
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public IGridNode getActionableNode() {
        return getGridNode(ForgeDirection.UNKNOWN);
    }

    @Override
    public void saveChanges() {
        markDirty();
    }

    public IInventory getInventoryByName(String name) {
        if ("upgrades".equals(name)) {
            return upgrades;
        }
        if ("input".equals(name)) {
            return inputInv;
        }
        if ("buffer".equals(name)) {
            return bufferInv;
        }
        return null;
    }

    public boolean isOutputToNetwork() {
        return outputToNetwork;
    }

    public void setOutputToNetwork(boolean outputToNetwork) {
        this.outputToNetwork = outputToNetwork;
        markDirty();
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum setting, Enum newValue) {
        markDirty();
    }

    public boolean isBlockMode() {
        return configManager.getSetting(Settings.BLOCK) == YesNo.YES;
    }

    public void setBlockMode(boolean blockMode) {
        configManager.putSetting(Settings.BLOCK, blockMode ? YesNo.YES : YesNo.NO);
        markDirty();
    }

    public UpgradeInventory getUpgradeInventory() {
        return upgrades;
    }

    public int getBufferSide() {
        return MatterBallDecompressorCapacityUtil.getBufferSide(upgrades);
    }

    public int getActiveBufferSlots() {
        return MatterBallDecompressorCapacityUtil.getActiveBufferSlots(upgrades);
    }

    public AppEngInternalInventory getInputInventory() {
        return inputInv;
    }

    public AppEngInternalInventory getBufferInventory() {
        return bufferInv;
    }

    @TileEvent(TileEventType.TICK)
    public void onTick() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (!outputToNetwork && isBlockMode() && hasAnyBufferItem()) {
            return;
        }
        processAccumulator += 1.0D / 20.0D;
        while (processAccumulator >= 1.0D) {
            if (!decompressOneSecondBatch()) {
                break;
            }
            processAccumulator -= 1.0D;
        }
    }

    private boolean hasAnyBufferItem() {
        int active = getActiveBufferSlots();
        for (int i = 0; i < active; i++) {
            if (bufferInv.getStackInSlot(i) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Once per second: up to {@link MatterBallDecompressorSpeedUtil#getParallelTypesPerSecond} types,
     * each up to {@link MatterBallDecompressorSpeedUtil#getItemsPerTypePerSecond} items.
     * A type with fewer items still consumes one parallel slot for that second.
     */
    private boolean decompressOneSecondBatch() {
        int parallel = MatterBallDecompressorSpeedUtil.getParallelTypesPerSecond(upgrades);
        int maxPerType = MatterBallDecompressorSpeedUtil.getItemsPerTypePerSecond(upgrades);
        if (maxPerType <= 0) {
            return false;
        }
        boolean any = false;
        for (int pass = 0; pass < parallel; pass++) {
            int clusterSlot = findClusterSlot();
            if (clusterSlot < 0) {
                break;
            }
            ItemStack cluster = inputInv.getStackInSlot(clusterSlot);
            java.util.ArrayList<ItemStack> extracted = MatterBallClusterUtil.extractOneTypeBatch(cluster, maxPerType);
            if (extracted.isEmpty()) {
                if (!MatterBallClusterUtil.isMatterCluster(cluster) || cluster.getTagCompound() == null) {
                    inputInv.setInventorySlotContents(clusterSlot, null);
                }
                continue;
            }
            any = true;
            if (cluster.getTagCompound() == null) {
                inputInv.setInventorySlotContents(clusterSlot, null);
            } else {
                inputInv.setInventorySlotContents(clusterSlot, cluster);
            }
            for (ItemStack stack : extracted) {
                if (!outputStack(stack)) {
                    return any;
                }
            }
        }
        return any;
    }

    private boolean outputStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return true;
        }
        if (outputToNetwork) {
            return injectToNetwork(stack);
        }
        return insertToBuffer(stack);
    }

    private int findClusterSlot() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = inputInv.getStackInSlot(i);
            if (MatterBallClusterUtil.isMatterCluster(stack)) {
                Map<?, ?> data = ItemMatterCluster.getClusterData(stack);
                if (data != null && !data.isEmpty()) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean injectToNetwork(ItemStack stack) {
        try {
            IStorageGrid storage = getProxy().getGrid()
                .getCache(IStorageGrid.class);
            if (storage == null) {
                return false;
            }
            IAEItemStack aeStack = AEItemStack.create(stack);
            IAEItemStack remainder = storage.getItemInventory()
                .injectItems(aeStack, Actionable.MODULATE, machineSource);
            if (remainder == null || remainder.getStackSize() <= 0) {
                return true;
            }
            ItemStack left = remainder.getItemStack();
            if (left == null || left.stackSize <= 0) {
                return true;
            }
            return insertToBuffer(left);
        } catch (GridAccessException e) {
            return false;
        }
    }

    private boolean insertToBuffer(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return true;
        }
        int active = getActiveBufferSlots();
        for (int i = 0; i < active; i++) {
            ItemStack slot = bufferInv.getStackInSlot(i);
            if (slot != null && slot.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(slot, stack)) {
                int space = Math.min(slot.getMaxStackSize(), 64) - slot.stackSize;
                if (space > 0) {
                    int move = Math.min(space, stack.stackSize);
                    slot.stackSize += move;
                    stack.stackSize -= move;
                    bufferInv.setInventorySlotContents(i, slot);
                    if (stack.stackSize <= 0) {
                        return true;
                    }
                }
            }
        }
        for (int i = 0; i < active; i++) {
            if (bufferInv.getStackInSlot(i) == null) {
                bufferInv.setInventorySlotContents(i, stack.copy());
                return true;
            }
        }
        return false;
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        markDirty();
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBTEvent(NBTTagCompound data) {
        NBTTagCompound tag = new NBTTagCompound();
        inputInv.writeToNBT(tag, "input");
        bufferInv.writeToNBT(tag, "buffer");
        upgrades.writeToNBT(tag, "upgrades");
        tag.setBoolean("outputToNetwork", outputToNetwork);
        configManager.writeToNBT(tag);
        tag.setDouble("processAccumulator", processAccumulator);
        data.setTag("MatterBallDecompressor", tag);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBTEvent(NBTTagCompound data) {
        if (!data.hasKey("MatterBallDecompressor")) {
            return;
        }
        NBTTagCompound tag = data.getCompoundTag("MatterBallDecompressor");
        inputInv.readFromNBT(tag, "input");
        bufferInv.readFromNBT(tag, "buffer");
        upgrades.readFromNBT(tag, "upgrades");
        outputToNetwork = !tag.hasKey("outputToNetwork") || tag.getBoolean("outputToNetwork");
        if (tag.hasKey("settings")) {
            configManager.readFromNBT(tag);
        } else if (tag.hasKey("blockMode")) {
            setBlockMode(tag.getBoolean("blockMode"));
        }
        processAccumulator = tag.hasKey("processAccumulator") ? tag.getDouble("processAccumulator") : 0.0D;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream(ByteBuf data) {
        data.writeBoolean(outputToNetwork);
        data.writeBoolean(isBlockMode());
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream(ByteBuf data) {
        outputToNetwork = data.readBoolean();
        setBlockMode(data.readBoolean());
        return true;
    }
}
