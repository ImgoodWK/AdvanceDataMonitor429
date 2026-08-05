package com.imgood.textech.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.utils.NetworkValidationUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketGrappleAnchorConfig implements IMessage {

    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_DISPLAY_NAME_BYTES = 256;

    private int x;

    private int y;

    private int z;

    private String displayName = "";

    private int iconCursorColor = TileEntityGrappleAnchor.DEFAULT_ICON_CURSOR_COLOR;
    public boolean malformed;

    public PacketGrappleAnchorConfig() {}

    public PacketGrappleAnchorConfig(int x, int y, int z, String displayName, int iconCursorColor) {

        this.x = x;

        this.y = y;

        this.z = z;

        this.displayName = displayName == null ? "" : displayName;

        this.iconCursorColor = iconCursorColor & 0xFFFFFF;

    }

    @Override

    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            NetworkPacketCodec.writeVarUtf8(buf, displayName == null ? "" : displayName, MAX_DISPLAY_NAME_BYTES);
            buf.writeInt(iconCursorColor);
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Grapple anchor config exceeds packet body limit");
            }
        } catch (RuntimeException error) {
            buf.writerIndex(start);
            throw error;
        }

    }

    public boolean fitsPacketBudget() {
        ByteBuf scratch = Unpooled.buffer(64);
        try {
            toBytes(scratch);
            return scratch.readableBytes() <= MAX_PACKET_BODY_BYTES;
        } catch (RuntimeException error) {
            return false;
        } finally {
            scratch.release();
        }
    }

    @Override

    public void fromBytes(ByteBuf buf) {

        malformed = false;
        x = 0;
        y = 0;
        z = 0;
        displayName = "";
        iconCursorColor = TileEntityGrappleAnchor.DEFAULT_ICON_CURSOR_COLOR;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Grapple anchor config exceeds packet body limit");
            }
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            displayName = NetworkPacketCodec.readVarUtf8(buf, MAX_DISPLAY_NAME_BYTES);
            iconCursorColor = buf.readInt();
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Grapple anchor config has trailing data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            x = 0;
            y = 0;
            z = 0;
            displayName = "";
            iconCursorColor = TileEntityGrappleAnchor.DEFAULT_ICON_CURSOR_COLOR;
        }

    }

    public int getX() {

        return x;

    }

    public int getY() {

        return y;

    }

    public int getZ() {

        return z;

    }

    public String getDisplayName() {

        return displayName;

    }

    public int getIconCursorColor() {

        return iconCursorColor;

    }

    private static void applyToTileEntity(World world, PacketGrappleAnchorConfig message) {

        if (world == null || message == null) {

            return;

        }

        TileEntity te = world.getTileEntity(message.x, message.y, message.z);

        if (te instanceof TileEntityGrappleAnchor) {

            TileEntityGrappleAnchor anchor = (TileEntityGrappleAnchor) te;

            anchor.setDisplayName(message.displayName);

            anchor.setIconCursorColor(message.iconCursorColor);

            world.markBlockForUpdate(message.x, message.y, message.z);

        }

    }

    public static class ServerHandler implements IMessageHandler<PacketGrappleAnchorConfig, IMessage> {

        @Override

        public IMessage onMessage(final PacketGrappleAnchorConfig message, MessageContext ctx) {

            if (message == null || message.malformed) {
                return null;
            }

            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override

                public void run() {

                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;

                    if (player == null || player.worldObj == null) {

                        return;

                    }

                    World world = player.worldObj;

                    TileEntity te = world.getTileEntity(message.x, message.y, message.z);

                    if (!(te instanceof TileEntityGrappleAnchor)) {

                        return;

                    }

                    if (!NetworkValidationUtil.canEditOwnedTile(player, te)) {

                        return;

                    }

                    TileEntityGrappleAnchor anchor = (TileEntityGrappleAnchor) te;

                    anchor.applyConfig(message.displayName, message.iconCursorColor);

                }

            });

        }

    }

    @SideOnly(Side.CLIENT)

    public static class ClientHandler implements IMessageHandler<PacketGrappleAnchorConfig, IMessage> {

        @Override

        public IMessage onMessage(PacketGrappleAnchorConfig message, MessageContext ctx) {

            if (message == null || message.malformed) {
                return null;
            }

            World world = Minecraft.getMinecraft().theWorld;

            applyToTileEntity(world, message);

            return null;

        }

    }

}
