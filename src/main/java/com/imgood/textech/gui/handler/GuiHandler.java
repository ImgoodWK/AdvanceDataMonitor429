package com.imgood.textech.gui.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.google.gson.JsonObject;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.container.ContainerAdvanceStorageLink;
import com.imgood.textech.gui.container.ContainerDimensionalPocket;
import com.imgood.textech.gui.container.ContainerMatterBallDecompressor;
import com.imgood.textech.gui.container.ContainerPocketStorage;
import com.imgood.textech.gui.guiscreen.GuiAdvanceStorageLink;
import com.imgood.textech.gui.guiscreen.GuiDimensionalPocketConfig;
import com.imgood.textech.gui.guiscreen.GuiGrappleAnchorConfig;
import com.imgood.textech.gui.guiscreen.GuiGrappleHookConfig;
import com.imgood.textech.gui.guiscreen.GuiMainAdvanceDataMonitor;
import com.imgood.textech.gui.guiscreen.GuiManual;
import com.imgood.textech.gui.guiscreen.GuiMatterBallDecompressor;
import com.imgood.textech.gui.guiscreen.GuiNbtViewer;
import com.imgood.textech.gui.guiscreen.GuiPocketStorage;
import com.imgood.textech.gui.guiscreen.GuiSuperOrangeConfig;
import com.imgood.textech.items.ItemDataImprint;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.items.ItemSuperOrange;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceStorageLink;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;
import com.imgood.textech.tileentity.TileEntityMatterBallDecompressor;
import com.imgood.textech.utils.NBTJsonParserHelper;

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
    public static final int MANUAL_GUI_ID = 3;
    public static final int GRAPPLE_ANCHOR_GUI_ID = 4;
    public static final int GRAPPLE_HOOK_GUI_ID = 5;
    public static final int POCKET_CONFIG_GUI_ID = 6;
    public static final int POCKET_STORAGE_GUI_ID = 7;
    public static final int SUPER_ORANGE_GUI_ID = 8;
    public static final int MATTER_BALL_DECOMPRESSOR_GUI_ID = 9;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == ADM_STORAGELINK_ID) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TileEntityAdvanceStorageLink) {
                return new ContainerAdvanceStorageLink(player.inventory, (TileEntityAdvanceStorageLink) tileEntity);
            }
        } else if (ID == POCKET_CONFIG_GUI_ID) {
            return new ContainerDimensionalPocket(player);
        } else if (ID == POCKET_STORAGE_GUI_ID) {
            return new ContainerPocketStorage(player);
        } else if (ID == MATTER_BALL_DECOMPRESSOR_GUI_ID) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TileEntityMatterBallDecompressor) {
                return new ContainerMatterBallDecompressor(
                    player.inventory,
                    (TileEntityMatterBallDecompressor) tileEntity);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        switch (ID) {
            case NBT_VIEWER_GUI_ID:
                ItemStack stack = player.getHeldItem();
                if (stack != null && stack.getItem() instanceof ItemDataImprint) {
                    NBTTagCompound nbt = stack.getTagCompound();
                    if (nbt != null && nbt.hasKey("tileNBT")) {
                        JsonObject json = NBTJsonParserHelper.parseNBTToJson(nbt.getCompoundTag("tileNBT"));
                        return new GuiNbtViewer(json);
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
            case MANUAL_GUI_ID:
                return new GuiManual();
            case GRAPPLE_ANCHOR_GUI_ID:
                TileEntity grappleTe = world.getTileEntity(x, y, z);
                if (grappleTe instanceof TileEntityGrappleAnchor) {
                    return new GuiGrappleAnchorConfig(player, world, x, y, z);
                }
                return null;
            case GRAPPLE_HOOK_GUI_ID:
                ItemStack hookStack = player.getHeldItem();
                if (hookStack != null && hookStack.getItem() instanceof ItemGrappleHook) {
                    return new GuiGrappleHookConfig(hookStack, player);
                }
                return null;
            case POCKET_CONFIG_GUI_ID:
                return new GuiDimensionalPocketConfig(player);
            case POCKET_STORAGE_GUI_ID:
                return new GuiPocketStorage(player);
            case SUPER_ORANGE_GUI_ID:
                ItemStack orangeStack = player.getHeldItem();
                if (orangeStack != null && orangeStack.getItem() instanceof ItemSuperOrange) {
                    return new GuiSuperOrangeConfig(orangeStack, player);
                }
                orangeStack = ItemSuperOrange.findOrangeStack(player);
                if (orangeStack != null) {
                    return new GuiSuperOrangeConfig(orangeStack, player);
                }
                return null;
            case MATTER_BALL_DECOMPRESSOR_GUI_ID:
                TileEntity decompressorTe = world.getTileEntity(x, y, z);
                if (decompressorTe instanceof TileEntityMatterBallDecompressor) {
                    return new GuiMatterBallDecompressor(
                        player.inventory,
                        (TileEntityMatterBallDecompressor) decompressorTe);
                }
                return null;
        }
        return null;
    }
}
