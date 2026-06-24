package com.imgood.advancedatamonitor.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.handler.HandlerTick;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketGrappleHookConfig implements IMessage {

    private double travelSpeed;

    private boolean showNodeName = true;

    private boolean showNodeDistance = true;

    public PacketGrappleHookConfig() {}

    public PacketGrappleHookConfig(double travelSpeed, boolean showNodeName, boolean showNodeDistance) {

        this.travelSpeed = travelSpeed;

        this.showNodeName = showNodeName;

        this.showNodeDistance = showNodeDistance;

    }

    @Override

    public void toBytes(ByteBuf buf) {

        buf.writeDouble(travelSpeed);

        buf.writeBoolean(showNodeName);

        buf.writeBoolean(showNodeDistance);

    }

    @Override

    public void fromBytes(ByteBuf buf) {

        travelSpeed = buf.readDouble();

        showNodeName = buf.readBoolean();

        showNodeDistance = buf.readBoolean();

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

                }

            });

            return null;

        }

    }

}
