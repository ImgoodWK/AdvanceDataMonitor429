package com.imgood.advancedatamonitor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonObject;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAdvanceLinkScanner;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAdvancePlanner;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiNbtViewer;
import com.imgood.advancedatamonitor.gui.handler.GuiHandler;
import com.imgood.advancedatamonitor.utils.NBTJsonParserHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-only GUI open helpers so item classes avoid importing {@link Minecraft}. */
@SideOnly(Side.CLIENT)
public final class ItemClientGui {

    private ItemClientGui() {}

    public static void openPlannerGui(ItemStack stack, EntityPlayer player) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiAdvancePlanner(stack, player));
    }

    public static void openLinkScannerGui(ItemStack stack, EntityPlayer player) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiAdvanceLinkScanner(stack, player));
    }

    public static void openNbtViewerGui(NBTTagCompound tileNbt) {
        JsonObject json = NBTJsonParserHelper.parseNBTToJson(tileNbt);
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiNbtViewer(json));
    }

    /**
     * Open the Dimensional Pocket config GUI via the IGuiHandler so the server
     * builds the Container with the upgrade slots and the switch state.
     */
    public static void openPocketConfigGui(net.minecraft.item.ItemStack stack,
        net.minecraft.entity.player.EntityPlayer player) {
        net.minecraft.entity.player.EntityPlayer p = player;
        if (p == null) return;
        p.openGui(AdvanceDataMonitor.instance, GuiHandler.POCKET_CONFIG_GUI_ID, p.worldObj, 0, 0, 0);
    }

    /**
     * Open the Dimensional Pocket storage GUI via the IGuiHandler. This is the
     * primary entry point: a native Container with the pocket's slot grid plus
     * the player inventory, so item movement works through vanilla windowClick.
     */
    public static void openPocketStorageGui(net.minecraft.item.ItemStack stack,
        net.minecraft.entity.player.EntityPlayer player) {
        net.minecraft.entity.player.EntityPlayer p = player;
        if (p == null) return;
        p.openGui(AdvanceDataMonitor.instance, GuiHandler.POCKET_STORAGE_GUI_ID, p.worldObj, 0, 0, 0);
    }
}
