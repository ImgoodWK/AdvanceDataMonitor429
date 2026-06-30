package com.imgood.advancedatamonitor.items;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.client.PocketClientCache;
import com.imgood.advancedatamonitor.gui.handler.GuiHandler;
import com.imgood.advancedatamonitor.handler.PocketState;
import com.imgood.advancedatamonitor.network.packet.PacketPocketAction;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Dimensional Pocket
 * - ZH: 次元口袋
 * Lang keys: item.dimensionalPocket.name, adm.title.pocketConfig, adm.tooltip.pocket.usage
 *
 * The item itself carries NO state — all pocket content/upgrade/switch/window-position
 * state is bound to the player UUID via PocketStore. Multiple pocket items in the
 * inventory share the same PocketState. The item is only a trigger.
 *
 * Right-click: open storage GUI (store/retrieve items). Shift+right-click: toggle overlay collapsed/expanded.
 * Tooltip shows current upgrade counts and switch state (read from client cache).
 */
public class ItemDimensionalPocket extends Item {

    public ItemDimensionalPocket() {
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (world.isRemote) {
                boolean next = !PocketClientCache.isCollapsed();
                PocketClientCache.setCollapsed(next);
                String msg = next ? StatCollector.translateToLocal("adm.pocket.collapse.on")
                    : StatCollector.translateToLocal("adm.pocket.collapse.off");
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + msg));
                AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketPocketAction.setCollapsed(next));
            }
        } else {
            // Open the storage GUI on the SERVER side. In 1.7.10 Forge, only a server-side
            // openGui call triggers getServerGuiElement + S2D_OPEN_WINDOW, which assigns a
            // non-zero windowId and syncs the container to the client. Calling openGui on
            // the client player (e.g. GCEntityClientPlayerMP) only builds the client GUI
            // without server participation, leaving the server's openContainer as the
            // default ContainerPlayer (windowId 0). Every windowClick packet then gets
            // validated against and executed on ContainerPlayer, so items never reach the
            // pocket's PocketInventory and appear to "bounce back to the inventory".
            if (!world.isRemote) {
                player.openGui(AdvanceDataMonitor.instance, GuiHandler.POCKET_STORAGE_GUI_ID, world, 0, 0, 0);
            }
        }
        return stack;
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("adm.tooltip.pocket.title"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("adm.tooltip.pocket.usage"));

        int space = PocketClientCache.getSpaceUpgrades();
        int page = PocketClientCache.getPageUpgrades();
        int slots = Math.min(PocketState.SLOTS_PER_PAGE_CAP, 1 + Math.min(space, PocketState.MAX_SPACE_UPGRADES - 2));
        int pages = PocketState.BASE_PAGES
            + (space >= PocketState.MAX_SPACE_UPGRADES ? Math.min(page, PocketState.MAX_PAGE_UPGRADES) : 0);

        tooltip.add(
            EnumChatFormatting.GRAY + String.format(
                StatCollector.translateToLocal("adm.tooltip.pocket.stats_space"),
                space,
                PocketState.MAX_SPACE_UPGRADES,
                slots));
        tooltip.add(
            EnumChatFormatting.GRAY + String.format(
                StatCollector.translateToLocal("adm.tooltip.pocket.stats_pages"),
                page,
                PocketState.MAX_PAGE_UPGRADES,
                pages));
        super.addInformation(stack, player, tooltip, advanced);
    }

    public static boolean hasPocketInInventory(EntityPlayer player) {
        if (player == null || player.inventory == null) return false;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemDimensionalPocket) {
                return true;
            }
        }
        return false;
    }
}
