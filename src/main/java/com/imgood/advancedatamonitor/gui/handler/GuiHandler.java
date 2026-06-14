package com.imgood.advancedatamonitor.gui.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.google.gson.JsonObject;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.container.ContainerAdvanceStorageLink;
import com.imgood.advancedatamonitor.gui.guiscreen.GUINBTViewer;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiAdvanceStorageLink;
import com.imgood.advancedatamonitor.gui.guiscreen.GuiMainAdvanceDataMonitor;
import com.imgood.advancedatamonitor.items.ItemDataWeave;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;
import com.imgood.advancedatamonitor.utils.NBTJsonParserHelper;

import cpw.mods.fml.common.network.IGuiHandler;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-22 11:04
 **/
public class GuiHandler implements IGuiHandler {

    ResourceLocation guiAdvanceDataMonitor_Main_Background = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_AdvanceDataMonitor_Main.png");
    // GUI的唯一ID
    public static final int NBT_VIEWER_GUI_ID = 0;
    public static final int ADM_MAIN_GUI_ID = 1;
    public static final int ADM_STORAGELINK_ID = 2;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == ADM_STORAGELINK_ID) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TileEntityAdvanceStorageLink) {
                return new ContainerAdvanceStorageLink(player.inventory, (TileEntityAdvanceStorageLink) tileEntity);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        switch (ID) {
            case NBT_VIEWER_GUI_ID:
                ItemStack stack = player.getHeldItem();
                if (stack != null && stack.getItem() instanceof ItemDataWeave) {
                    NBTTagCompound nbt = stack.getTagCompound();
                    if (nbt != null && nbt.hasKey("tileNBT")) {
                        JsonObject json = NBTJsonParserHelper.parseNBTToJson(nbt.getCompoundTag("tileNBT"));
                        return new GUINBTViewer(json);
                    }
                }
                return null;
            case ADM_MAIN_GUI_ID:
                TileEntity tileEntity = world.getTileEntity(x, y, z);
                if (tileEntity instanceof TileEntityAdvanceDataMonitor) {
                    return new GuiMainAdvanceDataMonitor(player, world, (TileEntityAdvanceDataMonitor) tileEntity)
                        .setPosition(-10, 30)
                        .setSize(470, 270)
                        .setStretch(false)
                        .setBackgroundTexture(guiAdvanceDataMonitor_Main_Background);
                }
                return null;
            case ADM_STORAGELINK_ID:
                TileEntity tileEntityAdvanceStorageLink = world.getTileEntity(x, y, z);
                if (tileEntityAdvanceStorageLink instanceof TileEntityAdvanceStorageLink) {
                    return new GuiAdvanceStorageLink(
                        player.inventory,
                        (TileEntityAdvanceStorageLink) tileEntityAdvanceStorageLink);
                }
                return null;
        }
        return null;
    }
}
