package com.imgood.textech.network.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.textech.handler.GrapplePlayerState;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketGrappleAction implements IMessage {

    public static final byte DETACH = 0;
    public static final byte TRAVEL = 1;
    public static final byte ATTACH = 2;
    public static final byte TRAVEL_PATH = 3;
    private static final int MAX_ROUTE_ID_BYTES = 128;
    private static final int MAX_PATH_NODES = 512;
    public static final int MAX_PACKET_BODY_BYTES = 30000;

    private byte action;
    private int targetX;
    private int targetY;
    private int targetZ;
    private String routeId = "";
    private final List<BlockPos> pathNodes = new ArrayList<BlockPos>();
    public boolean malformed;

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
        int start = buf.writerIndex();
        try {
            validateShape();
            buf.writeByte(action);
            buf.writeInt(targetX);
            buf.writeInt(targetY);
            buf.writeInt(targetZ);
            writeString(buf, routeId);
            buf.writeShort(pathNodes.size());
            for (BlockPos node : pathNodes) {
                if (node == null) {
                    throw new IllegalArgumentException("Grapple path contains a null node");
                }
                buf.writeInt(node.getX());
                buf.writeInt(node.getY());
                buf.writeInt(node.getZ());
            }
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Grapple action exceeds packet body limit");
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
        action = DETACH;
        targetX = 0;
        targetY = 0;
        targetZ = 0;
        routeId = "";
        pathNodes.clear();
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Grapple action exceeds packet body limit");
            }
            action = buf.readByte();
            targetX = buf.readInt();
            targetY = buf.readInt();
            targetZ = buf.readInt();
            routeId = readString(buf, MAX_ROUTE_ID_BYTES);
            int count = buf.readUnsignedShort();
            if (count > MAX_PATH_NODES || count > buf.readableBytes() / 12) {
                throw new IllegalArgumentException("Invalid grapple path node count");
            }
            for (int i = 0; i < count; i++) {
                pathNodes.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
            }
            validateShape();
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Grapple action has trailing or oversized data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            action = DETACH;
            targetX = 0;
            targetY = 0;
            targetZ = 0;
            routeId = "";
            pathNodes.clear();
        }
    }

    private void validateShape() {
        if (action < DETACH || action > TRAVEL_PATH || pathNodes.size() > MAX_PATH_NODES) {
            throw new IllegalArgumentException("Invalid grapple action");
        }
        if (action != TRAVEL_PATH && ((!routeId.isEmpty()) || !pathNodes.isEmpty())) {
            throw new IllegalArgumentException("Non-path grapple action contains path data");
        }
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_ROUTE_ID_BYTES || bytes.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Grapple route id exceeds packet limit");
        }
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf, int maxBytes) {
        return NetworkPacketCodec.readUnsignedShortUtf8(buf, maxBytes);
    }

    public static class Handler implements IMessageHandler<PacketGrappleAction, IMessage> {

        @Override
        public IMessage onMessage(final PacketGrappleAction message, MessageContext ctx) {
            if (message == null || message.malformed) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
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
        }
    }
}
