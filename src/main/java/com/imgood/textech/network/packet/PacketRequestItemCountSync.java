package com.imgood.textech.network.packet;

import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.tileentity.TileEntityAdvanceStorageLink;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * @program: AdvanceDataMonitor429
 * @description:
 * @author: Imgood
 * @create: 2025-07-02 09:27
 **/
public class PacketRequestItemCountSync implements IMessage {

    private int x, y, z;

    public PacketRequestItemCountSync() {}

    public PacketRequestItemCountSync(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketRequestItemCountSync, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestItemCountSync message, MessageContext ctx) {
            TileEntity te = ctx.getServerHandler().playerEntity.worldObj.getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileEntityAdvanceStorageLink) {
                ((TileEntityAdvanceStorageLink) te).handleItemCountSyncRequest();
            }
            return null;
        }
    }
}
