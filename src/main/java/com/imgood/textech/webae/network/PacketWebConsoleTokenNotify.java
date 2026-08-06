package com.imgood.textech.webae.network;

import net.minecraft.client.Minecraft;

import com.imgood.textech.client.WebConsoleClientChat;
import com.imgood.textech.utils.NetworkPacketCodec;

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
    private boolean valid = true;

    private static final int MAX_TOKEN_BYTES = 512;
    private static final int MAX_BIND_ADDRESS_BYTES = 128;
    private static final int MAX_PACKET_BYTES = 30_000;

    public PacketWebConsoleTokenNotify() {}

    public PacketWebConsoleTokenNotify(byte kind, String token, int port, String bindAddress) {
        this.kind = kind;
        this.token = token;
        this.port = port;
        this.bindAddress = bindAddress != null ? bindAddress : "127.0.0.1";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            if (!isValidKind(kind)) {
                throw new IllegalArgumentException("Invalid Web console notification kind");
            }
            buf.writeByte(kind);
            writeString(buf, token, MAX_TOKEN_BYTES);
            buf.writeInt(port);
            writeString(buf, bindAddress, MAX_BIND_ADDRESS_BYTES);
            requirePacketBudget(buf, start);
        } catch (RuntimeException e) {
            buf.writerIndex(start);
            throw e;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        valid = true;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Web console notification exceeds packet budget");
            }
            kind = buf.readByte();
            if (!isValidKind(kind)) {
                throw new IllegalArgumentException("Invalid Web console notification kind");
            }
            token = NetworkPacketCodec.readUtf8(buf, MAX_TOKEN_BYTES);
            port = buf.readInt();
            bindAddress = NetworkPacketCodec.readUtf8(buf, MAX_BIND_ADDRESS_BYTES);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Web console notification has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            token = "";
            bindAddress = "";
        }
    }

    private static boolean isValidKind(byte value) {
        return value == KIND_ISSUE || value == KIND_CLIP || value == KIND_LOGIN || value == KIND_ONBOARDING;
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Web console notification exceeds packet budget");
        }
    }

    private static void writeString(ByteBuf buf, String value, int maxBytes) {
        byte[] bytes = value != null ? value.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Web console notification field exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        if (bytes.length > 0) {
            buf.writeBytes(bytes);
        }
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketWebConsoleTokenNotify, IMessage> {

        @Override
        public IMessage onMessage(PacketWebConsoleTokenNotify message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
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
