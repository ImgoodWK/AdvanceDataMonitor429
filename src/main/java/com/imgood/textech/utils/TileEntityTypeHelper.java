package com.imgood.textech.utils;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;

import appeng.helpers.AEMultiTile;

public class TileEntityTypeHelper {

    public enum TileEntityType {
        AE,
        NOMAL,
        ADV_NETWORKLINK,
        /** @deprecated merged into ADV_NETWORKLINK; kept for switch compatibility */
        @Deprecated
        ADV_CRAFTINGLINK,
        /** @deprecated merged into ADV_NETWORKLINK; kept for switch compatibility */
        @Deprecated
        ADV_STORAGELINK
    }

    public static TileEntityType getTileEntityType(TileEntity te) {
        if (te instanceof AEMultiTile) {
            return TileEntityType.AE;
        } else if (te instanceof TileEntityAdvanceNetworkLink) {
            return TileEntityType.ADV_NETWORKLINK;
        }
        return TileEntityType.NOMAL;
    }

    public static TileEntityType getTileEntityType(BlockPos blockPos) {
        int x = blockPos.getX();
        int y = blockPos.getY();
        int z = blockPos.getZ();
        World world = blockPos.getWorld();
        TileEntity te = world.getTileEntity(x, y, z);
        return getTileEntityType(te);
    }

    /**
     * Route monitor sub-GUI / tick collection by binding dataType when target is the unified AE link.
     */
    public static String resolveLinkDisplayMode(String dataType) {
        if (dataType == null) {
            return "network";
        }
        if ("crafting".equals(dataType)) {
            return "crafting";
        }
        if ("storage".equals(dataType)) {
            return "storage";
        }
        return "network";
    }
}
