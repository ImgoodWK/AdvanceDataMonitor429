package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.textech.handler.GrapplePathStore;
import com.imgood.textech.handler.GrapplePlanningSession;
import com.imgood.textech.handler.GrappleRouteSync;
import com.imgood.textech.items.GrappleHookMode;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketGrapplePathAction implements IMessage {

    public static final byte REQUEST_SYNC = 0;
    public static final byte SAVE_ROUTE = 1;
    public static final byte DELETE_ROUTE = 2;
    public static final byte RENAME_ROUTE = 3;
    public static final byte RESET_BUFFER = 4;
    public static final byte SET_MODE = 5;
    public static final byte DISCARD_BUFFER = 6;
    private static final int MAX_ROUTE_ID_BYTES = 128;
    private static final int MAX_NAME_BYTES = 256;
    public static final int MAX_PACKET_BODY_BYTES = 30000;

    private byte action;
    private String routeId = "";
    private String name = "";
    private byte modeId;
    public boolean malformed;

    public PacketGrapplePathAction() {}

    public static PacketGrapplePathAction requestSync() {
        PacketGrapplePathAction packet = new PacketGrapplePathAction();
        packet.action = REQUEST_SYNC;
        return packet;
    }

    public static PacketGrapplePathAction saveRoute(String name) {
        PacketGrapplePathAction packet = new PacketGrapplePathAction();
        packet.action = SAVE_ROUTE;
        packet.name = name == null ? "" : name;
        return packet;
    }

    public static PacketGrapplePathAction deleteRoute(String routeId) {
        PacketGrapplePathAction packet = new PacketGrapplePathAction();
        packet.action = DELETE_ROUTE;
        packet.routeId = routeId == null ? "" : routeId;
        return packet;
    }

    public static PacketGrapplePathAction renameRoute(String routeId, String name) {
        PacketGrapplePathAction packet = new PacketGrapplePathAction();
        packet.action = RENAME_ROUTE;
        packet.routeId = routeId == null ? "" : routeId;
        packet.name = name == null ? "" : name;
        return packet;
    }

    public static PacketGrapplePathAction resetBuffer() {
        PacketGrapplePathAction packet = new PacketGrapplePathAction();
        packet.action = RESET_BUFFER;
        return packet;
    }

    public static PacketGrapplePathAction discardBuffer() {
        PacketGrapplePathAction packet = new PacketGrapplePathAction();
        packet.action = DISCARD_BUFFER;
        return packet;
    }

    public static PacketGrapplePathAction setMode(GrappleHookMode mode) {
        PacketGrapplePathAction packet = new PacketGrapplePathAction();
        packet.action = SET_MODE;
        packet.modeId = (byte) mode.getId();
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            validateShape();
            buf.writeByte(action);
            writeString(buf, routeId, MAX_ROUTE_ID_BYTES);
            writeString(buf, name, MAX_NAME_BYTES);
            buf.writeByte(modeId);
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Grapple path action exceeds packet body limit");
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
        action = REQUEST_SYNC;
        routeId = "";
        name = "";
        modeId = 0;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Grapple path action exceeds packet body limit");
            }
            action = buf.readByte();
            routeId = readString(buf, MAX_ROUTE_ID_BYTES);
            name = readString(buf, MAX_NAME_BYTES);
            modeId = buf.readByte();
            validateShape();
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Grapple path action has trailing or oversized data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            action = REQUEST_SYNC;
            routeId = "";
            name = "";
            modeId = 0;
        }
    }

    private void validateShape() {
        if (action < REQUEST_SYNC || action > DISCARD_BUFFER) {
            throw new IllegalArgumentException("Invalid grapple path action");
        }
        if (modeId < GrappleHookMode.QUEUE.getId() || modeId > GrappleHookMode.PATH.getId()) {
            throw new IllegalArgumentException("Invalid grapple path mode");
        }
    }

    private static void writeString(ByteBuf buf, String value, int maxBytes) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > maxBytes || bytes.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Grapple route text exceeds packet framing");
        }
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf, int maxBytes) {
        return NetworkPacketCodec.readUnsignedShortUtf8(buf, maxBytes);
    }

    public static class ServerHandler implements IMessageHandler<PacketGrapplePathAction, IMessage> {

        @Override
        public IMessage onMessage(final PacketGrapplePathAction message, MessageContext ctx) {
            if (message == null || message.malformed) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    handleServer(player, message);
                }
            });
        }
    }

    private static void handleServer(EntityPlayerMP player, PacketGrapplePathAction message) {
        if (player == null) {
            return;
        }
        if (message.action == REQUEST_SYNC) {
            GrappleRouteSync.syncAll(player);
        } else if (message.action == SAVE_ROUTE) {
            String routeId = GrapplePlanningSession.saveBuffer(player, message.name);
            if (routeId != null) {
                player.addChatMessage(new ChatComponentTranslation("adm.grapple.route_saved", message.name));
                GrappleRouteSync.syncAll(player);
            } else {
                player.addChatMessage(new ChatComponentTranslation("adm.grapple.route_save_failed"));
            }
        } else if (message.action == DELETE_ROUTE) {
            if (GrapplePathStore.instance()
                .deleteRoute(player, message.routeId)) {
                GrappleRouteSync.syncAll(player);
            }
        } else if (message.action == RENAME_ROUTE) {
            if (GrapplePathStore.instance()
                .renameRoute(player, message.routeId, message.name)) {
                GrappleRouteSync.syncAll(player);
            }
        } else if (message.action == RESET_BUFFER) {
            GrapplePlanningSession.resetBuffer(player);
            GrappleRouteSync.syncBuffer(player);
        } else if (message.action == DISCARD_BUFFER) {
            GrapplePlanningSession.resetBuffer(player);
            GrappleRouteSync.syncBuffer(player);
        } else if (message.action == SET_MODE) {
            applyMode(player, GrappleHookMode.fromId(message.modeId));
        }
    }

    private static void applyMode(EntityPlayerMP player, GrappleHookMode mode) {
        if (mode == null) {
            mode = GrappleHookMode.QUEUE;
        }
        ItemGrappleHook.setHookModeOnHeldOrAny(player, mode);
        GrappleRouteSync.syncAll(player);
    }
}
