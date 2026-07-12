package com.imgood.textech.webae.topology;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;

public class TopologyRulesTest {

    @Test
    public void tileCraftingTileClassifiesAsSubCpu() {
        Assert.assertEquals(
            TopologyRules.SUB_CPU,
            TopologyRules.classifySubtype("appeng.tile.crafting.TileCraftingTile", null));
    }

    @Test
    public void craftingTileSimpleNameClassifiesAsSubCpu() {
        Assert.assertEquals(
            TopologyRules.SUB_CPU,
            TopologyRules.classifySubtype("appeng.tile.crafting.CraftingTile", null));
    }

    @Test
    public void craftingUnitRegistryIdClassifiesAsSubCpu() {
        Item item = (Item) Item.itemRegistry.getObject("appeng:tile.BlockCraftingStorage");
        if (item == null) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        Assert.assertEquals(
            TopologyRules.SUB_CPU,
            TopologyRules.classifySubtype("appeng.tile.misc.UnknownTile", stack));
    }

    @Test
    public void miscClassDoesNotClassifyAsSubCpu() {
        Assert.assertEquals(
            TopologyRules.SUB_MISC,
            TopologyRules.classifySubtype("appeng.tile.networking.TileCableBus", null));
    }
}
