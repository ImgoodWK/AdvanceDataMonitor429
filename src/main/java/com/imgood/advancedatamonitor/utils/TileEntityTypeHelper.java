package com.imgood.advancedatamonitor.utils;

import appeng.helpers.AEMultiTile;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import shedar.mods.ic2.nuclearcontrol.crossmod.appeng.TileEntityNetworkLink;

import java.util.Objects;

public class TileEntityTypeHelper {
    public enum TileEntityType {
        AE,
        NOMAL,
        NETWORKLINK,
        ADV_NETWORKLINK
    }
     public static TileEntityType getTileEntityType(TileEntity te) {
        //貌似不允许switch case
         if (te instanceof AEMultiTile) {

             return TileEntityType.AE;
         } else if (te instanceof TileEntityNetworkLink) {

             return TileEntityType.NETWORKLINK;
         } else if (te instanceof TileEntityAdvanceNetworkLink) {

             return TileEntityType.ADV_NETWORKLINK;
         }
         return TileEntityType.NOMAL;
     }

     public static TileEntityType getTileEntityType(BlockPos blockPos) {
        int  x = blockPos.getX();
        int  y = blockPos.getY();
        int  z = blockPos.getZ();
        World world = blockPos.getWorld();
        TileEntity te = world.getTileEntity(x, y, z);
        switch ( getTileEntityType(te)) {
            case AE:
                return TileEntityType.AE;
            case NETWORKLINK:
                return TileEntityType.NETWORKLINK;
            case ADV_NETWORKLINK:
                return TileEntityType.ADV_NETWORKLINK;
            case NOMAL:
            default:
                return TileEntityType.NOMAL;
        }
     }
}
