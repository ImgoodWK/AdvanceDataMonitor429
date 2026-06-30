package com.imgood.textech.network.handler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.network.packet.PacketSynTileEntity;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.NetworkValidationUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class HandlerSynTileEntity implements IMessageHandler<PacketSynTileEntity, IMessage> {

    @Override
    public IMessage onMessage(PacketSynTileEntity message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        World world = player == null ? null : player.worldObj;
        if (world == null) {
            return null;
        }
        if (!NetworkValidationUtil.isWithinReach(player, message.getX(), message.getY(), message.getZ())) {
            return null;
        }
        TileEntity tileEntity = world.getTileEntity(message.getX(), message.getY(), message.getZ());
        if (!(tileEntity instanceof TileEntityAdvanceDataMonitor)) {
            return null;
        }
        if (!NetworkValidationUtil.canEditOwnedTile(player, tileEntity)) {
            return null;
        }
        if (message.getData() == null) {
            return null;
        }
        TileEntityAdvanceDataMonitor tileEntityADM = (TileEntityAdvanceDataMonitor) tileEntity;
        tileEntityADM.readFromNBT(message.getData());
        tileEntityADM.markDirty();
        tileEntityADM.syncData();
        world.markBlockForUpdate(message.getX(), message.getY(), message.getZ());
        return null;
    }
}
