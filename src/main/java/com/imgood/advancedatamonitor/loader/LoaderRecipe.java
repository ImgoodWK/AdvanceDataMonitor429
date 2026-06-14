package com.imgood.advancedatamonitor.loader;

import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMaps;

/**
 * Register crafting recipes for all AdvanceDataMonitor blocks and items.
 * Includes both vanilla crafting table recipes and GTNH IV-stage assembler recipes.
 */
public class LoaderRecipe {

    public static void registerRecipes() {
        registerCraftingTableRecipes();
        registerAssemblerRecipes();
    }

    // ========== Vanilla crafting table recipes (always available) ==========

    private static void registerCraftingTableRecipes() {
        // AdvanceDataMonitor — core monitoring block
        GameRegistry.addRecipe(
            new ItemStack(LoaderBlock.advanceDataMonitor),
            "gcg",
            "qrq",
            "gcg",
            'g',
            Blocks.glass,
            'c',
            Items.comparator,
            'q',
            Items.quartz,
            'r',
            Items.redstone);

        // AdvanceNetworkLink — AE2 network statistics monitor
        GameRegistry.addRecipe(
            new ItemStack(LoaderBlock.advanceNetworkLinkBlock),
            " i ",
            "cqc",
            " i ",
            'i',
            Items.iron_ingot,
            'c',
            Items.comparator,
            'q',
            Items.quartz);

        // AdvanceCraftingLink — AE2 crafting CPU monitor
        GameRegistry.addRecipe(
            new ItemStack(LoaderBlock.advanceCraftingLink),
            " g ",
            "cwc",
            " g ",
            'g',
            Blocks.glass,
            'c',
            Items.comparator,
            'w',
            Blocks.crafting_table);

        // AdvanceStorageLink — AE2 storage network monitor
        GameRegistry.addRecipe(
            new ItemStack(LoaderBlock.advanceStorageLinkBlock),
            " i ",
            "cwc",
            " i ",
            'i',
            Items.iron_ingot,
            'c',
            Items.comparator,
            'w',
            Blocks.chest);

        // AdvancePlanner — planning tool
        GameRegistry.addRecipe(
            new ItemStack(LoaderItem.advancePlanner),
            "b b",
            "qpq",
            "b b",
            'b',
            Items.book,
            'q',
            Items.quartz,
            'p',
            Items.paper);

        // AdvanceStorageLinkCell — storage link component
        GameRegistry.addRecipe(
            new ItemStack(LoaderItem.advanceStorageLinkCell),
            " q ",
            "i i",
            " q ",
            'q',
            Items.quartz,
            'i',
            Items.iron_ingot);
    }

    // ========== GTNH Assembler recipes (GregTech required) ==========

    @Optional.Method(modid = "gregtech")
    private static void registerAssemblerRecipes() {
        final int DURATION_NORMAL = 400;
        final int DURATION_FAST = 200;
        final int EU_IV = 8192;
        final int EU_EV = 2048;

        // IV-stage materials via OreDict (with vanilla fallbacks)
        ItemStack plateTungstenSteel = getOreAny("plateTungstenSteel", new ItemStack(Blocks.iron_block));
        ItemStack circuitElite = getOreAny("circuitElite", new ItemStack(Items.comparator));
        ItemStack blockGlass = new ItemStack(Blocks.glass);
        ItemStack rodTungstenSteel = getOreAny("stickTungstenSteel", new ItemStack(Items.iron_ingot));
        ItemStack screwTungstenSteel = getOreAny(
            "screwTungstenSteel",
            getOreAny("boltTungstenSteel", new ItemStack(Items.iron_ingot)));
        ItemStack craftingTable = new ItemStack(Blocks.crafting_table);
        ItemStack chest = new ItemStack(Blocks.chest);
        ItemStack book = new ItemStack(Items.book);
        ItemStack paper = new ItemStack(Items.paper);

        // EV-stage materials (with vanilla fallbacks)
        ItemStack plateTitanium = getOreAny("plateTitanium", new ItemStack(Items.iron_ingot));
        ItemStack circuitGood = getOreAny("circuitGood", new ItemStack(Items.comparator));
        ItemStack lensDiamond = getOreAny("lensDiamond", new ItemStack(Blocks.glass));
        ItemStack wireFineRedAlloy = getOreAny("wireFineRedAlloy", new ItemStack(Items.redstone));
        ItemStack cableGtRedAlloy = getOreAny(
            "cableGtSingleRedAlloy",
            getOreAny("wireGtSingleRedAlloy", new ItemStack(Items.redstone)));

        // AdvanceDataMonitor — core monitoring block (3 plates + 2 circuits + 2 glass)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitElite,
                    circuitElite,
                    blockGlass,
                    blockGlass)
                .itemOutputs(new ItemStack(LoaderBlock.advanceDataMonitor))
                .duration(DURATION_NORMAL)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceNetworkLink — AE2 network monitor (2 plates + 2 circuits + 2 rods + 1 glass)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitElite,
                    circuitElite,
                    rodTungstenSteel,
                    rodTungstenSteel,
                    blockGlass)
                .itemOutputs(new ItemStack(LoaderBlock.advanceNetworkLinkBlock))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceCraftingLink — AE2 crafting CPU monitor (2 plates + 2 circuits + crafting table)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitElite,
                    circuitElite,
                    craftingTable,
                    screwTungstenSteel,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderBlock.advanceCraftingLink))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceStorageLink — AE2 storage monitor (2 plates + 2 circuits + chest)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    plateTungstenSteel,
                    circuitElite,
                    circuitElite,
                    chest,
                    screwTungstenSteel,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderBlock.advanceStorageLinkBlock))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvancePlanner — planning tool (1 plate + 1 circuit + 2 books + 3 paper)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(plateTungstenSteel, circuitElite, book, book, paper, paper, paper)
                .itemOutputs(new ItemStack(LoaderItem.advancePlanner))
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // AdvanceStorageLinkCell — storage link component (1 plate + 1 circuit + 2 glass)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTungstenSteel,
                    circuitElite,
                    blockGlass,
                    blockGlass,
                    screwTungstenSteel,
                    screwTungstenSteel)
                .itemOutputs(new ItemStack(LoaderItem.advanceStorageLinkCell, 2)) // 2 cells per craft
                .duration(DURATION_FAST)
                .eut(EU_IV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}

        // ItemDataWeave — data binding tool (EV-stage: titanium plate + good circuit + diamond lens)
        try {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    plateTitanium,
                    plateTitanium,
                    circuitGood,
                    circuitGood,
                    blockGlass,
                    lensDiamond,
                    wireFineRedAlloy,
                    cableGtRedAlloy)
                .itemOutputs(new ItemStack(LoaderItem.dataWeave))
                .duration(DURATION_FAST)
                .eut(EU_EV)
                .addTo(RecipeMaps.assemblerRecipes);
        } catch (Throwable ignored) {}
    }

    /** Build an ItemStack[] array from non-null items, padding with null to 9 slots. */
    private static ItemStack[] inputs(ItemStack... items) {
        ItemStack[] result = new ItemStack[9];
        for (int i = 0; i < items.length && i < 9; i++) {
            result[i] = items[i];
        }
        return result;
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
