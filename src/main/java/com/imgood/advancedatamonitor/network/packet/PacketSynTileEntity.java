package com.imgood.advancedatamonitor.network.packet;

// 原有导入保持不变
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketSynTileEntity implements IMessage {

    // 原有字段和方法保持不变
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

    public static class ServerHandler implements IMessageHandler<PacketSynTileEntity, IMessage> {

        @Override
        public IMessage onMessage(PacketSynTileEntity message, MessageContext ctx) {
            World world = ctx.getServerHandler().playerEntity.worldObj;
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            if (te instanceof TileEntityAdvanceDataMonitor) {
                TileEntityAdvanceDataMonitor tile = (TileEntityAdvanceDataMonitor) te;
                tile.readFromNBT(message.data);
                tile.markDirty();
                // 同步到所有客户端
                // ((TileEntityAdvanceDataMonitor) te).syncData();
            }
            return null;
        }
    }
    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketSynTileEntity, IMessage> {

        @Override
        public IMessage onMessage(PacketSynTileEntity message, MessageContext ctx) {
            // AdvanceDataMonitor.LOG.info("Received sync packet at ({}, {}, {})", message.getX(), message.getY(),
            // message.getZ());
            World world = Minecraft.getMinecraft().theWorld; // 确保获取客户端World
            TileEntity te = world.getTileEntity(message.getX(), message.getY(), message.getZ());
            if (te instanceof TileEntityAdvanceDataMonitor) {
                te.readFromNBT(message.getData());
                world.markBlockForUpdate(message.getX(), message.getY(), message.getZ()); // 触发渲染更新
            }
            return null;
        }
    }
}
