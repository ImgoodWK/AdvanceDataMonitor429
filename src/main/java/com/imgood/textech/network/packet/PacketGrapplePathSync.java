package com.imgood.textech.network.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.textech.client.GrappleClientRouteCache;
import com.imgood.textech.items.GrappleRouteEntry;
import com.imgood.textech.utils.BlockPos;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class PacketGrapplePathSync implements IMessage {

    public static final byte KIND_ROUTES = 0;
    public static final byte KIND_BUFFER = 1;

    public byte kind = KIND_ROUTES;
    public final List<GrappleRouteEntry> routes = new ArrayList<GrappleRouteEntry>();
    public final List<BlockPos> buffer = new ArrayList<BlockPos>();
    public String messageKey = "";

    public PacketGrapplePathSync() {}

    public static PacketGrapplePathSync routes(List<GrappleRouteEntry> routeList) {
        PacketGrapplePathSync packet = new PacketGrapplePathSync();
        packet.kind = KIND_ROUTES;
        if (routeList != null) {
            packet.routes.addAll(routeList);
        }
        return packet;
    }

    public static PacketGrapplePathSync buffer(List<BlockPos> nodes) {
        PacketGrapplePathSync packet = new PacketGrapplePathSync();
        packet.kind = KIND_BUFFER;
        if (nodes != null) {
            packet.buffer.addAll(nodes);
        }
        return packet;
    }

    public static PacketGrapplePathSync withMessage(String key) {
        PacketGrapplePathSync packet = new PacketGrapplePathSync();
        packet.messageKey = key == null ? "" : key;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        buf.writeShort(routes.size());
        for (GrappleRouteEntry route : routes) {
            writeString(buf, route.routeId);
            writeString(buf, route.name);
            buf.writeInt(route.dimension);
            buf.writeLong(route.createdAt);
            buf.writeShort(route.nodes.size());
            for (BlockPos node : route.nodes) {
                buf.writeInt(node.getX());
                buf.writeInt(node.getY());
                buf.writeInt(node.getZ());
            }
        }
        buf.writeShort(buffer.size());
        for (BlockPos node : buffer) {
            buf.writeInt(node.getX());
            buf.writeInt(node.getY());
            buf.writeInt(node.getZ());
        }
        writeString(buf, messageKey);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
        int routeCount = buf.readShort();
        routes.clear();
        for (int i = 0; i < routeCount; i++) {
            GrappleRouteEntry route = new GrappleRouteEntry();
            route.routeId = readString(buf);
            route.name = readString(buf);
            route.dimension = buf.readInt();
            route.createdAt = buf.readLong();
            int nodeCount = buf.readShort();
            for (int j = 0; j < nodeCount; j++) {
                route.nodes.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
            }
            routes.add(route);
        }
        int bufferCount = buf.readShort();
        buffer.clear();
        for (int i = 0; i < bufferCount; i++) {
            buffer.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        messageKey = readString(buf);
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

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketGrapplePathSync, IMessage> {

        @Override
        public IMessage onMessage(PacketGrapplePathSync message, MessageContext ctx) {
            GrappleClientRouteCache.apply(message);
            if (message.messageKey != null && !message.messageKey.isEmpty()) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new ChatComponentTranslation(message.messageKey));
                }
            }
            return null;
        }
    }
}
