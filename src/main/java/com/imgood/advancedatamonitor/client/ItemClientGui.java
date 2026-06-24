package com.imgood.advancedatamonitor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonObject;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAdvanceLinkScanner;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAdvancePlanner;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiNbtViewer;
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
}
