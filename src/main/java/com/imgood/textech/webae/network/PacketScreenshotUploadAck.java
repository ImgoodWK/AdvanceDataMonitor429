package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.client.screenshot.ClientScreenshotService;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** S→C terminal acknowledgement for a screenshot upload. Packet ID 52. */
public final class PacketScreenshotUploadAck implements IMessage {

    private static final int MAX_TEXT_BYTES = 512;
    private static final int MAX_PACKET_BYTES = 30_000;
    public String uploadId = "";
    public boolean success;
    public String message = "";
    public String attachmentId = "";
    public boolean malformed;

    public PacketScreenshotUploadAck() {}

    public PacketScreenshotUploadAck(String uploadId, boolean success, String message, String attachmentId) {
        this.uploadId = safe(uploadId);
        this.success = success;
        this.message = safe(message);
        this.attachmentId = safe(attachmentId);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            write(buf, uploadId, 64);
            buf.writeBoolean(success);
            write(buf, message, MAX_TEXT_BYTES);
            write(buf, attachmentId, 64);
            requirePacketBudget(buf, start);
        } catch (RuntimeException e) {
            buf.writerIndex(start);
            throw e;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new IllegalArgumentException("Screenshot acknowledgement exceeds packet budget");
            }
            uploadId = read(buf, 64);
            success = buf.readBoolean();
            message = read(buf, MAX_TEXT_BYTES);
            attachmentId = read(buf, 64);
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Screenshot acknowledgement has trailing bytes");
            }
        } catch (RuntimeException e) {
            malformed = true;
            uploadId = "";
            message = "";
            attachmentId = "";
        }
    }

    private static void write(ByteBuf buf, String value, int max) {
        byte[] bytes = safe(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > max) {
            throw new IllegalArgumentException("Screenshot acknowledgement exceeds packet limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String read(ByteBuf buf, int max) {
        return NetworkPacketCodec.readUtf8(buf, max);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Screenshot acknowledgement exceeds packet budget");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @SideOnly(Side.CLIENT)
    public static final class Handler implements IMessageHandler<PacketScreenshotUploadAck, IMessage> {

        @Override
        public IMessage onMessage(final PacketScreenshotUploadAck message, MessageContext ctx) {
            if (message == null || message.malformed) return null;
            net.minecraft.client.Minecraft.getMinecraft()
                .func_152344_a(new Runnable() {

                    @Override
                    public void run() {
                        ClientScreenshotService.instance()
                            .onUploadAck(message.uploadId, message.success, message.message, message.attachmentId);
                    }
                });
            return null;
        }
    }
}
