package com.imgood.textech.compat;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver;
import com.imgood.textech.webae.worldmap.WorldMapBlockColorResolver.BlockFace;

/**
 * Optional mod-block color hints for server-side world map tiles when JAR texture sampling fails.
 */
public final class WorldMapBlockCompat {

    private WorldMapBlockCompat() {}

    /**
     * @return 24-bit RGB or {@code -1} when unknown
     */
    public static int colorForItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return -1;
        }
        String lower = itemId.toLowerCase();
        if (lower.contains("appliedenergistics2") || lower.contains("ae2")) {
            if (lower.contains("controller")) {
                return 0x3a4a6a;
            }
            if (lower.contains("drive")) {
                return 0x2a3344;
            }
            if (lower.contains("chest")) {
                return 0x334455;
            }
            if (lower.contains("interface")) {
                return 0x445566;
            }
            if (lower.contains("cable") || lower.contains("glass")
                || lower.contains("covered")
                || lower.contains("smart")
                || lower.contains("dense")) {
                return 0x6688aa;
            }
            if (lower.contains("terminal")) {
                return 0x556677;
            }
            if (lower.contains("energy")) {
                return 0x446644;
            }
            return 0x556677;
        }
        if (lower.contains("gregtech") || lower.startsWith("gt")) {
            return 0x666666;
        }
        return -1;
    }

    /**
     * @return 24-bit RGB or {@code -1} when unknown
     */
    public static int colorForBlock(Block block, int meta) {
        if (block == null || block == Blocks.air) {
            return -1;
        }
        int sampled = WorldMapBlockColorResolver.colorFor(block, meta, BlockFace.TOP);
        if (sampled != 0x555555 && sampled != 0x777777) {
            return sampled;
        }
        String reg = Block.blockRegistry.getNameForObject(block);
        if (reg != null) {
            int fromReg = colorForItemId(reg);
            if (fromReg >= 0) {
                return fromReg;
            }
        }
        return -1;
    }

    public static int colorForPlacement(String iconItemId, Block block, int meta) {
        int color = colorForBlock(block, meta);
        if (color >= 0) {
            return color;
        }
        color = colorForItemId(iconItemId);
        if (color >= 0) {
            return color;
        }
        if (iconItemId != null && !iconItemId.isEmpty()) {
            Object regObj = Item.itemRegistry.getObject(iconItemId);
            if (regObj instanceof Item) {
                Item item = (Item) regObj;
                ItemStack stack = new ItemStack(item, 1, parseDamage(iconItemId));
                Block fromItem = Block.getBlockFromItem(item);
                if (fromItem != null && fromItem != Blocks.air) {
                    return WorldMapBlockColorResolver.colorFor(fromItem, stack.getItemDamage(), BlockFace.TOP);
                }
            }
        }
        return 0x8899aa;
    }

    private static int parseDamage(String itemId) {
        int colon = itemId.indexOf(':');
        if (colon < 0 || colon >= itemId.length() - 1) {
            return 0;
        }
        int second = itemId.indexOf(':', colon + 1);
        String damagePart = second > colon ? itemId.substring(second + 1) : itemId.substring(colon + 1);
        try {
            return Integer.parseInt(damagePart.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
