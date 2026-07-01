package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketMatterBallDecompressorToggle implements IMessage {

    public static final byte KIND_OUTPUT_MODE = 0;
    public static final byte KIND_BLOCK_MODE = 1;

    private int x;
    private int y;
    private int z;
    private byte kind;
    private boolean value;

    public PacketMatterBallDecompressorToggle() {}

    public PacketMatterBallDecompressorToggle(int x, int y, int z, byte kind, boolean value) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.kind = kind;
        this.value = value;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(kind);
        buf.writeBoolean(value);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        kind = buf.readByte();
        value = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<PacketMatterBallDecompressorToggle, IMessage> {

        @Override
        public IMessage onMessage(final PacketMatterBallDecompressorToggle message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    if (player == null) {
                        return;
                    }
                    World world = player.worldObj;
                    TileEntity te = world.getTileEntity(message.x, message.y, message.z);
                    if (!(te instanceof TileEntityMatterBallDecompressor)) {
                        return;
                    }
                    TileEntityMatterBallDecompressor decompressor = (TileEntityMatterBallDecompressor) te;
                    if (!decompressor.isUseableByPlayer(player)) {
                        return;
                    }
                    if (message.kind == KIND_OUTPUT_MODE) {
                        decompressor.setOutputToNetwork(message.value);
                    } else if (message.kind == KIND_BLOCK_MODE) {
                        decompressor.setBlockMode(message.value);
                    }
                    world.markBlockForUpdate(message.x, message.y, message.z);
                }
            });
            return null;
        }
    }
}
