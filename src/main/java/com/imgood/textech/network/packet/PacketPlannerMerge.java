package com.imgood.textech.network.packet;

import com.imgood.textech.items.PlannerMergeMode;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketPlannerMerge implements IMessage {

    public PlannerMergeMode mode;
    public boolean malformed;

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
        malformed = false;
        mode = null;
        try {
            if (buf.readableBytes() != 1) {
                throw new IllegalArgumentException("Invalid planner merge length");
            }
            int ordinal = buf.readUnsignedByte();
            PlannerMergeMode[] modes = PlannerMergeMode.values();
            if (ordinal >= modes.length) {
                throw new IllegalArgumentException("Invalid planner merge mode");
            }
            mode = modes[ordinal];
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Planner merge has trailing data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            mode = null;
        }
    }
}
