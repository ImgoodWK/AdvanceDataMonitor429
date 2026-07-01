package com.imgood.textech.items.cell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.imgood.textech.AdvanceDataMonitor;

import appeng.api.AEApi;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;

public class DataLoomCellHandler implements ICellHandler {

    public static void register() {
        AEApi.instance()
            .registries()
            .cell()
            .addCellHandler(new DataLoomCellHandler());
        AdvanceDataMonitor.LOG.info("[DataLoomCell] Registered Data Loom cell handler.");
    }

    @Override

    public boolean isCell(ItemStack is) {

        return is != null && DataLoomCellUtil.isDataLoomCell(is.getItem());

    }

    @Override

    public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider container, StorageChannel channel) {

        if (is == null) {

            return null;

        }

        if (container instanceof TileDrive) {
            DataLoomCellIndex.INSTANCE.registerDrive((TileDrive) container);
        } else if (container instanceof TileChest) {
            DataLoomCellIndex.INSTANCE.registerChest((TileChest) container);
        }

        IMEInventoryHandler internal = null;

        if (channel == StorageChannel.FLUIDS) {

            if (is.getItem() instanceof IDataLoomFluidCell) {

                internal = new DataLoomFluidInventory(is, container);

            }

        } else if (channel == StorageChannel.ITEMS) {

            if (is.getItem() instanceof AbstractDataLoomItemCell) {

                internal = new DataLoomItemInventory(is, container, (AbstractDataLoomItemCell) is.getItem());

            }

        }

        if (internal == null) {

            return null;

        }

        return DataLoomCellInventoryHandler.wrap(internal, is, channel);

    }

    @Override
    public int getStatusForCell(ItemStack is, IMEInventory inventory) {
        Object target = inventory;
        if (inventory instanceof DataLoomCellInventoryHandler) {
            target = ((DataLoomCellInventoryHandler) inventory).getDelegate();
        }
        if (target instanceof DataLoomItemInventory) {
            return ((DataLoomItemInventory) target).getCellStatus();
        }
        if (target instanceof DataLoomFluidInventory) {
            return ((DataLoomFluidInventory) target).getCellStatus();
        }
        return 0;
    }

    @Override

    public double cellIdleDrain(ItemStack is, IMEInventory inventory) {

        return 0.0;

    }

    @Override

    public IIcon getTopTexture_Light() {

        return null;

    }

    @Override

    public IIcon getTopTexture_Medium() {

        return null;

    }

    @Override

    public IIcon getTopTexture_Dark() {

        return null;

    }

    @Override

    public void openChestGui(EntityPlayer player, IChestOrDrive chestOrDrive, ICellHandler cellHandler,

        IMEInventoryHandler invHandler, ItemStack cellItem, StorageChannel channel) {}

}
