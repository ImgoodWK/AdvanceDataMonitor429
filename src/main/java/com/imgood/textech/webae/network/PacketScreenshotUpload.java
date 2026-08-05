package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.webae.screenshot.ScreenshotUploadService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C→S bounded screenshot upload chunk. Metadata is transmitted only in chunk zero. Packet ID 51. */
public final class PacketScreenshotUpload implements IMessage {

    public static final int MAX_CHUNK_BYTES = 24 * 1024;
    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_ID_BYTES = 64;
    private static final int MAX_DESTINATION_BYTES = 16;
    private static final int MAX_TARGET_TYPE_BYTES = 16;
    private static final int MAX_TARGET_BYTES = 192;
    private static final int MAX_CAPTION_BYTES = 768;
    private static final int MAX_FILE_NAME_BYTES = 192;

    public String uploadId = "";
    public int chunkIndex;
    public int totalChunks;
    public int totalBytes;
    public String destination = "";
    public String targetType = "";
    public String targetId = "";
    public String caption = "";
    public String fileName = "";
    public int width;
    public int height;
    public byte[] chunk = new byte[0];
    public boolean malformed;

    public PacketScreenshotUpload() {}

    public PacketScreenshotUpload(String uploadId, int chunkIndex, int totalChunks, int totalBytes, String destination,
        String targetType, String targetId, String caption, String fileName, int width, int height, byte[] chunk) {
        this.uploadId = safe(uploadId);
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.totalBytes = totalBytes;
        this.destination = safe(destination);
        this.targetType = safe(targetType);
        this.targetId = safe(targetId);
        this.caption = safe(caption);
        this.fileName = safe(fileName);
        this.width = width;
        this.height = height;
        this.chunk = chunk == null ? new byte[0] : chunk;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (chunk == null || chunk.length == 0 || chunk.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Screenshot chunk exceeds packet limit");
        }
        int start = buf.writerIndex();
        writeString(buf, uploadId, MAX_ID_BYTES);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        buf.writeInt(totalBytes);
        if (chunkIndex == 0) {
            writeString(buf, destination, MAX_DESTINATION_BYTES);
            writeString(buf, targetType, MAX_TARGET_TYPE_BYTES);
            writeString(buf, targetId, MAX_TARGET_BYTES);
            writeString(buf, caption, MAX_CAPTION_BYTES);
            writeString(buf, fileName, MAX_FILE_NAME_BYTES);
            buf.writeInt(width);
            buf.writeInt(height);
        }
        buf.writeInt(chunk.length);
        if (chunk.length > 0) buf.writeBytes(chunk);
        if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
            throw new IllegalArgumentException("Screenshot packet exceeds FML payload limit");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        try {
            int start = buf.readerIndex();
            uploadId = readString(buf, MAX_ID_BYTES);
            chunkIndex = buf.readInt();
            totalChunks = buf.readInt();
            totalBytes = buf.readInt();
            destination = "";
            targetType = "";
            targetId = "";
            caption = "";
            fileName = "";
            width = 0;
            height = 0;
            if (chunkIndex == 0) {
                destination = readString(buf, MAX_DESTINATION_BYTES);
                targetType = readString(buf, MAX_TARGET_TYPE_BYTES);
                targetId = readString(buf, MAX_TARGET_BYTES);
                caption = readString(buf, MAX_CAPTION_BYTES);
                fileName = readString(buf, MAX_FILE_NAME_BYTES);
                width = buf.readInt();
                height = buf.readInt();
            }
            int length = buf.readInt();
            if (length <= 0 || length > MAX_CHUNK_BYTES || length > buf.readableBytes()) {
                throw new IllegalArgumentException("Invalid screenshot chunk length");
            }
            chunk = new byte[length];
            buf.readBytes(chunk);
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Screenshot packet has trailing bytes");
            }
        } catch (RuntimeException error) {
            malformed = true;
            chunk = new byte[0];
        }
    }

    private static void writeString(ByteBuf buf, String value, int maxBytes) {
        byte[] bytes = safe(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("packet string exceeds limit");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf, int maxBytes) {
        return NetworkPacketCodec.readUtf8(buf, maxBytes);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Handler implements IMessageHandler<PacketScreenshotUpload, IMessage> {

        @Override
        public IMessage onMessage(final PacketScreenshotUpload message, MessageContext ctx) {
            final EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            if (message == null || message.malformed || player == null || ctx == null) return null;
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    IMessage ack = ScreenshotUploadService.instance()
                        .accept(player, message);
                    if (ack != null) {
                        AdvanceDataMonitor.ADMCHANEL.sendTo(ack, player);
                    }
                }
            });
        }
    }
}
