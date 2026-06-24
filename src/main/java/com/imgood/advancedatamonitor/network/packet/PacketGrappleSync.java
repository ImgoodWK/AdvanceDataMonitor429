package com.imgood.advancedatamonitor.network.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.imgood.advancedatamonitor.client.GrappleClientCache;
import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class PacketGrappleSync implements IMessage {

    public boolean attached;
    public boolean traveling;
    public int anchorX;
    public int anchorY;
    public int anchorZ;
    public int travelTargetX;
    public int travelTargetY;
    public int travelTargetZ;
    public float travelProgress;
    public List<BlockPos> nodes = new ArrayList<BlockPos>();
    public List<BlockPos> travelQueue = new ArrayList<BlockPos>();

    public PacketGrappleSync() {}

    public static PacketGrappleSync detached() {
        PacketGrappleSync packet = new PacketGrappleSync();
        packet.attached = false;
        packet.traveling = false;
        return packet;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(attached);
        buf.writeBoolean(traveling);
        buf.writeInt(anchorX);
        buf.writeInt(anchorY);
        buf.writeInt(anchorZ);
        buf.writeInt(travelTargetX);
        buf.writeInt(travelTargetY);
        buf.writeInt(travelTargetZ);
        buf.writeFloat(travelProgress);
        writePositions(buf, nodes);
        writePositions(buf, travelQueue);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        attached = buf.readBoolean();
        traveling = buf.readBoolean();
        anchorX = buf.readInt();
        anchorY = buf.readInt();
        anchorZ = buf.readInt();
        travelTargetX = buf.readInt();
        travelTargetY = buf.readInt();
        travelTargetZ = buf.readInt();
        travelProgress = buf.readFloat();
        nodes = readPositions(buf);
        travelQueue = readPositions(buf);
    }

    private static void writePositions(ByteBuf buf, List<BlockPos> positions) {
        buf.writeShort(positions.size());
        for (BlockPos pos : positions) {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        }
    }

    private static List<BlockPos> readPositions(ByteBuf buf) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        int count = buf.readShort();
        for (int i = 0; i < count; i++) {
            result.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return result;
    }

    public static class ClientHandler implements IMessageHandler<PacketGrappleSync, IMessage> {

        @Override
        public IMessage onMessage(final PacketGrappleSync message, MessageContext ctx) {
            scheduleClient(new Runnable() {

                @Override
                public void run() {
                    applyClient(message);
                }
            });
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void applyClient(PacketGrappleSync message) {
            GrappleClientCache.apply(message);
        }

        @SideOnly(Side.CLIENT)
        private static void scheduleClient(Runnable runnable) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                mc.getClass()
                    .getMethod("func_152344_a", Runnable.class)
                    .invoke(mc, runnable);
            } catch (Exception e) {
                runnable.run();
            }
        }
    }
}
