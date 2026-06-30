package com.imgood.textech.loader;

import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.Optional;
import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMaps;

/**
 * Register GTNH Assembler recipes for all AdvanceDataMonitor blocks and items.
 * All recipes are IV-stage (8192 EU/t) requiring tungstensteel, master circuits,
 * and AE2 fluix crystals. No vanilla crafting table recipes.
 */
public class LoaderRecipe {

    public static void registerRecipes() {
        registerAssemblerRecipes();
    }

    // ========== GTNH Assembler recipes (GregTech required) ==========

    @Optional.Method(modid = "gregtech")
    private static void registerAssemblerRecipes() {
        final int DURATION_NORMAL = 400;
        final int DURATION_FAST = 200;
        final int EU_IV = 8192;

        // IV-stage materials via OreDict (with vanilla fallbacks)
        ItemStack plateTungstenSteel = getOreAny("plateTungstenSteel", new ItemStack(Blocks.iron_block));
        ItemStack circuitMaster = getOreAny("circuitMaster", new ItemStack(Items.comparator));
        ItemStack crystalFluix = getOreAny("crystalFluix", new ItemStack(Items.quartz));
        ItemStack rodTungstenSteel = getOreAny("stickTungstenSteel", new ItemStack(Items.iron_ingot));
        ItemStack screwTungstenSteel = getOreAny(
            "screwTungstenSteel",
            getOreAny("boltTungstenSteel", new ItemStack(Items.iron_ingot)));
        ItemStack lensDiamond = getOreAny("lensDiamond", new ItemStack(Blocks.glass));

        // Functional components
        ItemStack craftingTable = new ItemStack(Blocks.crafting_table);
        ItemStack chest = new ItemStack(Blocks.chest);
        ItemStack book = new ItemStack(Items.book);
        ItemStack paper = new ItemStack(Items.paper);

        // AdvanceDataMonitor â€?core monitoring block (3 plates + 2 master circuits + 2 fluix crystals)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitMaster,
                    circuitMaster,
                    crystalFluix,
                    crystalFluix)
                .itemOutputs(new ItemStack(LoaderBlock.advanceDataMonitor))
                .duration(DURATION_NORMAL)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceNetworkLink â€?AE2 network monitor (2 plates + 2 master circuits + 2 rods + 1 fluix crystal)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitMaster,
                    circuitMaster,
                    rodTungstenSteel,
                    rodTungstenSteel,
                    crystalFluix)
                .itemOutputs(new ItemStack(LoaderBlock.advanceNetworkLinkBlock))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceCraftingLink â€?AE2 crafting CPU monitor (2 plates + 2 master circuits + 1 crafting table + 2 screws)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitMaster,
                    circuitMaster,
                    craftingTable,
                    screwTungstenSteel,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderBlock.advanceCraftingLink))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceStorageLink â€?AE2 storage monitor (2 plates + 2 master circuits + 1 chest + 2 screws)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitMaster,
                    circuitMaster,
                    chest,
                    screwTungstenSteel,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderBlock.advanceStorageLinkBlock))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvancePlanner â€?planning tool (1 plate + 1 master circuit + 2 books + 3 paper)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(plateTungstenSteel, circuitMaster, book, book, paper, paper, paper)
                .itemOutputs(new ItemStack(LoaderItem.advancePlanner))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceLinkScanner â€?link scanner (1 plate + 1 master circuit + 1 planner + 1 data imprint)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    circuitMaster,
                    new ItemStack(LoaderItem.advancePlanner),
                    new ItemStack(LoaderItem.dataImprint),
                    screwTungstenSteel,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderItem.advanceLinkScanner))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceStorageLinkCell â€?storage link component (1 plate + 1 master circuit + 2 fluix crystals + 2 screws)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    circuitMaster,
                    crystalFluix,
                    crystalFluix,
                    screwTungstenSteel,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderItem.advanceStorageLinkCell, 2)) // 2 cells per craft
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // ItemDataImprint â€?data imprint / binding tool
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitMaster,
                    circuitMaster,
                    crystalFluix,
                    lensDiamond,
                    screwTungstenSteel,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderItem.dataImprint))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // DimensionalPocket â€?extra inventory item bound to player UUID
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitMaster,
                    circuitMaster,
                    chest,
                    crystalFluix,
                    crystalFluix)
                .itemOutputs(new ItemStack(LoaderItem.dimensionalPocket))
                .duration(DURATION_NORMAL)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // SpaceUpgradeCard â€?one card per craft
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(plateTungstenSteel, circuitMaster, crystalFluix, screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderItem.spaceUpgradeCard, 4))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // PageUpgradeCard â€?one card per craft (higher tier)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitMaster,
                    crystalFluix,
                    crystalFluix,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderItem.pageUpgradeCard, 2))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

    }

    /** Fetch one matching OreDict item, or return the fallback if none found. */
    private static ItemStack getOreAny(String oreName, ItemStack fallback) {
        List<ItemStack> ores = OreDictionary.getOres(oreName);
        if (ores != null && !ores.isEmpty()) {
            ItemStack found = ores.get(0)
                .copy();
            found.stackSize = 1;
            return found;
        }
        return fallback.copy();
    }
}
