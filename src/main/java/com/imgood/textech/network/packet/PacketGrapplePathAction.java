package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.textech.handler.GrapplePathStore;
import com.imgood.textech.handler.GrapplePlanningSession;
import com.imgood.textech.handler.GrappleRouteSync;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.items.GrappleHookMode;
import com.imgood.textech.items.ItemGrappleHook;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketGrapplePathAction implements IMessage {

    public static final byte REQUEST_SYNC = 0;
    public static final byte SAVE_ROUTE = 1;
    public static final byte DELETE_ROUTE = 2;
    public static final byte RENAME_ROUTE = 3;
    public static final byte RESET_BUFFER = 4;
    public static final byte SET_MODE = 5;
    public static final byte DISCARD_BUFFER = 6;

    private byte action;
    private String routeId = "";
    private String name = "";
    private byte modeId;

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
        buf.writeByte(action);
        writeString(buf, routeId);
        writeString(buf, name);
        buf.writeByte(modeId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        routeId = readString(buf);
        name = readString(buf);
        modeId = buf.readByte();
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

    public static class ServerHandler implements IMessageHandler<PacketGrapplePathAction, IMessage> {

        @Override
        public IMessage onMessage(final PacketGrapplePathAction message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    handleServer(player, message);
                }
            });
            return null;
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
