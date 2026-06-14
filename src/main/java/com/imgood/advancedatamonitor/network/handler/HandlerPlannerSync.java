package com.imgood.advancedatamonitor.network.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.imgood.advancedatamonitor.network.packet.PacketPlannerSync;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class HandlerPlannerSync implements IMessageHandler<PacketPlannerSync, IMessage> {

    @Override
    public IMessage onMessage(PacketPlannerSync message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        ItemStack stack = player.inventory.getStackInSlot(message.slot);

        if (stack != null && message.nbt != null) {
            NBTTagCompound existingNbt = stack.getTagCompound();
            if (existingNbt == null) {
                existingNbt = new NBTTagCompound();
            }
            mergePlannerNbt(existingNbt, message.nbt);
            stack.setTagCompound(existingNbt);
            player.inventory.setInventorySlotContents(message.slot, stack);
            player.inventory.markDirty();

            // Reply back to client with confirmed NBT to keep client-side inventory in sync
            return new PacketPlannerSync(message.slot, (NBTTagCompound) existingNbt.copy());
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketPlannerSync, IMessage> {

        @Override
        public IMessage onMessage(PacketPlannerSync message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;

            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(message.slot);
            if (stack != null && message.nbt != null) {
                NBTTagCompound existingNbt = stack.getTagCompound();
                if (existingNbt == null) {
                    existingNbt = new NBTTagCompound();
                }
                mergePlannerNbt(existingNbt, message.nbt);
                stack.setTagCompound(existingNbt);
                mc.thePlayer.inventory.setInventorySlotContents(message.slot, stack);
            }
            return null;
        }
    }

    private static void mergePlannerNbt(NBTTagCompound target, NBTTagCompound source) {
        if (source.hasKey("plannerEntries")) {
            target.setTag("plannerEntries", source.getTagList("plannerEntries", 10));
        }
        if (source.hasKey("nextSlotIndex")) {
            target.setInteger("nextSlotIndex", source.getInteger("nextSlotIndex"));
        }
        if (source.hasKey("hudEnabled")) {
            target.setBoolean("hudEnabled", source.getBoolean("hudEnabled"));
        }
        if (source.hasKey("hudMaxDisplay")) {
            target.setInteger("hudMaxDisplay", source.getInteger("hudMaxDisplay"));
        }
        if (source.hasKey("hudPosX")) {
            target.setFloat("hudPosX", source.getFloat("hudPosX"));
        }
        if (source.hasKey("hudPosY")) {
            target.setFloat("hudPosY", source.getFloat("hudPosY"));
        }
        if (source.hasKey("hudScale")) {
            target.setFloat("hudScale", source.getFloat("hudScale"));
        }
        if (source.hasKey("hudWidth")) {
            target.setInteger("hudWidth", source.getInteger("hudWidth"));
        }
        if (source.hasKey("hudTitle")) {
            target.setString("hudTitle", source.getString("hudTitle"));
        }
    }
}
