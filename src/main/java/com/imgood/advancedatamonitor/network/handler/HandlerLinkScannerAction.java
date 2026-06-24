package com.imgood.advancedatamonitor.network.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;

import com.imgood.advancedatamonitor.items.ItemAdvanceLinkScanner;
import com.imgood.advancedatamonitor.handler.LinkScannerService;
import com.imgood.advancedatamonitor.network.packet.PacketLinkScannerAction;
import com.imgood.advancedatamonitor.utils.NetworkValidationUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class HandlerLinkScannerAction implements IMessageHandler<PacketLinkScannerAction, IMessage> {

    @Override
    public IMessage onMessage(PacketLinkScannerAction message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (!NetworkValidationUtil.isValidInventorySlot(player, message.slot)) {
            return null;
        }
        ItemStack stack = player.inventory.getStackInSlot(message.slot);
        if (stack == null || !(stack.getItem() instanceof ItemAdvanceLinkScanner)) {
            return null;
        }

        switch (message.action) {
            case PacketLinkScannerAction.ACTION_SCAN: {
                int count = LinkScannerService.scanLoadedTiles(player, stack);
                LinkScannerService.notifyScanResult(player, count);
                player.inventory.markDirty();
                NBTTagCompound nbt = stack.getTagCompound();
                return PacketLinkScannerAction.sync(
                    message.slot,
                    nbt == null ? new NBTTagCompound() : (NBTTagCompound) nbt.copy());
            }
            case PacketLinkScannerAction.ACTION_SYNC: {
                if (message.nbt == null) {
                    return null;
                }
                mergeScannerNbt(stack, message.nbt);
                    player.inventory.setInventorySlotContents(message.slot, stack);
                    player.inventory.markDirty();
                    NBTTagCompound confirmed = stack.getTagCompound();
                    return PacketLinkScannerAction.sync(
                        message.slot,
                        confirmed == null ? new NBTTagCompound() : (NBTTagCompound) confirmed.copy());
            }
            case PacketLinkScannerAction.ACTION_TELEPORT: {
                if (!LinkScannerService.hasScannerEntry(stack, message.dimension, message.x, message.y, message.z)) {
                    return null;
                }
                if (!NetworkValidationUtil.isWithinReach(player, message.x, message.y, message.z)) {
                    return null;
                }
                String key = LinkScannerService.teleportTo(player, message.dimension, message.x, message.y, message.z);
                player.addChatMessage(new ChatComponentTranslation(key));
                return null;
            }
            default:
                return null;
        }
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketLinkScannerAction, IMessage> {

        @Override
        public IMessage onMessage(PacketLinkScannerAction message, MessageContext ctx) {
            if (message.action != PacketLinkScannerAction.ACTION_SYNC || message.nbt == null) {
                return null;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) {
                return null;
            }
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(message.slot);
            if (stack != null && stack.getItem() instanceof ItemAdvanceLinkScanner) {
                mergeScannerNbt(stack, message.nbt);
                mc.thePlayer.inventory.setInventorySlotContents(message.slot, stack);
            }
            return null;
        }
    }

    private static void mergeScannerNbt(ItemStack stack, NBTTagCompound source) {
        NBTTagCompound target = stack.getTagCompound();
        if (target == null) {
            target = new NBTTagCompound();
            stack.setTagCompound(target);
        }
        if (source.hasKey(ItemAdvanceLinkScanner.NBT_KEY_ENTRIES)) {
            target.setTag(
                ItemAdvanceLinkScanner.NBT_KEY_ENTRIES,
                source.getTagList(ItemAdvanceLinkScanner.NBT_KEY_ENTRIES, 10));
        }
        if (source.hasKey(ItemAdvanceLinkScanner.NBT_KEY_NEXT_SLOT)) {
            target.setInteger(ItemAdvanceLinkScanner.NBT_KEY_NEXT_SLOT, source.getInteger(ItemAdvanceLinkScanner.NBT_KEY_NEXT_SLOT));
        }
        if (source.hasKey(ItemAdvanceLinkScanner.NBT_KEY_OWNER_FILTER)) {
            target.setInteger(
                ItemAdvanceLinkScanner.NBT_KEY_OWNER_FILTER,
                source.getInteger(ItemAdvanceLinkScanner.NBT_KEY_OWNER_FILTER));
        }
        if (source.hasKey(ItemAdvanceLinkScanner.NBT_KEY_NAME_FILTER)) {
            target.setInteger(
                ItemAdvanceLinkScanner.NBT_KEY_NAME_FILTER,
                source.getInteger(ItemAdvanceLinkScanner.NBT_KEY_NAME_FILTER));
        }
    }
}
