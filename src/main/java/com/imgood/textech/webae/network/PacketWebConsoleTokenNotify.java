package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;

import com.imgood.textech.client.WebConsoleClientChat;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C: deliver Web console token UI on the client (click-to-copy / open URL).
 * Server chat click events are often stripped in GTNH production packs; client-local
 * messages preserve interactivity.
 */
public class PacketWebConsoleTokenNotify implements IMessage {

    public static final byte KIND_ISSUE = 0;
    public static final byte KIND_CLIP = 1;
    public static final byte KIND_LOGIN = 2;
    public static final byte KIND_ONBOARDING = 3;

    public byte kind;
    public String token;
    public int port;
    public String bindAddress;

    public PacketWebConsoleTokenNotify() {}

    public PacketWebConsoleTokenNotify(byte kind, String token, int port, String bindAddress) {
        this.kind = kind;
        this.token = token;
        this.port = port;
        this.bindAddress = bindAddress != null ? bindAddress : "127.0.0.1";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        writeString(buf, token);
        buf.writeInt(port);
        writeString(buf, bindAddress);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
        token = readString(buf);
        port = buf.readInt();
        bindAddress = readString(buf);
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
        buf.writeInt(bytes.length);
        if (bytes.length > 0) {
            buf.writeBytes(bytes);
        }
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readInt();
        if (len <= 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebConsoleTokenNotify, IMessage> {

        @Override
        public IMessage onMessage(PacketWebConsoleTokenNotify message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) {
                return null;
            }
            if (message.kind == KIND_CLIP) {
                WebConsoleClientChat.copyToken(message.token);
            } else if (message.kind == KIND_LOGIN) {
                WebConsoleClientChat.showLoginCode(message.token, message.port, message.bindAddress);
            } else if (message.kind == KIND_ONBOARDING) {
                WebConsoleClientChat.showOnboarding(message.port, message.bindAddress);
            } else {
                WebConsoleClientChat.showIssue(message.token, message.port, message.bindAddress);
                if (mc.thePlayer != null) {
                    com.imgood.textech.client.worldmap.WorldMapSnapshotDownloadHandler.instance()
                        .scheduleSyncForOwner(
                            mc.thePlayer.getUniqueID()
                                .toString(),
                            0);
                }
            }
            return null;
        }
    }
}
