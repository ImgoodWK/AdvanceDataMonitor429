package com.imgood.advancedatamonitor.network.packet;

import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class PacketItemNBT implements IMessage {

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
        slot = buf.readInt();

        // 读取坐标
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        position = new BlockPos(x, y, z);

        // 直接读取单个字符串（不再需要数组长度和循环）
        textData = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);

        // 写入坐标
        buf.writeInt(position.getX());
        buf.writeInt(position.getY());
        buf.writeInt(position.getZ());

        // 直接写入单个字符串（不再需要数组长度和循环）
        ByteBufUtils.writeUTF8String(buf, textData);
    }
}
