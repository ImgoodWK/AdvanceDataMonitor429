package com.imgood.textech.network.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.textech.Config;
import com.imgood.textech.items.ItemSuperOrange;
import com.imgood.textech.network.handler.PacketHandlers;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketSuperOrangeConfig implements IMessage {

    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_CUSTOM_NAME_BYTES = 256;

    private String customName = "";
    private boolean matterBallEnabled;
    private boolean pickupMatterBallEnabled;
    private boolean dropMatterBallEnabled;
    private int dropMultiplier;
    public boolean malformed;

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
        int start = buf.writerIndex();
        try {
            byte[] nameBytes = (customName == null ? "" : customName)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (nameBytes.length > MAX_CUSTOM_NAME_BYTES) {
                throw new IllegalArgumentException("Super Orange custom name exceeds packet limit");
            }
            buf.writeInt(nameBytes.length);
            if (nameBytes.length > 0) {
                buf.writeBytes(nameBytes);
            }
            buf.writeBoolean(matterBallEnabled);
            buf.writeBoolean(pickupMatterBallEnabled);
            buf.writeBoolean(dropMatterBallEnabled);
            buf.writeInt(dropMultiplier);
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Super Orange config exceeds packet body limit");
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
        customName = "";
        matterBallEnabled = false;
        pickupMatterBallEnabled = false;
        dropMatterBallEnabled = false;
        dropMultiplier = 0;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Super Orange config exceeds packet body limit");
            }
            int nameLen = buf.readInt();
            if (nameLen < 0 || nameLen > MAX_CUSTOM_NAME_BYTES || nameLen > buf.readableBytes()) {
                throw new IllegalArgumentException("Invalid Super Orange custom name length");
            }
            byte[] bytes = new byte[nameLen];
            if (nameLen > 0) {
                buf.readBytes(bytes);
            }
            customName = NetworkPacketCodec.decodeUtf8(bytes);
            matterBallEnabled = buf.readBoolean();
            pickupMatterBallEnabled = buf.readBoolean();
            dropMatterBallEnabled = buf.readBoolean();
            dropMultiplier = buf.readInt();
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Super Orange config has trailing data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            customName = "";
            matterBallEnabled = false;
            pickupMatterBallEnabled = false;
            dropMatterBallEnabled = false;
            dropMultiplier = 0;
        }
    }

    public static class Handler implements IMessageHandler<PacketSuperOrangeConfig, IMessage> {

        @Override
        public IMessage onMessage(final PacketSuperOrangeConfig message, MessageContext ctx) {
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
        }
    }
}
