package com.imgood.textech.network.packet;

// Client handler uses Minecraft; keep import scoped to nested class only via fully qualified name below.
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketSynTileEntity implements IMessage {

    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int FIXED_BODY_BYTES = 3 * Integer.BYTES;
    private static final int MAX_NBT_COMPRESSED_BYTES = MAX_PACKET_BODY_BYTES - FIXED_BODY_BYTES - 2;
    private static final long MAX_NBT_BYTES = 1024L * 1024L;

    // 原有字段和方法保持不取
    private int x, y, z, index;
    private NBTTagCompound data;
    public boolean malformed;

    public PacketSynTileEntity() {}

    public PacketSynTileEntity(int x, int y, int z, NBTTagCompound data) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.data = data;
    }

    public PacketSynTileEntity(int x, int y, int z, int index, NBTTagCompound data) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.index = index;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        x = 0;
        y = 0;
        z = 0;
        data = null;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Tile entity sync exceeds packet body limit");
            }
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            data = NetworkPacketCodec.readTag(buf, MAX_NBT_COMPRESSED_BYTES, MAX_NBT_BYTES);
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Tile entity sync has trailing or oversized data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            x = 0;
            y = 0;
            z = 0;
            data = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            NetworkPacketCodec.writeTag(buf, data, MAX_NBT_COMPRESSED_BYTES);
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Tile entity sync exceeds packet body limit");
            }
        } catch (RuntimeException error) {
            buf.writerIndex(start);
            throw error;
        }
    }

    public boolean fitsPacketBudget() {
        ByteBuf scratch = Unpooled.buffer(64);
        try {
            toBytes(scratch);
            return scratch.readableBytes() <= MAX_PACKET_BODY_BYTES;
        } catch (RuntimeException error) {
            return false;
        } finally {
            scratch.release();
        }
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public NBTTagCompound getData() {
        return data;
    }

    public void setData(NBTTagCompound data) {
        this.data = data;
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketSynTileEntity, IMessage> {

        @Override
        public IMessage onMessage(PacketSynTileEntity message, MessageContext ctx) {
            if (message == null || message.malformed || message.getData() == null) {
                return null;
            }
            // AdvanceDataMonitor.LOG.info("Received sync packet at ({}, {}, {})", message.getX(), message.getY(),
            // message.getZ());
            World world = net.minecraft.client.Minecraft.getMinecraft().theWorld; // 确保获取客户端World
            TileEntity te = world.getTileEntity(message.getX(), message.getY(), message.getZ());
            if (te instanceof TileEntityAdvanceDataMonitor) {
                te.readFromNBT(message.getData());
                world.markBlockForUpdate(message.getX(), message.getY(), message.getZ()); // 触发渲染更新
            }
            return null;
        }
    }
}
