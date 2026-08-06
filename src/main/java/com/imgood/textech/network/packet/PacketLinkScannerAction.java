package com.imgood.textech.network.packet;

import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketLinkScannerAction implements IMessage {

    public static final int MAX_PACKET_BODY_BYTES = 30000;
    /** Leave room for action, slot and Forge's signed-short NBT length. */
    private static final int MAX_NBT_COMPRESSED_BYTES = MAX_PACKET_BODY_BYTES - 1 - 4 - 2;
    private static final long MAX_NBT_BYTES = 512L * 1024L;

    public static final int ACTION_SCAN = 0;
    public static final int ACTION_SYNC = 1;
    public static final int ACTION_TELEPORT = 2;

    public int action;
    public int slot;
    public NBTTagCompound nbt;
    public int dimension;
    public int x;
    public int y;
    public int z;
    public boolean malformed;

    public PacketLinkScannerAction() {}

    public static PacketLinkScannerAction scan(int slot) {
        PacketLinkScannerAction packet = new PacketLinkScannerAction();
        packet.action = ACTION_SCAN;
        packet.slot = slot;
        return packet;
    }

    public static PacketLinkScannerAction sync(int slot, NBTTagCompound nbt) {
        PacketLinkScannerAction packet = new PacketLinkScannerAction();
        packet.action = ACTION_SYNC;
        packet.slot = slot;
        packet.nbt = nbt;
        return packet;
    }

    public static PacketLinkScannerAction teleport(int slot, int dimension, int x, int y, int z) {
        PacketLinkScannerAction packet = new PacketLinkScannerAction();
        packet.action = ACTION_TELEPORT;
        packet.slot = slot;
        packet.dimension = dimension;
        packet.x = x;
        packet.y = y;
        packet.z = z;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            validateAction();
            buf.writeByte(action);
            buf.writeInt(slot);
            if (action == ACTION_SYNC) {
                NetworkPacketCodec.writeTag(buf, nbt == null ? new NBTTagCompound() : nbt, MAX_NBT_COMPRESSED_BYTES);
            } else if (action == ACTION_TELEPORT) {
                buf.writeInt(dimension);
                buf.writeInt(x);
                buf.writeInt(y);
                buf.writeInt(z);
            }
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Link scanner action exceeds packet body limit");
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
        action = ACTION_SCAN;
        slot = 0;
        nbt = null;
        dimension = 0;
        x = 0;
        y = 0;
        z = 0;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Link scanner action exceeds packet body limit");
            }
            action = buf.readUnsignedByte();
            slot = buf.readInt();
            if (action == ACTION_SYNC) {
                nbt = NetworkPacketCodec.readTag(buf, MAX_NBT_COMPRESSED_BYTES, MAX_NBT_BYTES);
                if (nbt == null) {
                    nbt = new NBTTagCompound();
                }
            } else if (action == ACTION_TELEPORT) {
                dimension = buf.readInt();
                x = buf.readInt();
                y = buf.readInt();
                z = buf.readInt();
            } else if (action != ACTION_SCAN) {
                throw new IllegalArgumentException("Invalid link scanner action");
            }
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Link scanner action has trailing or oversized data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            action = ACTION_SCAN;
            slot = 0;
            nbt = null;
            dimension = 0;
            x = 0;
            y = 0;
            z = 0;
        }
    }

    private void validateAction() {
        if (action != ACTION_SCAN && action != ACTION_SYNC && action != ACTION_TELEPORT) {
            throw new IllegalArgumentException("Invalid link scanner action");
        }
    }
}
