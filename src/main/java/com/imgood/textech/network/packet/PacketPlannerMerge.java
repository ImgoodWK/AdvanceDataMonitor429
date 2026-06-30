package com.imgood.textech.network.packet;

import com.imgood.textech.items.PlannerMergeMode;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketPlannerMerge implements IMessage {

    public PlannerMergeMode mode;

    public PacketPlannerMerge() {}

    public PacketPlannerMerge(PlannerMergeMode mode) {
        this.mode = mode;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode == null ? PlannerMergeMode.BY_TIME.ordinal() : mode.ordinal());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        mode = PlannerMergeMode.values()[ordinal];
    }
}
