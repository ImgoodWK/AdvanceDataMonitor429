package com.imgood.textech.network.handler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import com.imgood.textech.items.ItemDataImprint;
import com.imgood.textech.network.packet.PacketItemNBT;
import com.imgood.textech.utils.NetworkValidationUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class HandlerNetwork implements IMessageHandler<PacketItemNBT, IMessage> {

    @Override
    public IMessage onMessage(final PacketItemNBT message, final MessageContext ctx) {
        if (message == null || message.malformed) {
            return null;
        }
        return PacketHandlers.runOnServer(ctx, new Runnable() {

            @Override
            public void run() {
                handleOnServerThread(message, ctx.getServerHandler().playerEntity);
            }
        });
    }

    private void handleOnServerThread(PacketItemNBT message, EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        if (!NetworkValidationUtil.isValidInventorySlot(player, message.slot)) {
            return;
        }
        ItemStack stack = player.inventory.getStackInSlot(message.slot);
        if (stack == null || !(stack.getItem() instanceof ItemDataImprint)) {
            return;
        }
        if (message.position == null || message.textData == null) {
            return;
        }
        if (message.textData.length() > 4096) {
            return;
        }
        if (!NetworkValidationUtil
            .isWithinReach(player, message.position.getX(), message.position.getY(), message.position.getZ())) {
            return;
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
        }

        NBTTagCompound posTag = new NBTTagCompound();
        posTag.setInteger("x", message.position.getX());
        posTag.setInteger("y", message.position.getY());
        posTag.setInteger("z", message.position.getZ());
        nbt.setTag("Position", posTag);
        nbt.setString("Data", message.textData);

        stack.setTagCompound(nbt);
        syncItemStack(player, stack, message.slot);
        player.addChatMessage(new ChatComponentText("数据保存成功。"));
    }

    private void syncItemStack(EntityPlayerMP player, ItemStack stack, int slot) {
        player.inventory.setInventorySlotContents(slot, stack);
        player.openContainer.detectAndSendChanges();
    }
}
