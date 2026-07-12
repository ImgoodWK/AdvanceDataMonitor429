package com.imgood.textech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.client.event.MouseEvent;

import com.imgood.textech.items.ItemDataImprint;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DataImprintScrollHandler {

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        if (event.dwheel == 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        if (!player.isSneaking()) return;

        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemDataImprint)) return;

        // Cancel the event so it doesn't change hotbar selection
        event.setCanceled(true);

        int currentRadius = ItemDataImprint.getScanRadius(held);
        int[] radii = { 8, 16, 32, 64 };

        int currentIdx = -1;
        for (int i = 0; i < radii.length; i++) {
            if (radii[i] == currentRadius) {
                currentIdx = i;
                break;
            }
        }
        if (currentIdx < 0) currentIdx = 1; // default to 16

        if (event.dwheel > 0) {
            currentIdx = (currentIdx + 1) % radii.length;
        } else {
            currentIdx = (currentIdx - 1 + radii.length) % radii.length;
        }

        int newRadius = radii[currentIdx];
        ItemDataImprint.setScanRadius(held, newRadius);

        player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.scan_radius_set", Integer.valueOf(newRadius)));
    }
}
