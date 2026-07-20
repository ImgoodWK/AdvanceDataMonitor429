package com.imgood.textech.network.packet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.network.packet.PacketSynTileEntity;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.NetworkValidationUtil;
import com.imgood.textech.utils.WebDashboardSnapshotCodec;
import com.imgood.textech.utils.WebDisplayBindingCodec;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Bidirectional, bounded transfer for passive monitor web-surface snapshots.
 *
 * C->S: request content by hash or upload one binding. S->C: content response or upload acknowledgement.
 */
public class PacketMonitorWebSurface implements IMessage {

    private static final byte KIND_REQUEST = 0;
    private static final byte KIND_CONTENT = 1;
    private static final byte KIND_UPLOAD = 2;
    private static final byte KIND_ACK = 3;
    private byte kind;
    private int x;
    private int y;
    private int z;
    private int index;
    private String hash = "";
    private byte[] payload = new byte[0];
    private NBTTagCompound config;
    private boolean success;
    private boolean valid = true;

    public PacketMonitorWebSurface() {}

    public static PacketMonitorWebSurface request(int x, int y, int z, int index, String hash) {
        return new PacketMonitorWebSurface(KIND_REQUEST, x, y, z, index, hash, null, null, false);
    }

    public static PacketMonitorWebSurface upload(int x, int y, int z, int index, String hash, byte[] payload,
        NBTTagCompound config) {
        return new PacketMonitorWebSurface(KIND_UPLOAD, x, y, z, index, hash, payload, config, false);
    }

    private static PacketMonitorWebSurface content(int x, int y, int z, int index, String hash, byte[] payload) {
        return new PacketMonitorWebSurface(KIND_CONTENT, x, y, z, index, hash, payload, null, true);
    }

    private static PacketMonitorWebSurface ack(int x, int y, int z, int index, String hash, boolean success) {
        return new PacketMonitorWebSurface(KIND_ACK, x, y, z, index, hash, null, null, success);
    }

