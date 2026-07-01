package com.imgood.textech.loader;

import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.common.Optional;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.recipe.Scanning;

/**
 * Register GTNH Assembler (IV) and Assembly Line (UHV) recipes for TeXTech blocks and items.
 */
public class LoaderRecipe {

    public static void registerRecipes() {
        registerAssemblerRecipes();
        registerAssemblyLineRecipes();
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

        // AdvanceDataMonitor —core monitoring block (3 plates + 2 master circuits + 2 fluix crystals)
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

        // AdvanceNetworkLink —AE2 network monitor (2 plates + 2 master circuits + 2 rods + 1 fluix crystal)
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

        // AdvanceCraftingLink —AE2 crafting CPU monitor (2 plates + 2 master circuits + 1 crafting table + 2 screws)
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

        // AdvanceStorageLink —AE2 storage monitor (2 plates + 2 master circuits + 1 chest + 2 screws)
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

        // AdvancePlanner —planning tool (1 plate + 1 master circuit + 2 books + 3 paper)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(plateTungstenSteel, circuitMaster, book, book, paper, paper, paper)
                .itemOutputs(new ItemStack(LoaderItem.advancePlanner))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceLinkScanner —link scanner (1 plate + 1 master circuit + 1 planner + 1 data imprint)
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

        // AdvanceStorageLinkCell —storage link component (1 plate + 1 master circuit + 2 fluix crystals + 2 screws)
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

        // ItemDataImprint —data imprint / binding tool
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

        // DimensionalPocket —extra inventory item bound to player UUID
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

        // SpaceUpgradeCard —one card per craft
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(plateTungstenSteel, circuitMaster, crystalFluix, screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderItem.spaceUpgradeCard, 4))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // PageUpgradeCard —one card per craft (higher tier)
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

    /** UHV Assembly Line recipe for the Matter Ball Decompressor (AE matter cluster extraction). */
    @Optional.Method(modid = "gregtech")
    private static void registerAssemblyLineRecipes() {
        final int DURATION_UHV = 120;
        final int SCAN_TIME = 90;

        ItemStack plateNeutronium = getOreAny("plateNeutronium", new ItemStack(Blocks.iron_block));
        ItemStack circuitUltimate = getOreAny("circuitUltimate", getOreAny("circuitSuperconductor", new ItemStack(Items.comparator)));
        ItemStack crystalFluix = getOreAny("crystalFluix", new ItemStack(Items.quartz));
        ItemStack screwNeutronium = getOreAny(
            "screwNeutronium",
            getOreAny("boltNeutronium", new ItemStack(Items.iron_ingot)));
        screwNeutronium.stackSize = 8;
        ItemStack wireFineNaquadah = getOreAny("wireFineNaquadah", new ItemStack(Items.string));
        wireFineNaquadah.stackSize = 16;
        ItemStack storageLink = new ItemStack(LoaderBlock.advanceStorageLinkBlock);
        ItemStack networkLink = new ItemStack(LoaderBlock.advanceNetworkLinkBlock);

        FluidStack solder = Materials.SolderingAlloy.getMolten(5760);
        FluidStack lubricant = Materials.Lubricant.getFluid(2000);
        if (solder == null) {
            solder = getFluidAny("molten.solderingalloy", 5760);
        }
        if (lubricant == null) {
            lubricant = getFluidAny("lubricant", 2000);
        }

        try {
            GTValues.RA.stdBuilder()
                .metadata(GTRecipeConstants.RESEARCH_ITEM, storageLink.copy())
                .metadata(GTRecipeConstants.SCANNING, new Scanning(SCAN_TIME, TierEU.RECIPE_UV))
                .itemInputs(
                    plateNeutronium,
                    plateNeutronium,
                    plateNeutronium,
                    plateNeutronium,
                    circuitUltimate,
                    circuitUltimate,
                    storageLink,
                    networkLink,
                    crystalFluix,
                    crystalFluix,
                    crystalFluix,
                    crystalFluix,
                    screwNeutronium,
                    screwNeutronium,
                    wireFineNaquadah,
                    wireFineNaquadah)
                .fluidInputs(solder, lubricant)
                .itemOutputs(new ItemStack(LoaderBlock.matterBallDecompressor))
                .duration(DURATION_UHV)
                .eut(TierEU.RECIPE_UHV)
                .addTo(GTRecipeConstants.AssemblyLine);
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

    private static FluidStack getFluidAny(String fluidName, int amount) {
        if (FluidRegistry.isFluidRegistered(fluidName)) {
            return new FluidStack(FluidRegistry.getFluid(fluidName), amount);
        }
        return null;
    }
}
