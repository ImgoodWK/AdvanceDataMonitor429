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
    public IMessage onMessage(final PacketSynTileEntity message, final MessageContext ctx) {
        if (message == null || message.getData() == null) return null;
        return PacketHandlers.runOnServer(ctx, new Runnable() {

            @Override
            public void run() {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                World world = player == null ? null : player.worldObj;
                if (world == null
                    || !NetworkValidationUtil.isWithinReach(player, message.getX(), message.getY(), message.getZ())) {
                    return;
                }
                TileEntity tileEntity = world.getTileEntity(message.getX(), message.getY(), message.getZ());
                if (!(tileEntity instanceof TileEntityAdvanceDataMonitor)
                    || !NetworkValidationUtil.canEditOwnedTile(player, tileEntity)) {
                    return;
                }
                TileEntityAdvanceDataMonitor tileEntityADM = (TileEntityAdvanceDataMonitor) tileEntity;
                tileEntityADM.applyClientConfiguration(message.getData());
                tileEntityADM.syncData();
                world.markBlockForUpdate(message.getX(), message.getY(), message.getZ());
            }
        });
    }
}
