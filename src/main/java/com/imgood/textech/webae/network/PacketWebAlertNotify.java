package com.imgood.textech.webae.network;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;

import com.imgood.textech.renders.WebAlertHudRenderer;

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

    public String severity = "warning";
    public String title = "";
    public String message = "";
    public int durationSeconds = 10;
    public int maxVisible = 3;
    public String position = "top_right";
    public boolean soundEnabled;

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
        writeString(buf, severity, MAX_SEVERITY_BYTES);
        writeString(buf, title, MAX_TITLE_BYTES);
        writeString(buf, message, MAX_MESSAGE_BYTES);
        buf.writeByte(Math.max(2, Math.min(120, durationSeconds)));
        buf.writeByte(Math.max(1, Math.min(8, maxVisible)));
        writeString(buf, position, MAX_POSITION_BYTES);
        buf.writeBoolean(soundEnabled);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        severity = readString(buf, MAX_SEVERITY_BYTES);
        title = readString(buf, MAX_TITLE_BYTES);
        message = readString(buf, MAX_MESSAGE_BYTES);
        durationSeconds = Math.max(2, Math.min(120, buf.readUnsignedByte()));
        maxVisible = Math.max(1, Math.min(8, buf.readUnsignedByte()));
        position = readString(buf, MAX_POSITION_BYTES);
        soundEnabled = buf.readBoolean();
    }

    private static void writeString(ByteBuf buf, String value, int maxBytes) {
        String text = value == null ? "" : value;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        while (bytes.length > maxBytes && text.length() > 0) {
            int next = Math.max(0, text.length() - Math.max(1, text.length() / 8));
            text = text.substring(0, next);
            bytes = text.getBytes(StandardCharsets.UTF_8);
        }
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf, int maxBytes) {
        int declared = buf.readUnsignedShort();
        int available = Math.min(declared, buf.readableBytes());
        int accepted = Math.min(available, maxBytes);
        byte[] bytes = new byte[accepted];
        if (accepted > 0) buf.readBytes(bytes);
        int remaining = available - accepted;
        if (remaining > 0) buf.skipBytes(remaining);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @SideOnly(Side.CLIENT)
    public static final class Handler implements IMessageHandler<PacketWebAlertNotify, IMessage> {

        @Override
        public IMessage onMessage(final PacketWebAlertNotify message, MessageContext ctx) {
            final Minecraft minecraft = Minecraft.getMinecraft();
            Runnable task = new Runnable() {

                @Override
                public void run() {
                    WebAlertHudRenderer.instance().push(
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
                Method method = minecraft.getClass().getMethod("func_152344_a", Runnable.class);
                method.invoke(minecraft, task);
            } catch (Exception ignored) {
                task.run();
            }
            return null;
        }
    }
}
