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

    @Test
    public void podKindMapsAccessAndIoAndOrbit() {
        Assert.assertEquals(TopologyRules.POD_ACCESS, TopologyRules.podKindForSubtype(TopologyRules.SUB_TERMINAL_ME));
        Assert.assertEquals(TopologyRules.POD_IO, TopologyRules.podKindForSubtype(TopologyRules.SUB_BUS_IMPORT));
        Assert.assertEquals(TopologyRules.POD_CRAFT, TopologyRules.podKindForSubtype(TopologyRules.SUB_INTERFACE));
        Assert.assertEquals(TopologyRules.POD_STORAGE0, TopologyRules.podKindForSubtype(TopologyRules.SUB_DRIVE));
        Assert.assertEquals(0, TopologyRules.preferredLaneForPodKind(TopologyRules.POD_ACCESS));
        Assert.assertEquals(1, TopologyRules.preferredLaneForPodKind(TopologyRules.POD_CRAFT));
        Assert.assertEquals(3, TopologyRules.preferredLaneForPodKind(TopologyRules.POD_TUNNEL));
    }
}
