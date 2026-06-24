package com.imgood.advancedatamonitor.network.packet;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketLinkScannerAction implements IMessage {

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
        buf.writeByte(action);
        buf.writeInt(slot);
        if (action == ACTION_SYNC) {
            ByteBufUtils.writeTag(buf, nbt == null ? new NBTTagCompound() : nbt);
        } else if (action == ACTION_TELEPORT) {
            buf.writeInt(dimension);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        slot = buf.readInt();
        if (action == ACTION_SYNC) {
            nbt = ByteBufUtils.readTag(buf);
            if (nbt == null) {
                nbt = new NBTTagCompound();
            }
        } else if (action == ACTION_TELEPORT) {
            dimension = buf.readInt();
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
        }
    }
}
