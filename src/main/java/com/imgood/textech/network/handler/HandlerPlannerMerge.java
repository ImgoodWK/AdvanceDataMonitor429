package com.imgood.textech.network.handler;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.textech.items.ItemAdvancePlanner;
import com.imgood.textech.network.packet.PacketPlannerMerge;
import com.imgood.textech.network.packet.PacketPlannerSync;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class HandlerPlannerMerge implements IMessageHandler<PacketPlannerMerge, IMessage> {

    @Override
    public IMessage onMessage(PacketPlannerMerge message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        List<ItemStack> plannerStacks = ItemAdvancePlanner.getPlannerStacksInInventory(player);

        if (plannerStacks.size() < 2) {
            return null;
        }

        ItemStack merged = ItemAdvancePlanner.mergeMultiplePlanners(plannerStacks, message.mode);

        // Find and clear all planner stacks, then place the merged result in the first planner's slot
        int resultSlot = -1;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                if (resultSlot == -1) {
                    resultSlot = i;
                    player.inventory.setInventorySlotContents(i, merged);
                } else {
                    player.inventory.setInventorySlotContents(i, null);
                }
            }
        }

        player.openContainer.detectAndSendChanges();

        // Send the merged NBT back to the client to ensure it has the correct state
        if (resultSlot >= 0) {
            return new PacketPlannerSync(resultSlot, merged.getTagCompound());
        }

        return null;
    }
}
