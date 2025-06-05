package com.imgood.advancedatamonitor.network.handler;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class HandlerSynTileEntity implements IMessageHandler<PacketSynTileEntity, IMessage> {

    @Override
    public IMessage onMessage(PacketSynTileEntity message, MessageContext ctx) {
        World world = ctx.getServerHandler().playerEntity.worldObj;
        if (world != null) {
            TileEntity tileEntity = world.getTileEntity(message.getX(), message.getY(), message.getZ());
            if ((tileEntity != null) && (tileEntity instanceof TileEntityAdvanceDataMonitor)) {
                TileEntityAdvanceDataMonitor tileEntityADM = (TileEntityAdvanceDataMonitor) tileEntity;
                tileEntityADM.readFromNBT(message.getData());
                tileEntityADM.markDirty();
                tileEntityADM.syncData();
                world.markBlockForUpdate(message.getX(), message.getY(), message.getZ());
            }
        }
        return null;
    }
}
