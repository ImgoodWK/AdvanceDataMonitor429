package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.webae.screenshot.ScreenshotUploadService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** C→S bounded screenshot upload chunk. Metadata is transmitted only in chunk zero. Packet ID 51. */
public final class PacketScreenshotUpload implements IMessage {

    public static final int MAX_CHUNK_BYTES = 24 * 1024;
    private static final int MAX_ID_BYTES = 64;
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

    public PacketScreenshotUpload(String uploadId, int chunkIndex, int totalChunks, int totalBytes,
        String destination, String targetType, String targetId, String caption, String fileName, int width,
        int height, byte[] chunk) {
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
        writeString(buf, uploadId, MAX_ID_BYTES);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        buf.writeInt(totalBytes);
        if (chunkIndex == 0) {
            writeString(buf, destination, 16);
            writeString(buf, targetType, 16);
            writeString(buf, targetId, MAX_TARGET_BYTES);
            writeString(buf, caption, MAX_CAPTION_BYTES);
            writeString(buf, fileName, MAX_FILE_NAME_BYTES);
            buf.writeInt(width);
            buf.writeInt(height);
        }
        int length = Math.min(MAX_CHUNK_BYTES, chunk.length);
        buf.writeInt(length);
        buf.writeBytes(chunk, 0, length);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            uploadId = readString(buf, MAX_ID_BYTES);
            chunkIndex = buf.readInt();
            totalChunks = buf.readInt();
            totalBytes = buf.readInt();
            if (chunkIndex == 0) {
                destination = readString(buf, 16);
                targetType = readString(buf, 16);
                targetId = readString(buf, MAX_TARGET_BYTES);
                caption = readString(buf, MAX_CAPTION_BYTES);
                fileName = readString(buf, MAX_FILE_NAME_BYTES);
                width = buf.readInt();
                height = buf.readInt();
            }
            int length = buf.readInt();
            if (length < 0 || length > MAX_CHUNK_BYTES || length > buf.readableBytes()) {
                malformed = true;
                chunk = new byte[0];
                return;
            }
            chunk = new byte[length];
            buf.readBytes(chunk);
        } catch (RuntimeException error) {
            malformed = true;
            chunk = new byte[0];
        }
    }

    private static void writeString(ByteBuf buf, String value, int maxBytes) {
        byte[] bytes = safe(value).getBytes(StandardCharsets.UTF_8);
        int length = Math.min(maxBytes, bytes.length);
        buf.writeInt(length);
        buf.writeBytes(bytes, 0, length);
    }

    private static String readString(ByteBuf buf, int maxBytes) {
        int length = buf.readInt();
        if (length < 0 || length > maxBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException("invalid string length");
        }
        if (length == 0) return "";
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Handler implements IMessageHandler<PacketScreenshotUpload, IMessage> {

        @Override
        public IMessage onMessage(PacketScreenshotUpload message, MessageContext ctx) {
            EntityPlayerMP player = ctx == null || ctx.getServerHandler() == null ? null
                : ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            return ScreenshotUploadService.instance().accept(player, message);
        }
    }
}
