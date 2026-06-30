package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.assistant.AssistantMonitorRegistry;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.NetworkValidationUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client notifies server which Advance Data Monitor the player is interacting with (e.g. via hotkey).
 */
public class PacketMonitorRecord implements IMessage {

    private int x;
    private int y;
    private int z;

    public PacketMonitorRecord() {}

    public PacketMonitorRecord(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    public static class Handler implements IMessageHandler<PacketMonitorRecord, IMessage> {

        @Override
        public IMessage onMessage(final PacketMonitorRecord message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    if (player == null || player.worldObj == null) {
                        return;
                    }
                    if (!NetworkValidationUtil.isWithinReach(player, message.x, message.y, message.z)) {
                        return;
                    }
                    if (!player.worldObj.blockExists(message.x, message.y, message.z)) {
                        return;
                    }
                    TileEntity tile = player.worldObj.getTileEntity(message.x, message.y, message.z);
                    if (!(tile instanceof TileEntityAdvanceDataMonitor)) {
                        return;
                    }
                    AssistantMonitorRegistry
                        .record(player, player.worldObj.provider.dimensionId, message.x, message.y, message.z);
                }
            });
            return null;
        }
    }
}
