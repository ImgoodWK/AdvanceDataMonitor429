package com.imgood.textech.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class PacketGrappleAnchorConfig implements IMessage {

    private int x;

    private int y;

    private int z;

    private String displayName = "";

    private int iconCursorColor = TileEntityGrappleAnchor.DEFAULT_ICON_CURSOR_COLOR;

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

        buf.writeInt(x);

        buf.writeInt(y);

        buf.writeInt(z);

        ByteBufUtils.writeUTF8String(buf, displayName == null ? "" : displayName);

        buf.writeInt(iconCursorColor);

    }

    @Override

    public void fromBytes(ByteBuf buf) {

        x = buf.readInt();

        y = buf.readInt();

        z = buf.readInt();

        displayName = ByteBufUtils.readUTF8String(buf);

        iconCursorColor = buf.readInt();

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

            HandlerTick.enqueueServerTask(new Runnable() {

                @Override

                public void run() {

                    World world = ctx.getServerHandler().playerEntity.worldObj;

                    TileEntity te = world.getTileEntity(message.x, message.y, message.z);

                    if (te instanceof TileEntityGrappleAnchor) {

                        TileEntityGrappleAnchor anchor = (TileEntityGrappleAnchor) te;

                        anchor.applyConfig(message.displayName, message.iconCursorColor);

                    }

                }

            });

            return null;

        }

    }

    @SideOnly(Side.CLIENT)

    public static class ClientHandler implements IMessageHandler<PacketGrappleAnchorConfig, IMessage> {

        @Override

        public IMessage onMessage(PacketGrappleAnchorConfig message, MessageContext ctx) {

            World world = Minecraft.getMinecraft().theWorld;

            applyToTileEntity(world, message);

            return null;

        }

    }

}
