package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.textech.Config;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.items.ItemSuperOrange;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketSuperOrangeConfig implements IMessage {

    private String customName = "";
    private boolean matterBallEnabled;
    private boolean pickupMatterBallEnabled;
    private boolean dropMatterBallEnabled;
    private int dropMultiplier;

    public PacketSuperOrangeConfig() {}

    public PacketSuperOrangeConfig(String customName, boolean matterBallEnabled, boolean pickupMatterBallEnabled,
        boolean dropMatterBallEnabled, int dropMultiplier) {
        this.customName = customName != null ? customName : "";
        this.matterBallEnabled = matterBallEnabled;
        this.pickupMatterBallEnabled = pickupMatterBallEnabled;
        this.dropMatterBallEnabled = dropMatterBallEnabled;
        this.dropMultiplier = dropMultiplier;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int nameLen = Math.min(customName.length(), 64);
        buf.writeInt(nameLen);
        if (nameLen > 0) {
            buf.writeBytes(
                customName.substring(0, nameLen)
                    .getBytes(java.nio.charset.Charset.forName("UTF-8")));
        }
        buf.writeBoolean(matterBallEnabled);
        buf.writeBoolean(pickupMatterBallEnabled);
        buf.writeBoolean(dropMatterBallEnabled);
        buf.writeInt(dropMultiplier);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int nameLen = buf.readInt();
        if (nameLen > 0) {
            byte[] bytes = new byte[nameLen];
            buf.readBytes(bytes);
            customName = new String(bytes, java.nio.charset.Charset.forName("UTF-8"));
        } else {
            customName = "";
        }
        matterBallEnabled = buf.readBoolean();
        pickupMatterBallEnabled = buf.readBoolean();
        dropMatterBallEnabled = buf.readBoolean();
        dropMultiplier = buf.readInt();
    }

    public static class Handler implements IMessageHandler<PacketSuperOrangeConfig, IMessage> {

        @Override
        public IMessage onMessage(final PacketSuperOrangeConfig message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            HandlerTick.enqueueServerTask(new Runnable() {

                @Override
                public void run() {
                    if (player == null) {
                        return;
                    }
                    ItemStack held = player.getHeldItem();
                    if (held == null || !(held.getItem() instanceof ItemSuperOrange)) {
                        held = ItemSuperOrange.findOrangeStack(player);
                    }
                    if (held == null || !(held.getItem() instanceof ItemSuperOrange)) {
                        return;
                    }
                    String name = message.customName.trim();
                    if (name.length() > 64) {
                        name = name.substring(0, 64);
                    }
                    ItemSuperOrange.setCustomName(held, name);
                    if (!name.isEmpty()) {
                        held.setStackDisplayName(name);
                    } else {
                        held.func_135074_t();
                    }
                    ItemSuperOrange.setMatterBallEnabled(held, message.matterBallEnabled);
                    ItemSuperOrange.setPickupMatterBallEnabled(held, message.pickupMatterBallEnabled);
                    ItemSuperOrange.setDropMatterBallEnabled(held, message.dropMatterBallEnabled);
                    int mult = message.dropMultiplier;
                    if (mult < 1) {
                        mult = 1;
                    }
                    int max = Math.max(1, Config.superOrangeDropMultiplierMax);
                    if (mult > max) {
                        mult = max;
                    }
                    ItemSuperOrange.setDropMultiplier(held, mult);
                }
            });
            return null;
        }
    }
}
