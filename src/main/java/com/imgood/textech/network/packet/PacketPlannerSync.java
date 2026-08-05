package com.imgood.textech.network.packet;

import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPlannerSync implements IMessage {

    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_NBT_COMPRESSED_BYTES = MAX_PACKET_BODY_BYTES - 4 - 2;
    private static final long MAX_NBT_BYTES = 512L * 1024L;

    public int slot;
    public NBTTagCompound nbt;
    public boolean malformed;

    public PacketPlannerSync() {}

    public PacketPlannerSync(int slot, NBTTagCompound nbt) {
        this.slot = slot;
        this.nbt = nbt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            buf.writeInt(slot);
            NetworkPacketCodec.writeTag(buf, nbt == null ? new NBTTagCompound() : nbt, MAX_NBT_COMPRESSED_BYTES);
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Planner sync exceeds packet body limit");
            }
        } catch (RuntimeException error) {
            buf.writerIndex(start);
            throw error;
        }
    }

    public boolean fitsPacketBudget() {
        ByteBuf scratch = Unpooled.buffer(128);
        try {
            toBytes(scratch);
            return scratch.readableBytes() <= MAX_PACKET_BODY_BYTES;
        } catch (RuntimeException error) {
            return false;
        } finally {
            scratch.release();
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        slot = 0;
        nbt = null;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Planner sync exceeds packet body limit");
            }
            slot = buf.readInt();
            nbt = NetworkPacketCodec.readTag(buf, MAX_NBT_COMPRESSED_BYTES, MAX_NBT_BYTES);
            if (nbt == null) nbt = new NBTTagCompound();
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Planner sync has trailing or oversized data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            slot = 0;
            nbt = null;
        }
    }
}
