package com.imgood.advancedatamonitor.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.handler.HandlerTick;
import com.imgood.advancedatamonitor.items.GrappleHookMode;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketGrappleHookConfig implements IMessage {

    private double travelSpeed;

    private boolean showNodeName = true;

    private boolean showNodeDistance = true;

    private int modeId = GrappleHookMode.QUEUE.getId();

    public PacketGrappleHookConfig() {}

    public PacketGrappleHookConfig(double travelSpeed, boolean showNodeName, boolean showNodeDistance) {
        this(travelSpeed, showNodeName, showNodeDistance, GrappleHookMode.QUEUE.getId());
    }

    public PacketGrappleHookConfig(double travelSpeed, boolean showNodeName, boolean showNodeDistance, int modeId) {
        this.travelSpeed = travelSpeed;
        this.showNodeName = showNodeName;
        this.showNodeDistance = showNodeDistance;
        this.modeId = modeId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(travelSpeed);
        buf.writeBoolean(showNodeName);
        buf.writeBoolean(showNodeDistance);
        buf.writeInt(modeId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        travelSpeed = buf.readDouble();
        showNodeName = buf.readBoolean();
        showNodeDistance = buf.readBoolean();
        if (buf.readableBytes() >= 4) {
            modeId = buf.readInt();
        }
    }

    public static class Handler implements IMessageHandler<PacketGrappleHookConfig, IMessage> {

        @Override
        public IMessage onMessage(final PacketGrappleHookConfig message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    if (player == null) {
                        return;
                    }
                    ItemStack held = player.getHeldItem();
                    if (held == null || !(held.getItem() instanceof ItemGrappleHook)) {
                        return;
                    }
                    double speed = message.travelSpeed;
                    if (speed < 0.1D) {
                        speed = 0.1D;
                    }
                    if (speed > 5.0D) {
                        speed = 5.0D;
                    }
                    ItemGrappleHook.setTravelSpeed(held, speed);
                    ItemGrappleHook.setShowNodeName(held, message.showNodeName);
                    ItemGrappleHook.setShowNodeDistance(held, message.showNodeDistance);
                    ItemGrappleHook.setHookMode(held, GrappleHookMode.fromId(message.modeId));
                }
            });
            return null;
        }
    }
}
