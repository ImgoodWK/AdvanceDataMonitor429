package com.imgood.textech.network.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.textech.handler.GrapplePlayerState;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.utils.BlockPos;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketGrappleAction implements IMessage {

    public static final byte DETACH = 0;
    public static final byte TRAVEL = 1;
    public static final byte ATTACH = 2;
    public static final byte TRAVEL_PATH = 3;

    private byte action;
    private int targetX;
    private int targetY;
    private int targetZ;
    private String routeId = "";
    private final List<BlockPos> pathNodes = new ArrayList<BlockPos>();

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

    public static PacketGrappleAction travelPath(String routeId, List<BlockPos> nodes) {
        PacketGrappleAction packet = new PacketGrappleAction();
        packet.action = TRAVEL_PATH;
        packet.routeId = routeId == null ? "" : routeId;
        if (nodes != null) {
            packet.pathNodes.addAll(nodes);
        }
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        writeString(buf, routeId);
        buf.writeShort(pathNodes.size());
        for (BlockPos node : pathNodes) {
            buf.writeInt(node.getX());
            buf.writeInt(node.getY());
            buf.writeInt(node.getZ());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        targetX = buf.readInt();
        targetY = buf.readInt();
        targetZ = buf.readInt();
        routeId = readString(buf);
        int count = buf.readShort();
        pathNodes.clear();
        for (int i = 0; i < count; i++) {
            pathNodes.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(java.nio.charset.Charset.forName("UTF-8"));
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readShort();
        if (len <= 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.Charset.forName("UTF-8"));
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
                    } else if (message.action == TRAVEL_PATH) {
                        if (!GrapplePlayerState.travelPath(player, message.pathNodes)) {
                            if (message.pathNodes.size() >= 2) {
                                BlockPos fallback = message.pathNodes.get(1);
                                GrapplePlayerState.travelTo(player, fallback.getX(), fallback.getY(), fallback.getZ());
                                player.addChatMessage(new ChatComponentTranslation("adm.grapple.path_degraded"));
                            }
                        }
                    }
                }
            });
            return null;
        }
    }
}
