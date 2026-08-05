package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

import com.imgood.textech.handler.ItemCountSyncCoordinator;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.utils.NetworkValidationUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * @program: TeXTech-GTNH
 * @description:
 * @author: Imgood
 * @create: 2025-07-02 09:27
 **/
public class PacketRequestItemCountSync implements IMessage {

    private int x, y, z;
    public boolean malformed;

    public PacketRequestItemCountSync() {}

    public PacketRequestItemCountSync(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        x = 0;
        y = 0;
        z = 0;
        try {
            if (buf.readableBytes() != 12) {
                throw new IllegalArgumentException("Invalid item count request length");
            }
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            if (buf.isReadable()) {
                throw new IllegalArgumentException("Item count request has trailing data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            x = 0;
            y = 0;
            z = 0;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketRequestItemCountSync, IMessage> {

        @Override
        public IMessage onMessage(final PacketRequestItemCountSync message, final MessageContext ctx) {
            if (message == null || message.malformed) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    if (player == null || player.worldObj == null
                        || !NetworkValidationUtil.isWithinReach(player, message.x, message.y, message.z)) {
                        return;
                    }
                    TileEntity tile = player.worldObj.getTileEntity(message.x, message.y, message.z);
                    if (!(tile instanceof TileEntityAdvanceNetworkLink)) {
                        return;
                    }
                    ItemCountSyncCoordinator.schedule(player.worldObj, message.x, message.y, message.z);
                }
            });
        }
    }
}