    private PacketMonitorWebSurface(byte kind, int x, int y, int z, int index, String hash, byte[] payload,
        NBTTagCompound config, boolean success) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.index = index;
        this.hash = hash == null ? "" : hash;
        this.payload = payload == null ? new byte[0] : payload.clone();
        this.config = config == null ? null : (NBTTagCompound) config.copy();
        this.success = success;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        index = buf.readUnsignedByte();
        hash = ByteBufUtils.readUTF8String(buf);
        success = buf.readBoolean();
        int length = buf.readInt();
        if (length < 0 || length > WebDashboardSnapshotCodec.MAX_COMPRESSED_BYTES || length > buf.readableBytes()) {
            valid = false;
            if (length > 0) buf.skipBytes(Math.min(length, buf.readableBytes()));
            payload = new byte[0];
            config = null;
            return;
        } else {
            payload = new byte[length];
            buf.readBytes(payload);
        }
        config = buf.readBoolean() ? ByteBufUtils.readTag(buf) : null;
        if (hash.length() > 64 || index >= TileEntityAdvanceDataMonitor.MAX_DATA_BINDINGS) valid = false;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(index);
        ByteBufUtils.writeUTF8String(buf, hash == null ? "" : hash);
        buf.writeBoolean(success);
        int length = payload == null ? 0 : Math.min(payload.length, WebDashboardSnapshotCodec.MAX_COMPRESSED_BYTES);
        buf.writeInt(length);
        if (length > 0) buf.writeBytes(payload, 0, length);
        buf.writeBoolean(config != null);
        if (config != null) ByteBufUtils.writeTag(buf, config);
    }

    private boolean hasValidHash() {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    public static class ServerHandler implements IMessageHandler<PacketMonitorWebSurface, IMessage> {

        private static final Map<UUID, RateState> RATE = new HashMap<UUID, RateState>();

        @Override
        public IMessage onMessage(final PacketMonitorWebSurface message, final MessageContext ctx) {
            if (message == null || !message.valid || !message.hasValidHash()) return null;
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || !allow(player.getUniqueID(), message.kind == KIND_UPLOAD ? 3.0D : 1.0D)) {
                return null;
            }
            WebDashboardSnapshotCodec.DecodedSnapshot decoded = null;
            boolean uploadPayloadValid = true;
            if (message.kind == KIND_UPLOAD && message.payload.length > 0) {
                try {
                    decoded = WebDashboardSnapshotCodec.decode(message.payload);
                    uploadPayloadValid = message.hash.equals(decoded.hash);
                } catch (WebDashboardSnapshotCodec.SnapshotException e) {
                    uploadPayloadValid = false;
                }
            }
            final WebDashboardSnapshotCodec.DecodedSnapshot validatedSnapshot = decoded;
            final boolean validatedUploadPayload = uploadPayloadValid;
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    World world = player.worldObj;
                    if (world == null || !NetworkValidationUtil.isWithinReach(player, message.x, message.y, message.z)) {
                        return;
                    }
                    TileEntity tile = world.getTileEntity(message.x, message.y, message.z);
                    if (!(tile instanceof TileEntityAdvanceDataMonitor)) return;
                    TileEntityAdvanceDataMonitor monitor = (TileEntityAdvanceDataMonitor) tile;

                    if (message.kind == KIND_REQUEST) {
                        byte[] content = monitor.getWebDashboardPayload(message.index, message.hash);
                        if (content != null) {
                            AdvanceDataMonitor.ADMCHANEL.sendTo(
                                PacketMonitorWebSurface.content(
                                    message.x,
                                    message.y,
                                    message.z,
                                    message.index,
                                    message.hash,
                                    content),
                                player);
                        }
                        return;
                    }

                    if (message.kind != KIND_UPLOAD || !NetworkValidationUtil.canEditOwnedTile(player, monitor)) {
                        return;
                    }
                    boolean accepted = false;
                    String mode = message.config == null ? ""
                        : message.config.getString(TileEntityAdvanceDataMonitor.WEB_SURFACE_MODE_KEY);
                    if (TileEntityAdvanceDataMonitor.MODE_DASHBOARD_LIVE.equals(mode)
                        || TileEntityAdvanceDataMonitor.MODE_LIVE_URL.equals(mode)) {
                        try {
                            String bindingJson = message.config.hasKey("webBindingJson")
                                ? message.config.getString("webBindingJson")
                                : "";
                            WebDisplayBindingCodec.Binding binding = WebDisplayBindingCodec.parse(bindingJson);
                            if (message.hash.equals(binding.bindingHash)) {
                                accepted = monitor.applyWebLiveBinding(message.index, message.config, binding);
                            }
                        } catch (WebDisplayBindingCodec.BindingException e) {
                            accepted = false;
                        }
                    } else {
                        accepted = validatedUploadPayload && message.config != null
                            && monitor.applyWebDashboardBinding(
                                message.index,
                                message.config,
                                message.payload.length == 0 ? null : message.payload,
                                message.hash,
                                validatedSnapshot);
                    }
                    if (accepted) {
                        monitor.markDirty();
                        world.markBlockForUpdate(message.x, message.y, message.z);
                    }
                    AdvanceDataMonitor.ADMCHANEL.sendTo(
                        PacketMonitorWebSurface.ack(
                            message.x,
                            message.y,
                            message.z,
                            message.index,
                            message.hash,
                            accepted),
                        player);
                    if (!accepted) {
                        NBTTagCompound authoritative = new NBTTagCompound();
                        monitor.writeSyncNBT(authoritative);
                        AdvanceDataMonitor.ADMCHANEL.sendTo(
                            new PacketSynTileEntity(message.x, message.y, message.z, authoritative),
                            player);
                    }
                }
            });
        }

        private static synchronized boolean allow(UUID playerId, double cost) {
            long now = System.currentTimeMillis();
            RateState state = RATE.get(playerId);
            if (state == null) {
                state = new RateState(now);
                RATE.put(playerId, state);
            }
            double elapsedSeconds = Math.max(0L, now - state.updatedAt) / 1000.0D;
            state.tokens = Math.min(12.0D, state.tokens + elapsedSeconds * 4.0D);
            state.updatedAt = now;
            if (state.tokens < cost) return false;
            state.tokens -= cost;

            if (RATE.size() > 256) {
                Iterator<Map.Entry<UUID, RateState>> iterator = RATE.entrySet().iterator();
                while (iterator.hasNext()) {
                    if (now - iterator.next().getValue().updatedAt > 120000L) iterator.remove();
                }
            }
            return true;
        }

        private static final class RateState {

            private double tokens = 12.0D;
            private long updatedAt;

            private RateState(long updatedAt) {
                this.updatedAt = updatedAt;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketMonitorWebSurface, IMessage> {

        @Override
        public IMessage onMessage(PacketMonitorWebSurface message, MessageContext ctx) {
            if (message == null || !message.valid || !message.hasValidHash()) return null;
            if (message.kind == KIND_CONTENT && message.payload.length > 0) {
                com.imgood.textech.client.WebSurfaceClientCache.acceptContent(message.hash, message.payload);
            } else if (message.kind == KIND_ACK) {
                com.imgood.textech.client.WebSurfaceClientCache.recordUploadAck(message.hash, message.success);
                if (!message.success) {
                    final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
                    minecraft.func_152344_a(new Runnable() {

                        @Override
                        public void run() {
                            if (minecraft.thePlayer != null) {
                                minecraft.thePlayer.addChatMessage(
                                    new net.minecraft.util.ChatComponentTranslation(
                                        "adm.error.web_dashboard_upload_failed"));
                            }
                        }
                    });
                }
            }
            return null;
        }
    }
}
