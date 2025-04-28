package com.imgood.advancedatamonitor.gui.handler;

import com.google.gson.JsonObject;
import com.imgood.advancedatamonitor.gui.guiscreen.NBTViewerGUI;
import com.imgood.advancedatamonitor.items.DataWeave;
import com.imgood.advancedatamonitor.utils.NBTJsonParser;
import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-22 11:04
 **/
public class GuiHandler implements IGuiHandler {
    // GUI的唯一ID
    public static final int NBT_VIEWER_GUI_ID = 0;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null; // 服务器端不需要GUI容器
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == NBT_VIEWER_GUI_ID) {
            // 从物品NBT获取数据（此处需要具体实现）
            ItemStack stack = player.getHeldItem();
            if (stack != null && stack.getItem() instanceof DataWeave) {
                NBTTagCompound nbt = stack.getTagCompound();
                if (nbt != null && nbt.hasKey("tileNBT")) {
                    JsonObject json = NBTJsonParser.parseNBTToJson(nbt.getCompoundTag("tileNBT"));
                    return new NBTViewerGUI(json);
                }
            }
        }
        return null;
    }
}
