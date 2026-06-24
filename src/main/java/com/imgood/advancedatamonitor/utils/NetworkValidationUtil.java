package com.imgood.advancedatamonitor.utils;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

import com.imgood.advancedatamonitor.tileentity.IOwnableTile;

public final class NetworkValidationUtil {

    public static final double MAX_REACH = 64.0D;
    private static final double MAX_REACH_SQ = MAX_REACH * MAX_REACH;

    private NetworkValidationUtil() {}

    public static boolean isWithinReach(EntityPlayerMP player, int x, int y, int z) {
        if (player == null || player.worldObj == null) {
            return false;
        }
        if (y < 0 || y > 255) {
            return false;
        }
        if (!player.worldObj.blockExists(x, y, z)) {
            return false;
        }
        return player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) <= MAX_REACH_SQ;
    }

    public static boolean isValidInventorySlot(EntityPlayerMP player, int slot) {
        return player != null && player.inventory != null && slot >= 0 && slot < player.inventory.getSizeInventory();
    }

    public static boolean canEditOwnedTile(EntityPlayerMP player, TileEntity tile) {
        if (player == null || tile == null) {
            return false;
        }
        if (!isWithinReach(player, tile.xCoord, tile.yCoord, tile.zCoord)) {
            return false;
        }
        if (!(tile instanceof IOwnableTile)) {
            return true;
        }
        String owner = ((IOwnableTile) tile).getOwnerName();
        if (owner == null || owner.trim()
            .isEmpty()) {
            return true;
        }
        return owner.equals(player.getCommandSenderName())
            || player.canCommandSenderUseCommand(2, "advancedatamonitor");
    }
}
