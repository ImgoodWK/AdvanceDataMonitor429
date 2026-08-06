package com.imgood.textech.webae.network;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;

import com.imgood.textech.renders.WebAlertHudRenderer;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** S→C: show a bounded WebAE alert in the owning player's client HUD. */
public final class PacketWebAlertNotify implements IMessage {

    private static final int MAX_SEVERITY_BYTES = 16;
    private static final int MAX_TITLE_BYTES = 256;
    private static final int MAX_MESSAGE_BYTES = 2048;
    private static final int MAX_POSITION_BYTES = 16;
    private static final int MAX_PACKET_BYTES = 30_000;

    public String severity = "warning";
    public String title = "";
    public String message = "";
    public int durationSeconds = 10;
    public int maxVisible = 3;
    public String position = "top_right";
    public boolean soundEnabled;
    private boolean valid = true;

    public PacketWebAlertNotify() {}

    public PacketWebAlertNotify(String severity, String title, String message, int durationSeconds, int maxVisible,
        String position, boolean soundEnabled) {
        this.severity = severity == null ? "warning" : severity;
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        this.durationSeconds = durationSeconds;
        this.maxVisible = maxVisible;
        this.position = position == null ? "top_right" : position;
        this.soundEnabled = soundEnabled;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            if (!isValidSeverity(severity) || !isValidPosition(position)) {
                throw new IllegalArgumentException("Invalid WebAE alert enum value");
            }
            writeString(buf, severity, MAX_SEVERITY_BYTES);
            writeString(buf, title, MAX_TITLE_BYTES);
            writeString(buf, message, MAX_MESSAGE_BYTES);
            buf.writeByte(Math.max(2, Math.min(120, durationSeconds)));
            buf.writeByte(Math.max(1, Math.min(8, maxVisible)));
            writeString(buf, position, MAX_POSITION_BYTES);
            buf.writeBoolean(soundEnabled);
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
                throw new IllegalArgumentException("WebAE alert exceeds packet budget");
            }
            severity = readString(buf, MAX_SEVERITY_BYTES);
            if (!isValidSeverity(severity)) {
                throw new IllegalArgumentException("Invalid WebAE alert severity");
            }
            title = readString(buf, MAX_TITLE_BYTES);
            message = readString(buf, MAX_MESSAGE_BYTES);
            durationSeconds = Math.max(2, Math.min(120, buf.readUnsignedByte()));
            maxVisible = Math.max(1, Math.min(8, buf.readUnsignedByte()));
            position = readString(buf, MAX_POSITION_BYTES);
            if (!isValidPosition(position)) {
                throw new IllegalArgumentException("Invalid WebAE alert position");
            }
            soundEnabled = buf.readBoolean();
            if (buf.isReadable()) {
                throw new IllegalArgumentException("WebAE alert has trailing bytes");
            }
        } catch (RuntimeException e) {
            valid = false;
            severity = "";
            title = "";
            message = "";
            position = "";
        }
    }

    private static void writeString(ByteBuf buf, String value, int maxBytes) {
        String text = value == null ? "" : value;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes || maxBytes > 0xffff) {
            throw new IllegalArgumentException("WebAE alert field exceeds packet limit");
        }
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf, int maxBytes) {
        if (buf.readableBytes() < 2) {
            throw new IllegalArgumentException("Missing WebAE alert string length");
        }
        int declared = buf.readUnsignedShort();
        if (declared > maxBytes || declared > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid WebAE alert string length");
        }
        byte[] bytes = new byte[declared];
        if (declared > 0) buf.readBytes(bytes);
        return NetworkPacketCodec.decodeUtf8(bytes);
    }

    private static boolean isValidSeverity(String value) {
        return "info".equals(value) || "warning".equals(value) || "error".equals(value);
    }

    private static boolean isValidPosition(String value) {
        return "top_left".equals(value) || "top_right".equals(value)
            || "bottom_left".equals(value)
            || "bottom_right".equals(value);
    }

    private static void requirePacketBudget(ByteBuf buf, int start) {
        if (buf.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("WebAE alert exceeds packet budget");
        }
    }

    @SideOnly(Side.CLIENT)
    public static final class Handler implements IMessageHandler<PacketWebAlertNotify, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebAlertNotify message, MessageContext ctx) {
            if (message == null || !message.valid) return null;
            final Minecraft minecraft = Minecraft.getMinecraft();
            Runnable task = new Runnable() {

                @Override
                public void run() {
                    WebAlertHudRenderer.instance()
                        .push(
                            message.severity,
                            message.title,
                            message.message,
                            message.durationSeconds,
                            message.maxVisible,
                            message.position,
                            message.soundEnabled);
                }
            };
            try {
                Method method = minecraft.getClass()
                    .getMethod("func_152344_a", Runnable.class);
                method.invoke(minecraft, task);
            } catch (Exception ignored) {
                task.run();
            }
            return null;
        }
    }
}
