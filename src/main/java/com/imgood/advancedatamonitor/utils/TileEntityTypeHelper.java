package com.imgood.advancedatamonitor.utils;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;

import appeng.helpers.AEMultiTile;

public class TileEntityTypeHelper {

    public enum TileEntityType {
        AE,
        NOMAL,
        ADV_NETWORKLINK,
        ADV_CRAFTINGLINK,
        ADV_STORAGELINK
    }

    public static TileEntityType getTileEntityType(TileEntity te) {
        if (te instanceof AEMultiTile) {
            return TileEntityType.AE;
        } else if (te instanceof TileEntityAdvanceCraftingLink) {
            return TileEntityType.ADV_CRAFTINGLINK;
        } else if (te instanceof TileEntityAdvanceNetworkLink) {
            return TileEntityType.ADV_NETWORKLINK;
        } else if (te instanceof TileEntityAdvanceStorageLink) {
            return TileEntityType.ADV_STORAGELINK;
        }
        return TileEntityType.NOMAL;
    }

    public static TileEntityType getTileEntityType(BlockPos blockPos) {
        int x = blockPos.getX();
        int y = blockPos.getY();
        int z = blockPos.getZ();
        World world = blockPos.getWorld();
        TileEntity te = world.getTileEntity(x, y, z);
        switch (getTileEntityType(te)) {
            case AE:
                return TileEntityType.AE;
            case ADV_NETWORKLINK:
                return TileEntityType.ADV_NETWORKLINK;
            case ADV_CRAFTINGLINK:
                return TileEntityType.ADV_CRAFTINGLINK;
            case ADV_STORAGELINK:
                return TileEntityType.ADV_STORAGELINK;
            case NOMAL:
            default:
                return TileEntityType.NOMAL;
        }
    }
}
