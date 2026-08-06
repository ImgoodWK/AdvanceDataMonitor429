package com.imgood.textech.network.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.textech.client.GrappleClientRouteCache;
import com.imgood.textech.items.GrappleRouteEntry;
import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketGrapplePathSync implements IMessage {

    public static final int MAX_PACKET_BODY_BYTES = 30000;
    public static final byte KIND_ROUTES = 0;
    public static final byte KIND_BUFFER = 1;
    /** Matches the largest configured saved-route count. */
    public static final int MAX_TOTAL_ROUTES = 512;
    private static final int MAX_ROUTE_BATCHES = MAX_TOTAL_ROUTES;
    private static final int MAX_NODES_PER_ROUTE = 512;
    private static final int MAX_BUFFER_NODES = 2048;
    private static final int MAX_ROUTE_TEXT_BYTES = 256;
    public static final byte KIND_ROUTES_BATCH = 2;

    public byte kind = KIND_ROUTES;
    public final List<GrappleRouteEntry> routes = new ArrayList<GrappleRouteEntry>();
    public final List<BlockPos> buffer = new ArrayList<BlockPos>();
    public String messageKey = "";
    public int batchIndex;
    public int batchCount;
    public boolean malformed;

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

    /**
     * Build a complete route snapshot before any packet is handed to Forge.
     * The legacy packet remains the default for compatibility; batching is
     * used only when that complete legacy snapshot exceeds the body budget.
     */
    public static List<PacketGrapplePathSync> routePackets(List<GrappleRouteEntry> routeList) {
        List<GrappleRouteEntry> source = routeList == null ? Collections.<GrappleRouteEntry>emptyList() : routeList;
        if (source.size() > MAX_TOTAL_ROUTES) {
            return Collections.emptyList();
        }

        PacketGrapplePathSync legacy = routes(source);
        if (legacy.fitsPacketBudget()) {
            return Collections.singletonList(legacy);
        }

        List<PacketGrapplePathSync> batches = new ArrayList<PacketGrapplePathSync>();
        PacketGrapplePathSync current = newBatch();
        for (GrappleRouteEntry route : source) {
            current.routes.add(route);
            if (!current.fitsPacketBudget()) {
                current.routes.remove(current.routes.size() - 1);
                if (current.routes.isEmpty()) {
                    return Collections.emptyList();
                }
                batches.add(current);
                current = newBatch();
                current.routes.add(route);
                if (!current.fitsPacketBudget()) {
                    return Collections.emptyList();
                }
            }
        }
        if (!current.routes.isEmpty() || source.isEmpty()) {
            if (!current.fitsPacketBudget()) {
                return Collections.emptyList();
            }
            batches.add(current);
        }
        if (batches.size() > MAX_ROUTE_BATCHES) {
            return Collections.emptyList();
        }
        for (int i = 0; i < batches.size(); i++) {
            PacketGrapplePathSync packet = batches.get(i);
            packet.batchIndex = i;
            packet.batchCount = batches.size();
            if (!packet.fitsPacketBudget()) {
                return Collections.emptyList();
            }
        }
        return batches;
    }

    private static PacketGrapplePathSync newBatch() {
        PacketGrapplePathSync packet = new PacketGrapplePathSync();
        packet.kind = KIND_ROUTES_BATCH;
        packet.batchIndex = 0;
        packet.batchCount = 1;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            validateShape();
            buf.writeByte(kind);
            if (kind == KIND_ROUTES_BATCH) {
                buf.writeShort(batchIndex);
                buf.writeShort(batchCount);
            }
            buf.writeShort(routes.size());
            for (GrappleRouteEntry route : routes) {
                writeRoute(buf, route);
            }
            buf.writeShort(buffer.size());
            for (BlockPos node : buffer) {
                if (node == null) {
                    throw new IllegalArgumentException("Grapple buffer contains a null node");
                }
                buf.writeInt(node.getX());
                buf.writeInt(node.getY());
                buf.writeInt(node.getZ());
            }
            writeString(buf, messageKey);
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Grapple route sync exceeds packet body limit");
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

    private void validateShape() {
        if (kind != KIND_ROUTES && kind != KIND_BUFFER && kind != KIND_ROUTES_BATCH) {
            throw new IllegalArgumentException("Invalid grapple route sync kind");
        }
        if (routes.size() > MAX_TOTAL_ROUTES || buffer.size() > MAX_BUFFER_NODES) {
            throw new IllegalArgumentException("Grapple route sync exceeds packet limit");
        }
        if (kind == KIND_ROUTES_BATCH) {
            if (batchCount < 1 || batchCount > MAX_ROUTE_BATCHES
                || batchIndex < 0
                || batchIndex >= batchCount
                || routes.isEmpty()
                || !buffer.isEmpty()
                || (messageKey != null && !messageKey.isEmpty())) {
                throw new IllegalArgumentException("Invalid grapple route batch metadata");
            }
        } else {
            if (batchIndex != 0 || batchCount != 0) {
                throw new IllegalArgumentException("Legacy grapple route packet has batch metadata");
            }
            if (kind == KIND_ROUTES && !buffer.isEmpty()) {
                throw new IllegalArgumentException("Route packet contains a buffer payload");
            }
            if (kind == KIND_BUFFER && (!routes.isEmpty() || (messageKey != null && !messageKey.isEmpty()))) {
                throw new IllegalArgumentException("Buffer packet contains route or message payloads");
            }
        }
        for (GrappleRouteEntry route : routes) {
            validateRoute(route);
        }
        for (BlockPos node : buffer) {
            if (node == null) {
                throw new IllegalArgumentException("Grapple buffer contains a null node");
            }
        }
    }

    private static void validateRoute(GrappleRouteEntry route) {
        if (route == null || route.nodes == null || route.nodes.size() > MAX_NODES_PER_ROUTE) {
            throw new IllegalArgumentException("Grapple route nodes exceed packet limit");
        }
        for (BlockPos node : route.nodes) {
            if (node == null) {
                throw new IllegalArgumentException("Grapple route contains a null node");
            }
        }
    }

    private static void writeRoute(ByteBuf buf, GrappleRouteEntry route) {
        validateRoute(route);
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

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        kind = KIND_ROUTES;
        routes.clear();
        buffer.clear();
        messageKey = "";
        batchIndex = 0;
        batchCount = 0;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Grapple route sync exceeds packet body limit");
            }
            kind = buf.readByte();
            if (kind != KIND_ROUTES && kind != KIND_BUFFER && kind != KIND_ROUTES_BATCH) {
                throw new IllegalArgumentException("Invalid grapple route sync kind");
            }
            if (kind == KIND_ROUTES_BATCH) {
                batchIndex = buf.readUnsignedShort();
                batchCount = buf.readUnsignedShort();
                if (batchCount < 1 || batchCount > MAX_ROUTE_BATCHES || batchIndex >= batchCount) {
                    throw new IllegalArgumentException("Invalid grapple route batch metadata");
                }
            }
            int routeCount = buf.readUnsignedShort();
            if (routeCount > MAX_TOTAL_ROUTES || (kind == KIND_ROUTES_BATCH && routeCount == 0)
                || (kind == KIND_BUFFER && routeCount != 0)
                || routeCount > buf.readableBytes() / 18) {
                throw new IllegalArgumentException("Invalid grapple route count");
            }
            for (int i = 0; i < routeCount; i++) {
                GrappleRouteEntry route = new GrappleRouteEntry();
                route.routeId = readString(buf);
                route.name = readString(buf);
                route.dimension = buf.readInt();
                route.createdAt = buf.readLong();
                int nodeCount = buf.readUnsignedShort();
                if (nodeCount > MAX_NODES_PER_ROUTE || nodeCount > buf.readableBytes() / 12) {
                    throw new IllegalArgumentException("Invalid grapple route node count");
                }
                for (int j = 0; j < nodeCount; j++) {
                    route.nodes.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
                }
                routes.add(route);
            }
            int bufferCount = buf.readUnsignedShort();
            if (bufferCount > MAX_BUFFER_NODES || bufferCount > buf.readableBytes() / 12
                || (kind != KIND_BUFFER && bufferCount != 0)) {
                throw new IllegalArgumentException("Invalid grapple buffer node count");
            }
            for (int i = 0; i < bufferCount; i++) {
                buffer.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
            }
            messageKey = readString(buf);
            if (kind == KIND_ROUTES_BATCH && !messageKey.isEmpty()) {
                throw new IllegalArgumentException("Grapple route batch contains a message");
            }
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Grapple route sync has trailing or oversized data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            kind = KIND_ROUTES;
            routes.clear();
            buffer.clear();
            messageKey = "";
            batchIndex = 0;
            batchCount = 0;
        }
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (value != null && !NetworkPacketCodec.decodeUtf8(bytes)
            .equals(value)) {
            throw new IllegalArgumentException("Grapple route text is not valid UTF-8");
        }
        if (bytes.length > MAX_ROUTE_TEXT_BYTES || bytes.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Grapple route text exceeds packet limit");
        }
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        return NetworkPacketCodec.readUnsignedShortUtf8(buf, MAX_ROUTE_TEXT_BYTES);
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketGrapplePathSync, IMessage> {

        @Override
        public IMessage onMessage(PacketGrapplePathSync message, MessageContext ctx) {
            if (message == null || message.malformed) {
                return null;
            }
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
