package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import com.imgood.textech.client.screenshot.ClientScreenshotService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** S→C terminal acknowledgement for a screenshot upload. Packet ID 52. */
public final class PacketScreenshotUploadAck implements IMessage {

    private static final int MAX_TEXT_BYTES = 512;
    public String uploadId = "";
    public boolean success;
    public String message = "";
    public String attachmentId = "";

    public PacketScreenshotUploadAck() {}

    public PacketScreenshotUploadAck(String uploadId, boolean success, String message, String attachmentId) {
        this.uploadId = safe(uploadId);
        this.success = success;
        this.message = safe(message);
        this.attachmentId = safe(attachmentId);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        write(buf, uploadId, 64);
        buf.writeBoolean(success);
        write(buf, message, MAX_TEXT_BYTES);
        write(buf, attachmentId, 64);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        uploadId = read(buf, 64);
        success = buf.readBoolean();
        message = read(buf, MAX_TEXT_BYTES);
        attachmentId = read(buf, 64);
    }

    private static void write(ByteBuf buf, String value, int max) {
        byte[] bytes = safe(value).getBytes(StandardCharsets.UTF_8);
        int length = Math.min(max, bytes.length);
        buf.writeInt(length);
        buf.writeBytes(bytes, 0, length);
    }

    private static String read(ByteBuf buf, int max) {
        int length = buf.readInt();
        if (length <= 0) return "";
        if (length > max || length > buf.readableBytes()) {
            buf.skipBytes(buf.readableBytes());
            return "";
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @SideOnly(Side.CLIENT)
    public static final class Handler implements IMessageHandler<PacketScreenshotUploadAck, IMessage> {

        @Override
        public IMessage onMessage(final PacketScreenshotUploadAck message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().func_152344_a(new Runnable() {

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
