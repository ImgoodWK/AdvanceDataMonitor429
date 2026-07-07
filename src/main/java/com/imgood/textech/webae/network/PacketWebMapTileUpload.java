package com.imgood.textech.webae.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.webae.worldmap.WorldMapChunkSetBuilder;
import com.imgood.textech.webae.worldmap.WorldMapHdSupport;
import com.imgood.textech.webae.worldmap.WorldMapMetaDto;
import com.imgood.textech.webae.worldmap.WorldMapTileCache;
import com.imgood.textech.webae.worldmap.WorldMapRenderSupport;
import com.imgood.textech.webae.worldmap.WorldMapTileLayer;
import com.imgood.textech.webae.worldmap.WorldMapView;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C→S: client uploads a rendered HD world map chunk PNG.
 * Packet ID 35.
 */
public class PacketWebMapTileUpload implements IMessage {

    public String view;
    public String layer = WorldMapTileLayer.TERRAIN;
    public int dim;
    public int chunkX;
    public int chunkZ;
    public int networkId;
    public String ownerUuid;
    public byte[] png;

    public PacketWebMapTileUpload() {}

    public PacketWebMapTileUpload(String view, int dim, int chunkX, int chunkZ, int networkId, String ownerUuid,
        byte[] png) {
        this(view, WorldMapTileLayer.TERRAIN, dim, chunkX, chunkZ, networkId, ownerUuid, png);
    }

    public PacketWebMapTileUpload(String view, String layer, int dim, int chunkX, int chunkZ, int networkId,
        String ownerUuid, byte[] png) {
        this.view = view;
        this.layer = WorldMapTileLayer.normalize(layer);
        this.dim = dim;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.networkId = networkId;
        this.ownerUuid = ownerUuid;
        this.png = png;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf8(buf, view);
        writeUtf8(buf, layer);
        buf.writeInt(dim);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeInt(networkId);
        writeUtf8(buf, ownerUuid);
        if (png != null) {
            buf.writeInt(png.length);
            buf.writeBytes(png);
        } else {
            buf.writeInt(0);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        view = readUtf8(buf);
        layer = WorldMapTileLayer.normalize(readUtf8(buf));
        dim = buf.readInt();
        chunkX = buf.readInt();
        chunkZ = buf.readInt();
        networkId = buf.readInt();
        ownerUuid = readUtf8(buf);
        int len = buf.readInt();
        if (len > 0) {
            png = new byte[len];
            buf.readBytes(png);
        } else {
            png = new byte[0];
        }
    }

    private static void writeUtf8(ByteBuf buf, String s) {
        if (s == null) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf8(ByteBuf buf) {
        int len = buf.readInt();
        if (len <= 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<PacketWebMapTileUpload, IMessage> {

        private static final int MAX_PNG_BYTES = 512 * 1024;

        @Override
        public IMessage onMessage(final PacketWebMapTileUpload message, MessageContext ctx) {
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    handleOnMainThread(message, ctx.getServerHandler().playerEntity);
                }
            });
        }

        private static void handleOnMainThread(PacketWebMapTileUpload message, EntityPlayerMP player) {
            if (message == null || player == null) {
                return;
            }
            if (!WorldMapHdSupport.isHdEnabled()) {
                return;
            }
            if (message.ownerUuid == null || message.ownerUuid.isEmpty()) {
                return;
            }
            if (!WorldMapHdSupport.canUploadForOwner(player, message.ownerUuid)) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Rejected world map HD upload from non-owner {}", player.getCommandSenderName());
                return;
            }
            WorldMapView parsed = WorldMapView.fromId(message.view);
            if (parsed == null || !WorldMapView.isEnabled(parsed)) {
                return;
            }
            if (message.png == null || message.png.length == 0 || message.png.length > MAX_PNG_BYTES) {
                return;
            }
            if (!WorldMapRenderSupport.isValidTilePng(message.png)) {
                return;
            }
            if (Config.webWorldMapRequireNetworkScope && message.networkId >= 0) {
                if (!isChunkAllowed(message.ownerUuid, message.networkId, message.dim, message.chunkX, message.chunkZ)) {
                    return;
                }
            }
            WorldMapTileCache.writeHd(parsed.id, message.layer, message.dim, message.chunkX, message.chunkZ, message.png);
            AdvanceDataMonitor.LOG.debug(
                "[WebAE] Stored HD world map tile view={} layer={} dim={} cx={} cz={} bytes={}",
                parsed.id,
                message.layer,
                message.dim,
                message.chunkX,
                message.chunkZ,
                message.png.length);
        }

        private static boolean isChunkAllowed(String ownerUuid, int networkId, int dim, int chunkX, int chunkZ) {
            WorldMapMetaDto meta = com.imgood.textech.webae.worldmap.WorldMapBoundsBuilder.rebuild(ownerUuid, networkId);
            if (meta == null || meta.dimensions == null || meta.dimensions.isEmpty()) {
                return meta != null && meta.hasLogicalSnapshot;
            }
            for (WorldMapMetaDto.DimensionInfo info : meta.dimensions) {
                if (info == null || info.dim != dim) {
                    continue;
                }
                return WorldMapChunkSetBuilder.containsChunk(info, chunkX, chunkZ);
            }
            return false;
        }
    }
}
