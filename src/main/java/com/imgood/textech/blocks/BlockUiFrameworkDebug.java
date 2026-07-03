package com.imgood.textech.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.gui.handler.GuiHandler;

/**
 * Debug block — opens the ADM UI framework showcase GUI.
 * Registered only when {@link Config#debugUiFrameworkBlock} is true.
 */
public class BlockUiFrameworkDebug extends Block {

    public BlockUiFrameworkDebug() {
        super(Material.iron);
        setHardness(1.5F);
        setResistance(5.0F);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabBlock);
        setBlockName("uiFrameworkDebug");
        setBlockTextureName(AdvanceDataMonitor.MODID + ":adv_data_monitor");
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!Config.debugUiFrameworkBlock) {
            if (!world.isRemote) {
                player.addChatMessage(new ChatComponentTranslation("adm.error.ui_framework.debug_disabled"));
            }
            return true;
        }
        if (!world.isRemote) {
            player.openGui(AdvanceDataMonitor.instance, GuiHandler.UI_FRAMEWORK_DEBUG_GUI_ID, world, x, y, z);
        }
        return true;
    }
}
