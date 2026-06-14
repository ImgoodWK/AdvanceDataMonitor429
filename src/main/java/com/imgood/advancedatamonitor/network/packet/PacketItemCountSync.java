package com.imgood.advancedatamonitor.network.packet;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * @program: AdvanceDataMonitor429
 * @description:
 * @author: Imgood
 * @create: 2025-07-02 09:30
 **/
public class PacketItemCountSync implements IMessage {

    private int x, y, z;
    private Map<Integer, Long> cacheData = new HashMap<>();

    public PacketItemCountSync() {}

    public PacketItemCountSync(int x, int y, int z, Map<Integer, Long> cacheData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.cacheData = cacheData;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();

        int size = buf.readByte();
        for (int i = 0; i < size; i++) {
            int slot = buf.readByte();
            long count = buf.readLong();
            cacheData.put(slot, count);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);

        buf.writeByte(cacheData.size());
        for (Map.Entry<Integer, Long> entry : cacheData.entrySet()) {
            buf.writeByte(entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    public static class Handler implements IMessageHandler<PacketItemCountSync, IMessage> {

        @Override
        public IMessage onMessage(PacketItemCountSync message, MessageContext ctx) {
            TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileEntityAdvanceStorageLink) {
                ((TileEntityAdvanceStorageLink) te).updateClientCache(message.cacheData);
            }
            return null;
        }
    }
}
