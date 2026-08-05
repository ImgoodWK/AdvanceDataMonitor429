package com.imgood.textech.network.packet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

/**
 * Runtime monitor binding delta sync.
 *
 * <p>The packet carries one binding revision, a small field patch, and optional appended history points so the client
 * does not need a whole monitor NBT blob on every sample tick.
 * </p>
 */
public class PacketMonitorBindingDelta implements IMessage {

    private static final int MAX_NBT_COMPRESSED_BYTES = Short.MAX_VALUE;
    private static final long MAX_NBT_BYTES = 1024L * 1024L;

    private int x;
    private int y;
    private int z;
    private int index;
    private int revision;
    private boolean replaceHistory;
    private NBTTagCompound fieldPatch;
    private NBTTagList appendedData;
    public boolean malformed;

    public PacketMonitorBindingDelta() {}

    public PacketMonitorBindingDelta(int x, int y, int z, int index, int revision, boolean replaceHistory,
        NBTTagCompound fieldPatch, NBTTagList appendedData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.index = index;
        this.revision = revision;
        this.replaceHistory = replaceHistory;
        this.fieldPatch = fieldPatch == null ? null : (NBTTagCompound) fieldPatch.copy();
        this.appendedData = copyList(appendedData);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        index = buf.readUnsignedByte();
        revision = buf.readInt();
        replaceHistory = buf.readBoolean();
        NBTTagCompound root = NetworkPacketCodec.readTag(buf, MAX_NBT_COMPRESSED_BYTES, MAX_NBT_BYTES);
        fieldPatch = root != null && root.hasKey("fieldPatch") ? root.getCompoundTag("fieldPatch") : null;
        appendedData = root != null ? root.getTagList("appendedData", 10) : new NBTTagList();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(index);
        buf.writeInt(revision);
        buf.writeBoolean(replaceHistory);
        NBTTagCompound root = new NBTTagCompound();
        if (fieldPatch != null) {
            root.setTag("fieldPatch", fieldPatch);
        }
        if (appendedData != null) {
            root.setTag("appendedData", appendedData);
        }
        NetworkPacketCodec.writeTag(buf, root, MAX_NBT_COMPRESSED_BYTES);
    }

    private static NBTTagList copyList(NBTTagList source) {
        NBTTagList copy = new NBTTagList();
        if (source == null) {
            return copy;
        }
        for (int i = 0; i < source.tagCount(); i++) {
            copy.appendTag(source.getCompoundTagAt(i).copy());
        }
        return copy;
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketMonitorBindingDelta, IMessage> {

        @Override
        public IMessage onMessage(final PacketMonitorBindingDelta message, MessageContext ctx) {
            if (message == null || message.malformed) {
                return null;
            }
            final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
            minecraft.func_152344_a(new Runnable() {

                @Override
                public void run() {
                    World world = minecraft.theWorld;
                    if (world == null) {
                        return;
                    }
                    TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                    if (te instanceof TileEntityAdvanceDataMonitor) {
                        ((TileEntityAdvanceDataMonitor) te).applyBindingDelta(
                            message.index,
                            message.revision,
                            message.fieldPatch,
                            message.appendedData,
                            message.replaceHistory);
                        world.markBlockForUpdate(message.x, message.y, message.z);
                    }
                }
            });
            return null;
        }
    }
}
