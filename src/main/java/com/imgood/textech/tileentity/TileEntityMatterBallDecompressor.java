package com.imgood.textech.tileentity;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.Config;
import com.imgood.textech.utils.AeUpgradeSpeedUtil;
import com.imgood.textech.utils.MatterBallClusterUtil;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.item.AEItemStack;
import fox.spiteful.avaritia.items.ItemMatterCluster;
import io.netty.buffer.ByteBuf;

/**
 * AE matter-ball decompressor: extracts items from Avaritia matter clusters into the network or a local buffer.
 */
public class TileEntityMatterBallDecompressor extends AENetworkTile
    implements IActionHost, IAEAppEngInventory {

    public static final int INPUT_SLOTS = 9;
    public static final int BUFFER_SLOTS = 81;
    public static final int UPGRADE_SLOTS = 4;

    private final AppEngInternalInventory inputInv = new AppEngInternalInventory(this, INPUT_SLOTS);
    private final AppEngInternalInventory bufferInv = new AppEngInternalInventory(this, BUFFER_SLOTS);
    private final UpgradeInventory upgrades;
    private final MachineSource machineSource = new MachineSource(this);

    private boolean outputToNetwork = true;
    private boolean blockMode = false;
    private double processAccumulator = 0.0D;

    public TileEntityMatterBallDecompressor() {
        this.getProxy()
            .setFlags(new GridFlags[] { GridFlags.REQUIRE_CHANNEL });
        this.upgrades = new MatterBallDecompressorUpgrades(this, UPGRADE_SLOTS);
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

    public boolean isBlockMode() {
        return blockMode;
    }

    public void setBlockMode(boolean blockMode) {
        this.blockMode = blockMode;
        markDirty();
    }

    public UpgradeInventory getUpgradeInventory() {
        return upgrades;
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
        if (!outputToNetwork && blockMode && hasAnyBufferItem()) {
            return;
        }
        double itemsPerSecond = Config.matterBallDecompressorItemsPerSecond
            * AeUpgradeSpeedUtil.getSpeedMultiplier(upgrades);
        if (itemsPerSecond <= 0.0D) {
            return;
        }
        processAccumulator += itemsPerSecond / 20.0D;
        while (processAccumulator >= 1.0D) {
            if (!decompressOneItem()) {
                break;
            }
            processAccumulator -= 1.0D;
        }
    }

    private boolean hasAnyBufferItem() {
        for (int i = 0; i < BUFFER_SLOTS; i++) {
            if (bufferInv.getStackInSlot(i) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean decompressOneItem() {
        int clusterSlot = findClusterSlot();
        if (clusterSlot < 0) {
            return false;
        }
        ItemStack cluster = inputInv.getStackInSlot(clusterSlot);
        ItemStack extracted = MatterBallClusterUtil.extractOne(cluster, 64);
        if (extracted == null) {
            if (!MatterBallClusterUtil.isMatterCluster(cluster) || cluster.getTagCompound() == null) {
                inputInv.setInventorySlotContents(clusterSlot, null);
            }
            return false;
        }
        if (cluster.getTagCompound() == null) {
            inputInv.setInventorySlotContents(clusterSlot, null);
        } else {
            inputInv.setInventorySlotContents(clusterSlot, cluster);
        }

        if (outputToNetwork) {
            return injectToNetwork(extracted);
        }
        return insertToBuffer(extracted);
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
        for (int i = 0; i < BUFFER_SLOTS; i++) {
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
        for (int i = 0; i < BUFFER_SLOTS; i++) {
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
        tag.setBoolean("blockMode", blockMode);
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
        blockMode = tag.getBoolean("blockMode");
        processAccumulator = tag.hasKey("processAccumulator") ? tag.getDouble("processAccumulator") : 0.0D;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream(ByteBuf data) {
        data.writeBoolean(outputToNetwork);
        data.writeBoolean(blockMode);
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream(ByteBuf data) {
        outputToNetwork = data.readBoolean();
        blockMode = data.readBoolean();
        return true;
    }
}
