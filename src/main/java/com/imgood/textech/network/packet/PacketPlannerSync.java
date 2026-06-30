package com.imgood.textech.network.packet;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketPlannerSync implements IMessage {

    public int slot;
    public NBTTagCompound nbt;

    public PacketPlannerSync() {}

    public PacketPlannerSync(int slot, NBTTagCompound nbt) {
        this.slot = slot;
        this.nbt = nbt;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        ByteBufUtils.writeTag(buf, nbt == null ? new NBTTagCompound() : nbt);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        nbt = ByteBufUtils.readTag(buf);
        if (nbt == null) nbt = new NBTTagCompound();
    }
}
