package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.textech.items.GrappleHookMode;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.network.handler.PacketHandlers;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketGrappleHookConfig implements IMessage {

    private double travelSpeed;

    private boolean showNodeName = true;

    private boolean showNodeDistance = true;

    private int modeId = GrappleHookMode.QUEUE.getId();
    public boolean malformed;

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
        int start = buf.writerIndex();
        try {
            if (!isFinite(travelSpeed) || GrappleHookMode.fromId(modeId).getId() != modeId) {
                throw new IllegalArgumentException("Invalid grapple hook configuration");
            }
            buf.writeDouble(travelSpeed);
            buf.writeBoolean(showNodeName);
            buf.writeBoolean(showNodeDistance);
            buf.writeInt(modeId);
        } catch (RuntimeException error) {
            buf.writerIndex(start);
            throw error;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        travelSpeed = 0.0D;
        showNodeName = false;
        showNodeDistance = false;
        modeId = GrappleHookMode.QUEUE.getId();
        try {
            int length = buf.readableBytes();
            if (length != 10 && length != 14) {
                throw new IllegalArgumentException("Invalid grapple hook configuration length");
            }
            travelSpeed = buf.readDouble();
            showNodeName = buf.readBoolean();
            showNodeDistance = buf.readBoolean();
            if (length == 14) {
                modeId = buf.readInt();
            }
            if (!isFinite(travelSpeed) || GrappleHookMode.fromId(modeId).getId() != modeId
                || buf.isReadable()) {
                throw new IllegalArgumentException("Invalid grapple hook configuration");
            }
        } catch (RuntimeException error) {
            malformed = true;
            travelSpeed = 0.0D;
            showNodeName = false;
            showNodeDistance = false;
            modeId = GrappleHookMode.QUEUE.getId();
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static class Handler implements IMessageHandler<PacketGrappleHookConfig, IMessage> {

        @Override
        public IMessage onMessage(final PacketGrappleHookConfig message, MessageContext ctx) {
            if (message == null || message.malformed) {
                return null;
            }
            return PacketHandlers.runOnServer(ctx, new Runnable() {

                @Override
                public void run() {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
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
        }
    }
}
