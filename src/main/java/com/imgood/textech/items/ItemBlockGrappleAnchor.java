package com.imgood.textech.items;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Grapple Anchor
 * - ZH: 挂索节点
 * Lang keys: tile.grappleAnchor.name (block item)
 */
public class ItemBlockGrappleAnchor extends ItemBlock {

    public ItemBlockGrappleAnchor(Block block) {
        super(block);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("adm.tooltip.grappleAnchor.story"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.grappleAnchor.usage"));
    }
}
