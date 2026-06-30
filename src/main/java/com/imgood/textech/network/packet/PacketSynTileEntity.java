package com.imgood.textech.network.packet;

// Client handler uses Minecraft; keep import scoped to nested class only via fully qualified name below.
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class PacketSynTileEntity implements IMessage {

    // 原有字段和方法保持不取
    private int x, y, z, index;
    private NBTTagCompound data;

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
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ByteBufUtils.writeTag(buf, data);
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
