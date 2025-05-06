package com.imgood.advancedatamonitor.network.handler;

import com.imgood.advancedatamonitor.network.packet.PacketItemNBT;
import com.imgood.advancedatamonitor.utils.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

public class HandlerNetWork implements IMessageHandler<PacketItemNBT, IMessage> {
    @Override
    public IMessage onMessage(PacketItemNBT message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        ItemStack stack = player.inventory.getStackInSlot(message.slot);

        if (stack != null && isValidPosition(message.position)) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt == null) nbt = new NBTTagCompound();

            // 存储坐标
            NBTTagCompound posTag = new NBTTagCompound();
            posTag.setInteger("x", message.position.getX());
            posTag.setInteger("y", message.position.getY());
            posTag.setInteger("z", message.position.getZ());
            nbt.setTag("Position", posTag);

            //存储单个字符串
            nbt.setString("Data", message.textData);

            stack.setTagCompound(nbt);

            // 同步更新
            syncItemStack(player, stack, message.slot);
            player.addChatMessage(new ChatComponentText("数据保存成功！"));
        }
        return null;
    }

    private boolean isValidPosition(BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() <= 255;
    }

    private void syncItemStack(EntityPlayerMP player, ItemStack stack, int slot) {
        player.inventory.setInventorySlotContents(slot, stack);
        player.openContainer.detectAndSendChanges();
    }
}
