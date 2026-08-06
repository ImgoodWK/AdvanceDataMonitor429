package com.imgood.textech.network.packet;

import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketItemNBT implements IMessage {

    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_TEXT_BYTES = 16 * 1024;

    public boolean malformed;

    public int slot;
    public BlockPos position;
    public String textData; // 修改为String类型

    public PacketItemNBT() {}

    // 构造函数参数类型修改
    public PacketItemNBT(int slot, BlockPos pos, String data) {
        this.slot = slot;
        this.position = pos;
        this.textData = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        slot = 0;
        position = null;
        textData = null;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Item NBT packet exceeds packet body limit");
            }
            slot = buf.readInt();

            // 读取坐标
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            position = new BlockPos(x, y, z);

            // 直接读取单个字符串（不再需要数组长度和循环）
            textData = NetworkPacketCodec.readVarUtf8(buf, MAX_TEXT_BYTES);
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Item NBT packet has trailing or oversized data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            slot = 0;
            position = null;
            textData = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            if (position == null) {
                throw new IllegalArgumentException("Item NBT packet position is missing");
            }
            buf.writeInt(slot);

            // 写入坐标
            buf.writeInt(position.getX());
            buf.writeInt(position.getY());
            buf.writeInt(position.getZ());

            // 直接写入单个字符串（不再需要数组长度和循环）
            NetworkPacketCodec.writeVarUtf8(buf, textData, MAX_TEXT_BYTES);
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Item NBT packet exceeds packet body limit");
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
}
