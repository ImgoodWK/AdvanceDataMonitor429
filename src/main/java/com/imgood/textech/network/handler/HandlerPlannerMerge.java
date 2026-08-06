package com.imgood.textech.network.handler;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.items.ItemAdvancePlanner;
import com.imgood.textech.network.packet.PacketPlannerMerge;
import com.imgood.textech.network.packet.PacketPlannerSync;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class HandlerPlannerMerge implements IMessageHandler<PacketPlannerMerge, IMessage> {

    @Override
    public IMessage onMessage(final PacketPlannerMerge message, final MessageContext ctx) {
        if (message == null || message.malformed || message.mode == null) {
            return null;
        }
        return PacketHandlers.runOnServer(ctx, new Runnable() {

            @Override
            public void run() {
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                IMessage response = handleOnServerThread(message, player);
                if (response != null && player != null) {
                    AdvanceDataMonitor.ADMCHANEL.sendTo(response, player);
                }
            }
        });
    }

    private IMessage handleOnServerThread(PacketPlannerMerge message, EntityPlayerMP player) {
        if (player == null) {
            return null;
        }
        List<ItemStack> plannerStacks = ItemAdvancePlanner.getPlannerStacksInInventory(player);

        if (plannerStacks.size() < 2) {
            return null;
        }

        ItemStack merged = ItemAdvancePlanner.mergeMultiplePlanners(plannerStacks, message.mode);
        int resultSlot = -1;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                resultSlot = i;
                break;
            }
        }
        if (resultSlot < 0) {
            return null;
        }
        PacketPlannerSync response = new PacketPlannerSync(resultSlot, merged.getTagCompound());
        if (!response.fitsPacketBudget()) {
            return null;
        }

        // Find and clear all planner stacks, then place the merged result in the first planner's slot
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                if (i == resultSlot) {
                    player.inventory.setInventorySlotContents(i, merged);
                } else {
                    player.inventory.setInventorySlotContents(i, null);
                }
            }
        }

        player.openContainer.detectAndSendChanges();

        // Send the merged NBT back to the client to ensure it has the correct state
        return response;
    }
}
