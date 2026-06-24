package com.imgood.advancedatamonitor.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.advancedatamonitor.handler.GrapplePlayerState;
import com.imgood.advancedatamonitor.handler.HandlerTick;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketGrappleAction implements IMessage {

    public static final byte DETACH = 0;
    public static final byte TRAVEL = 1;
    public static final byte ATTACH = 2;

    private byte action;
    private int targetX;
    private int targetY;
    private int targetZ;

    public PacketGrappleAction() {}

    public static PacketGrappleAction detach() {
        PacketGrappleAction packet = new PacketGrappleAction();
        packet.action = DETACH;
        return packet;
    }

    public static PacketGrappleAction travel(int x, int y, int z) {
        PacketGrappleAction packet = new PacketGrappleAction();
        packet.action = TRAVEL;
        packet.targetX = x;
        packet.targetY = y;
        packet.targetZ = z;
        return packet;
    }

    public static PacketGrappleAction attach(int x, int y, int z) {
        PacketGrappleAction packet = new PacketGrappleAction();
        packet.action = ATTACH;
        packet.targetX = x;
        packet.targetY = y;
        packet.targetZ = z;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        targetX = buf.readInt();
        targetY = buf.readInt();
        targetZ = buf.readInt();
    }

    public static class Handler implements IMessageHandler<PacketGrappleAction, IMessage> {

        @Override
        public IMessage onMessage(final PacketGrappleAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    if (player == null) {
                        return;
                    }
                    if (message.action == DETACH) {
                        GrapplePlayerState.detach(player);
                        return;
                    }
                    if (!ItemGrappleHook.isHoldingHook(player)) {
                        return;
                    }
                    if (message.action == TRAVEL) {
                        GrapplePlayerState.travelTo(player, message.targetX, message.targetY, message.targetZ);
                    } else if (message.action == ATTACH) {
                        GrapplePlayerState.attach(
                            player,
                            player.worldObj.provider.dimensionId,
                            message.targetX,
                            message.targetY,
                            message.targetZ);
                    }
                }
            });
            return null;
        }
    }
}
